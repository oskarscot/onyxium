package dev.onyxium.proxy.io.packet.handler;

import java.util.Objects;
import java.util.function.Consumer;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.auth.AuthenticationService;
import dev.onyxium.proxy.io.packet.ProtocolVersion;
import dev.onyxium.proxy.player.PlayerRegistry;
import dev.onyxium.proxy.player.ProxyPlayer;

public record LoginContext(ProxyServer proxy, AuthenticationService authentication, PlayerRegistry players,
		ProtocolVersion protocolVersion, String password, Consumer<ProxyPlayer> onAuthenticated) {
	public LoginContext {
		Objects.requireNonNull(proxy);
		Objects.requireNonNull(authentication);
		Objects.requireNonNull(players);
		Objects.requireNonNull(protocolVersion);
		Objects.requireNonNull(onAuthenticated);
	}

	@Override
	public String toString() {
		return "LoginContext[protocolVersion=" + protocolVersion + "]";
	}
}
