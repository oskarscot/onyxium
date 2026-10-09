package dev.onyxium.proxy;

import module java.base;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import dev.onyxium.command.CommandDispatcher;
import dev.onyxium.eventbus.EventBus;
import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.network.NetworkManager;
import dev.onyxium.proxy.api.player.Player;
import dev.onyxium.proxy.api.plugin.PluginService;
import dev.onyxium.proxy.command.ProxyCommands;
import dev.onyxium.proxy.command.ProxyConsole;
import dev.onyxium.proxy.io.NettyNetworkManager;
import dev.onyxium.proxy.lifecycle.Lifecycle;
import dev.onyxium.proxy.lifecycle.LifecycleException;
import dev.onyxium.proxy.plugin.PluginManager;

@ApiStatus.Internal
public final class OnyxiumProxy implements ProxyServer, Lifecycle {

	NettyNetworkManager networkManager;

	EventBus eventBus = new EventBus();

	CommandDispatcher commandDispatcher = new CommandDispatcher();

	ProxyConsole console = new ProxyConsole(commandDispatcher);

	PluginManager pluginManager = new PluginManager(this);

	public OnyxiumProxy(@NotNull NettyNetworkManager networkManager) {
		this.networkManager = Objects.requireNonNull(networkManager, "networkManager");
		commandDispatcher.registerHandler(new ProxyCommands(this));
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
	public CommandDispatcher commandDispatcher() {
		return commandDispatcher;
	}

	@Override
	public ProxyConsole console() {
		return console;
	}

	@Override
	public PluginService pluginService() {
		return pluginManager;
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
	public Optional<Player> player(String username) {
		return networkManager.players().player(username).map(player -> player);
	}

	@Override
	public void start() throws LifecycleException {
		pluginManager.start();
		try {
			this.networkManager.start(this);
		}
		catch (RuntimeException | Error failure) {
			try {
				pluginManager.shutdown();
			}
			catch (LifecycleException cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
			throw failure;
		}
	}

	@Override
	public void shutdown() {
		try {
			this.networkManager.stop();
		}
		finally {
			pluginManager.shutdown();
		}
	}

}
