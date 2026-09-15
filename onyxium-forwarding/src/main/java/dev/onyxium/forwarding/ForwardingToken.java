package dev.onyxium.forwarding;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ForwardingToken {

	public static final String PREFIX = "onyxium-v1.";

	public static final int MAX_TOKEN_LENGTH = 8192;

	private static final long LIFETIME_SECONDS = 30;

	private final byte[] secret;

	public ForwardingToken(String base64Secret) {
		try {
			secret = Base64.getDecoder().decode(base64Secret == null ? "" : base64Secret);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Forwarding secret must be Base64");
		}
		if (secret.length < 32) {
			throw new IllegalArgumentException("Forwarding secret must contain at least 32 random bytes");
		}
	}

	public String sign(ForwardedIdentity identity, String clientFingerprint, String serverFingerprint,
			byte[] connectPayload, Instant now) {
		try {
			var bytes = new ByteArrayOutputStream();
			var out = new DataOutputStream(bytes);
			out.writeLong(now.getEpochSecond());
			writeUuid(out, UUID.randomUUID());
			writeUuid(out, identity.uuid());
			out.writeUTF(identity.username());
			out.writeBoolean(identity.skin() != null);
			if (identity.skin() != null)
				out.writeUTF(identity.skin());
			var address = identity.address().getAddress().getAddress();
			out.writeByte(address.length);
			out.write(address);
			out.writeShort(identity.address().getPort());
			out.writeUTF(clientFingerprint);
			out.writeUTF(serverFingerprint);
			out.write(hash(connectPayload));
			var body = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
			var signed = PREFIX + body;
			var token = signed + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(signed));
			if (token.length() > MAX_TOKEN_LENGTH)
				throw new IllegalArgumentException("Forwarded identity is too large");
			return token;
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("Cannot encode forwarded identity", exception);
		}
	}

	public Verified verify(String token, String clientFingerprint, String serverFingerprint, byte[] connectPayload,
			Instant now) {
		try {
			if (token == null || token.length() > MAX_TOKEN_LENGTH || !token.startsWith(PREFIX))
				throw invalid();

			var separator = token.lastIndexOf('.');
			if (separator <= PREFIX.length())
				throw invalid();

			var signed = token.substring(0, separator);
			if (!MessageDigest.isEqual(mac(signed), Base64.getUrlDecoder().decode(token.substring(separator + 1))))
				throw invalid();

			var in = new DataInputStream(
					new ByteArrayInputStream(Base64.getUrlDecoder().decode(signed.substring(PREFIX.length()))));

			var issuedAt = in.readLong();
			var current = now.getEpochSecond();

			if (issuedAt < current - LIFETIME_SECONDS || issuedAt > current + 5)
				throw invalid();

			var nonce = readUuid(in);
			var uuid = readUuid(in);
			var username = in.readUTF();
			var skin = in.readBoolean() ? in.readUTF() : null;
			var addressLength = in.readUnsignedByte();

			if (addressLength != 4 && addressLength != 16)
				throw invalid();

			var address = new InetSocketAddress(InetAddress.getByAddress(in.readNBytes(addressLength)),
					in.readUnsignedShort());

			if (!in.readUTF().equals(clientFingerprint) || !in.readUTF().equals(serverFingerprint)
					|| !MessageDigest.isEqual(in.readNBytes(32), hash(connectPayload)) || in.available() != 0)
				throw invalid();

			return new Verified(new ForwardedIdentity(uuid, username, skin, address), nonce,
					issuedAt + LIFETIME_SECONDS);
		}
		catch (IOException | IllegalArgumentException exception) {
			throw invalid();
		}
	}

	public String acknowledgement(String token) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(mac("onyxium-backend-v1:" + token));
	}

	public boolean verifyAcknowledgement(String token, String proof) {
		return proof != null && MessageDigest.isEqual(acknowledgement(token).getBytes(StandardCharsets.US_ASCII),
				proof.getBytes(StandardCharsets.US_ASCII));
	}

	public static String fingerprint(X509Certificate certificate) {
		try {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash(certificate.getEncoded()));
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalArgumentException("Cannot fingerprint certificate", exception);
		}
	}

	private byte[] mac(String text) {
		try {
			var mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return mac.doFinal(text.getBytes(StandardCharsets.US_ASCII));
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static byte[] hash(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
		out.writeLong(uuid.getMostSignificantBits());
		out.writeLong(uuid.getLeastSignificantBits());
	}

	private static UUID readUuid(DataInputStream in) throws IOException {
		return new UUID(in.readLong(), in.readLong());
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("Invalid forwarding token");
	}

	public record Verified(ForwardedIdentity identity, UUID nonce, long expiresAt) {
	}

	public static final class ReplayGuard {

		private final Map<UUID, Long> used = new HashMap<>();

		public synchronized boolean accept(Verified claims, Instant now) {
			used.values().removeIf(expiry -> expiry < now.getEpochSecond());
			if (claims.expiresAt() < now.getEpochSecond() || used.size() >= 10000)
				return false;
			return used.putIfAbsent(claims.nonce(), claims.expiresAt()) == null;
		}

	}

}
