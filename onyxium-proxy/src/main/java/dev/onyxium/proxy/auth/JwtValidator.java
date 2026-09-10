package dev.onyxium.proxy.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import dev.onyxium.proxy.io.packet.ClientType;

public final class JwtValidator {

	private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

	private static final Duration KEY_REFRESH_INTERVAL = Duration.ofMinutes(5);

	private final String issuer;

	private final String audience;

	private final Supplier<String> keySource;

	private final Clock clock;

	private JWKSet cachedKeys;

	private Instant lastFetch = Instant.MIN;

	public JwtValidator(String issuer, String audience, Supplier<String> keySource, Clock clock) {
		this.issuer = issuer;
		this.audience = audience;
		this.keySource = keySource;
		this.clock = clock;
	}

	public AuthenticatedProfile validateIdentity(String token, ClientType clientType) {
		try {
			var claims = validate(token);
			requireScope(claims, clientType == ClientType.EDITOR ? "hytale:editor" : "hytale:client");
			var profile = claims.getJSONObjectClaim("profile");
			if (profile == null)
				throw invalid();
			var username = SessionServiceClient.requiredString(profile, "username");
			if (!username.matches("[A-Za-z0-9_]{1,16}"))
				throw invalid();
			var skin = profile.get("skin") instanceof String value ? value : null;
			var entitlements = profile.get("entitlements") instanceof List<?> values
					? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
					: List.<String>of();
			return new AuthenticatedProfile(subject(claims), username, skin, entitlements);
		}
		catch (ParseException | IllegalArgumentException exception) {
			throw invalid();
		}
	}

	public Instant validateServerIdentity(String token) {
		var claims = validate(token);
		requireScope(claims, "hytale:server");
		return claims.getExpirationTime().toInstant();
	}

	public void validateAccess(String token, AuthenticatedProfile identity, String fingerprint) {
		try {
			var claims = validate(token);
			var confirmation = claims.getJSONObjectClaim("cnf");
			if (!claims.getAudience().contains(audience) || !subject(claims).equals(identity.uniqueId())
					|| !identity.username().equals(claims.getStringClaim("username")) || confirmation == null
					|| !(confirmation.get("x5t#S256") instanceof String binding) || fingerprint == null
					|| !MessageDigest.isEqual(binding.getBytes(StandardCharsets.US_ASCII),
							fingerprint.getBytes(StandardCharsets.US_ASCII)))
				throw invalid();
		}
		catch (ParseException | IllegalArgumentException exception) {
			throw invalid();
		}
	}

	private JWTClaimsSet validate(String token) {
		try {
			if (token == null || token.isBlank() || token.length() > 8192)
				throw invalid();
			var jwt = SignedJWT.parse(token);
			var header = jwt.getHeader();
			if (!JWSAlgorithm.EdDSA.equals(header.getAlgorithm()) || !header.isBase64URLEncodePayload()
					|| (header.getCriticalParams() != null && !header.getCriticalParams().isEmpty()))
				throw invalid();
			var keys = keys(false);
			if (!verify(jwt, keys) && !verify(jwt, keys(true)))
				throw invalid();
			var claims = jwt.getJWTClaimsSet();
			var now = clock.instant();
			if (!issuer.equals(claims.getIssuer()) || claims.getExpirationTime() == null
					|| !now.minus(CLOCK_SKEW).isBefore(claims.getExpirationTime().toInstant())
					|| (claims.getNotBeforeTime() != null
							&& now.plus(CLOCK_SKEW).isBefore(claims.getNotBeforeTime().toInstant()))
					|| (claims.getIssueTime() != null
							&& now.plus(CLOCK_SKEW).isBefore(claims.getIssueTime().toInstant())))
				throw invalid();
			subject(claims);
			return claims;
		}
		catch (ParseException | IllegalArgumentException exception) {
			throw invalid();
		}
	}

	private static UUID subject(JWTClaimsSet claims) {
		var value = claims.getSubject();
		if (value == null)
			throw invalid();
		var uuid = UUID.fromString(value);
		if (!uuid.toString().equalsIgnoreCase(value))
			throw invalid();
		return uuid;
	}

	private static void requireScope(JWTClaimsSet claims, String required) {
		try {
			var scope = claims.getStringClaim("scope");
			if (scope == null || Arrays.stream(scope.split("\\s+")).noneMatch(required::equals))
				throw invalid();
		}
		catch (ParseException exception) {
			throw invalid();
		}
	}

	/// Coalesces key fetches and throttles refresh attempts, including failed fetches.
	/// This lock is only acquired by authentication workers, never an event loop.
	private synchronized JWKSet keys(boolean refresh) {
		var now = clock.instant();
		if (now.isBefore(lastFetch.plus(KEY_REFRESH_INTERVAL))) {
			if (cachedKeys == null)
				throw new AuthenticationException("Authentication signing keys unavailable");
			return cachedKeys;
		}
		if (cachedKeys != null && !refresh)
			return cachedKeys;
		lastFetch = now;
		try {
			cachedKeys = JWKSet.parse(keySource.get());
			return cachedKeys;
		}
		catch (ParseException exception) {
			throw new AuthenticationException("Invalid authentication signing keys");
		}
	}

	private static boolean verify(SignedJWT jwt, JWKSet keys) {
		var kid = jwt.getHeader().getKeyID();
		var input = jwt.getSigningInput();
		for (var key : keys.getKeys()) {
			if (!(key instanceof OctetKeyPair okp) || !Curve.Ed25519.equals(okp.getCurve()) || okp.isPrivate()
					|| (kid != null && !kid.equals(key.getKeyID()))
					|| (key.getKeyUse() != null && !KeyUse.SIGNATURE.equals(key.getKeyUse()))
					|| (key.getAlgorithm() != null && !JWSAlgorithm.EdDSA.equals(key.getAlgorithm())))
				continue;
			var verifier = new Ed25519Signer();
			verifier.init(false, new Ed25519PublicKeyParameters(okp.getDecodedX(), 0));
			verifier.update(input, 0, input.length);
			if (verifier.verifySignature(jwt.getSignature().decode()))
				return true;
		}
		return false;
	}

	private static AuthenticationException invalid() {
		return new AuthenticationException("Invalid authentication token");
	}

}
