package dev.onyxium.proxy.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import org.junit.Before;
import org.junit.Test;

import dev.onyxium.proxy.io.packet.ClientType;

public class JwtValidatorTest {

	private static final String ISSUER = "https://sessions.hytale.com";

	private TestTokens tokens;

	private JwtValidator validator;

	private AuthenticatedProfile profile;

	@Before
	public void setup() throws Exception {
		tokens = new TestTokens("test-key");
		validator = new JwtValidator(ISSUER, "test-proxy", tokens::jwks, Clock.systemUTC());
		profile = validator.validateIdentity(tokens.identity(ISSUER), ClientType.GAME);
	}

	@Test
	public void validatesSignedIdentityAndCertificateBoundAccess() throws Exception {
		assertEquals(TestTokens.PLAYER_ID, profile.uniqueId());
		assertEquals("Oskar", profile.username());
		assertEquals(List.of("game"), profile.entitlements());
		validator.validateAccess(tokens.sign(tokens.access(ISSUER, "client-cert").build()), profile, "client-cert");
	}

	@Test
	public void rejectsInvalidClaimsEvenWithValidSignatures() throws Exception {
		var invalid = List.of(tokens.access(ISSUER, "wrong-cert").build(),
				tokens.access(ISSUER, "client-cert").audience("other-server").build(),
				tokens.access(ISSUER, "client-cert").issuer("https://attacker.invalid").build(),
				tokens.access(ISSUER, "client-cert").subject("00000000-0000-0000-0000-000000000001").build(),
				tokens.access(ISSUER, "client-cert").claim("username", "Impostor").build(),
				tokens.access(ISSUER, "client-cert").expirationTime(null).build(),
				tokens.access(ISSUER, "client-cert").expirationTime(Date.from(Instant.now().minusSeconds(600))).build(),
				tokens.access(ISSUER, "client-cert").notBeforeTime(Date.from(Instant.now().plusSeconds(600))).build(),
				tokens.access(ISSUER, "client-cert").issueTime(Date.from(Instant.now().plusSeconds(600))).build(),
				tokens.access(ISSUER, "client-cert").claim("cnf", null).build());
		for (var claims : invalid) {
			var token = tokens.sign(claims);
			assertThrows(AuthenticationException.class, () -> validator.validateAccess(token, profile, "client-cert"));
		}
	}

	@Test
	public void rejectsScopeConfusionAndInvalidIdentity() throws Exception {
		var clientToken = tokens.identity(ISSUER);
		assertThrows(AuthenticationException.class, () -> validator.validateIdentity(clientToken, ClientType.EDITOR));
		var wrongScope = tokens.sign(tokens.claims(ISSUER)
			.claim("scope", "prefix-hytale:client")
			.claim("profile", Map.of("username", "Oskar"))
			.build());
		assertThrows(AuthenticationException.class, () -> validator.validateIdentity(wrongScope, ClientType.GAME));
		var invalidSubject = tokens.sign(tokens.claims(ISSUER)
			.subject("1-1-1-1-1")
			.claim("scope", "hytale:client")
			.claim("profile", Map.of("username", "Oskar"))
			.build());
		assertThrows(AuthenticationException.class, () -> validator.validateIdentity(invalidSubject, ClientType.GAME));
	}

	@Test
	public void rejectsAlgorithmConfusionCriticalHeadersAndForgedSignatures() throws Exception {
		var claims = tokens.access(ISSUER, "client-cert").build();
		var hmacHeader = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(tokens.keyId).build();
		var criticalHeader = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(tokens.keyId)
			.criticalParams(Set.of("unknown"))
			.customParam("unknown", true)
			.build();
		var otherKey = new TestTokens(tokens.keyId);
		for (var token : List.of(tokens.sign(claims, hmacHeader), tokens.sign(claims, criticalHeader),
				otherKey.sign(claims), "not.a.jwt")) {
			assertThrows(AuthenticationException.class, () -> validator.validateAccess(token, profile, "client-cert"));
		}
	}

	@Test
	public void refreshesRotatedKeysWithoutFetchingForEveryInvalidToken() throws Exception {
		var time = new MutableClock();
		var source = new AtomicReference<>(tokens.jwks());
		var fetches = new AtomicInteger();
		var rotating = new JwtValidator(ISSUER, "test-proxy", () -> {
			fetches.incrementAndGet();
			return source.get();
		}, time);
		rotating.validateIdentity(tokens.identity(ISSUER), ClientType.GAME);
		var newKey = new TestTokens("rotated");
		var token = newKey.identity(ISSUER);
		source.set(newKey.jwks());
		for (var i = 0; i < 10; i++) {
			assertThrows(AuthenticationException.class, () -> rotating.validateIdentity(token, ClientType.GAME));
		}
		assertEquals(1, fetches.get());
		time.now = time.now.plusSeconds(301);
		assertEquals("Oskar", rotating.validateIdentity(token, ClientType.GAME).username());
		assertEquals(2, fetches.get());
	}

	private static final class MutableClock extends Clock {

		private Instant now = Instant.now();

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
