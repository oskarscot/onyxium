package dev.onyxium.proxy.api.network;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

public record NetworkInfo(@NotNull InetSocketAddress bindAddress,
		@NotNull @Unmodifiable List<String> applicationProtocols) {

	public static final int PROTOCOL_VERSION = 3;

	public NetworkInfo {
		Objects.requireNonNull(bindAddress, "bindAddress");
		applicationProtocols = List.copyOf(applicationProtocols);

		if (applicationProtocols.isEmpty()) {
			throw new IllegalArgumentException("at least one application protocol is required");
		}
	}

	@NotNull
	public static NetworkInfo of(@NotNull InetSocketAddress bindAddress) {
		return new NetworkInfo(bindAddress, supportedApplicationProtocols(PROTOCOL_VERSION));
	}

	/// The Hytale Server accepts and current and previous ALPN, we need to support the
	/// previous ALPN
	/// to not lock out an un-updated client
	@NotNull
	@Unmodifiable
	public static List<String> supportedApplicationProtocols(int protocolVersion) {
		return List.of("hytale/" + protocolVersion, "hytale/" + (protocolVersion - 1));
	}
}
