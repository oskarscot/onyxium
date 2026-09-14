package dev.onyxium.proxy.io.packet;

/// Matches Protocol/protocol-version.json from the hytale source.
/// ALPN alone does not identify a compatible packet schema.
public record ProtocolVersion(int crc, int buildNumber) {

	public static final ProtocolVersion CURRENT = new ProtocolVersion(322808978, 232);

	public boolean matches(int clientCrc) {
		return crc == clientCrc;
	}

}
