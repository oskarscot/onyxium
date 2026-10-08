package dev.onyxium.proxy.io.packet;

/// Matches Protocol/protocol-version.json from the hytale source.
/// ALPN alone does not identify a compatible packet schema.
/// CRCs are unsigned 32-bit values, represented as positive longs in the proxy API.
public record ProtocolVersion(long crc, int buildNumber) {

	public static final ProtocolVersion CURRENT = new ProtocolVersion(2982205573L, 251);

	public boolean matches(long clientCrc) {
		return crc == clientCrc;
	}

}
