package dev.onyxium.proxy.auth;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;

final class TestTokens {

	static final UUID PLAYER_ID = UUID.fromString("6ad0f0d4-6c9e-4a9e-bd0f-5c4a4d9e1b2c");

	final KeyPair key;

	final String keyId;

	TestTokens(String keyId) throws Exception {
		this.keyId = keyId;
		key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
	}

	String jwks() {
		var encoded = key.getPublic().getEncoded();
		var raw = Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
		var jwk = new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(raw)).keyID(keyId)
			.algorithm(JWSAlgorithm.EdDSA)
			.build();
		return new JWKSet(jwk).toString();
	}

	JWTClaimsSet.Builder claims(String issuer) {
		return new JWTClaimsSet.Builder().issuer(issuer)
			.subject(PLAYER_ID.toString())
			.issueTime(Date.from(Instant.now().minusSeconds(1)))
			.expirationTime(Date.from(Instant.now().plusSeconds(3600)));
	}

	String identity(String issuer) throws Exception {
		return sign(claims(issuer).claim("scope", "hytale:client")
			.claim("profile", Map.of("username", "Oskar", "skin", "{}", "entitlements", List.of("game")))
			.build());
	}

	String serverIdentity(String issuer) throws Exception {
		return sign(claims(issuer).claim("scope", "hytale:server").build());
	}

	JWTClaimsSet.Builder access(String issuer, String fingerprint) {
		return claims(issuer).audience("test-proxy")
			.claim("username", "Oskar")
			.claim("cnf", Map.of("x5t#S256", fingerprint));
	}

	String sign(JWTClaimsSet claims) throws Exception {
		return sign(claims, new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(keyId).build());
	}

	String sign(JWTClaimsSet claims, JWSHeader header) throws Exception {
		var input = header.toBase64URL() + "." + Base64URL.encode(claims.toString());
		var signature = Signature.getInstance("Ed25519");
		signature.initSign(key.getPrivate());
		signature.update(input.getBytes(StandardCharsets.US_ASCII));
		return input + "." + Base64URL.encode(signature.sign());
	}

}
