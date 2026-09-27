package dev.onyxium.proxy;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import dev.onyxium.eventbus.EventBus;
import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.network.NetworkManager;
import dev.onyxium.proxy.api.player.Player;
import dev.onyxium.proxy.io.NettyNetworkManager;
import dev.onyxium.proxy.lifecycle.Lifecycle;
import dev.onyxium.proxy.lifecycle.LifecycleException;

@ApiStatus.Internal
public final class OnyxiumProxy implements ProxyServer, Lifecycle {

	private final NettyNetworkManager networkManager;

	private final EventBus eventBus = new EventBus();

	public OnyxiumProxy(@NotNull NettyNetworkManager networkManager) {
		this.networkManager = Objects.requireNonNull(networkManager, "networkManager");
	}

	@Override
	@NotNull
	public NetworkManager networkManager() {
		return this.networkManager;
	}

	@Override
	public EventBus eventBus() {
		return this.eventBus;
	}

	@Override
	public Collection<Player> players() {
		return List.copyOf(networkManager.players().players());
	}

	@Override
	public Optional<Player> player(UUID uniqueId) {
		return networkManager.players().player(uniqueId).map(player -> player);
	}

	@Override
	public void start() throws LifecycleException {
		this.networkManager.start(this);
	}

	@Override
	public void stop() {
		this.networkManager.stop();
	}

}
