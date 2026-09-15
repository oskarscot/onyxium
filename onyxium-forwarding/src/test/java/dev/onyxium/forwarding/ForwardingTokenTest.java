package dev.onyxium.forwarding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.Test;

public class ForwardingTokenTest {

	private static final Instant NOW = Instant.ofEpochSecond(1800000000);

	private static final byte[] CONNECT = { 1, 2, 3 };

	private final ForwardingToken tokens = new ForwardingToken(Base64.getEncoder().encodeToString(new byte[32]));

	private final ForwardedIdentity identity = new ForwardedIdentity(UUID.randomUUID(), "Oskar", "{\"skin\":true}",
			new InetSocketAddress("2001:db8::7", 54321));

	@Test
	public void preservesIdentityAndRejectsReplay() {
		var token = sign(NOW);
		var verified = tokens.verify(token, "client", "server", CONNECT, NOW);
		assertEquals(identity, verified.identity());
		var replays = new ForwardingToken.ReplayGuard();
		assertTrue(replays.accept(verified, NOW));
		assertFalse(replays.accept(verified, NOW));
		assertFalse(replays.accept(verified, NOW.plusSeconds(31)));
		assertTrue(replays.accept(tokens.verify(sign(NOW), "client", "server", CONNECT, NOW), NOW));
	}

	@Test
	public void rejectsAlteredClaimsWrongSecretAndConnectionBindings() {
		var token = sign(NOW);
		var tampered = token.substring(0, ForwardingToken.PREFIX.length()) + "B"
				+ token.substring(ForwardingToken.PREFIX.length() + 1);
		assertThrows(IllegalArgumentException.class, () -> tokens.verify(tampered, "client", "server", CONNECT, NOW));
		assertThrows(IllegalArgumentException.class,
				() -> tokens.verify(token, "another-client", "server", CONNECT, NOW));
		assertThrows(IllegalArgumentException.class,
				() -> tokens.verify(token, "client", "another-server", CONNECT, NOW));
		assertThrows(IllegalArgumentException.class,
				() -> tokens.verify(token, "client", "server", new byte[] { 4 }, NOW));
		var different = new byte[32];
		different[0] = 1;
		var other = new ForwardingToken(Base64.getEncoder().encodeToString(different));
		assertThrows(IllegalArgumentException.class, () -> other.verify(token, "client", "server", CONNECT, NOW));
	}

	@Test
	public void rejectsExpiredFutureMalformedAndOversizedTokens() {
		assertThrows(IllegalArgumentException.class,
				() -> tokens.verify(sign(NOW.minusSeconds(31)), "client", "server", CONNECT, NOW));
		assertThrows(IllegalArgumentException.class,
				() -> tokens.verify(sign(NOW.plusSeconds(6)), "client", "server", CONNECT, NOW));
		for (var token : new String[] { "", "identity-jwt", "onyxium-v1.@@.@@", "x".repeat(8193) }) {
			assertThrows(IllegalArgumentException.class, () -> tokens.verify(token, "client", "server", CONNECT, NOW));
		}
		assertThrows(IllegalArgumentException.class, () -> tokens.verify(null, "client", "server", CONNECT, NOW));
	}

	@Test
	public void backendProofIsBoundToTheForwardingToken() {
		var token = sign(NOW);
		assertTrue(tokens.verifyAcknowledgement(token, tokens.acknowledgement(token)));
		assertFalse(tokens.verifyAcknowledgement(sign(NOW), tokens.acknowledgement(token)));
		assertFalse(tokens.verifyAcknowledgement(token, token.substring(token.lastIndexOf('.') + 1)));
		assertFalse(tokens.verifyAcknowledgement(token, null));
	}

	@Test
	public void rejectsMissingWeakAndMalformedSecrets() {
		for (var secret : new String[] { null, "", "not base64!", Base64.getEncoder().encodeToString(new byte[31]) }) {
			assertThrows(IllegalArgumentException.class, () -> new ForwardingToken(secret));
		}
	}

	private String sign(Instant instant) {
		return tokens.sign(identity, "client", "server", CONNECT, instant);
	}

}
