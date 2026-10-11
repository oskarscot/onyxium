package dev.onyxium.proxy;

import module java.base;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.onyxium.eventbus.EventBus;
import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.command.CommandDispatcher;
import dev.onyxium.proxy.api.network.NetworkManager;
import dev.onyxium.proxy.api.permission.PermissionService;
import dev.onyxium.proxy.api.player.Player;
import dev.onyxium.proxy.api.plugin.PluginService;
import dev.onyxium.proxy.command.ProxyCommands;
import dev.onyxium.proxy.command.ProxyConsole;
import dev.onyxium.proxy.io.NettyNetworkManager;
import dev.onyxium.proxy.lifecycle.Lifecycle;
import dev.onyxium.proxy.lifecycle.LifecycleException;
import dev.onyxium.proxy.permission.ProxyPermissionService;
import dev.onyxium.proxy.plugin.PluginManager;

@ApiStatus.Internal
public final class OnyxiumProxy implements ProxyServer, Lifecycle {

	private static final Logger LOGGER = LoggerFactory.getLogger(OnyxiumProxy.class);

	NettyNetworkManager networkManager;

	EventBus eventBus = new EventBus();

	CommandDispatcher commandDispatcher = new CommandDispatcher();

	ProxyConsole console = new ProxyConsole(commandDispatcher);

	PluginManager pluginManager = new PluginManager(this);

	final PermissionService permissions = new ProxyPermissionService();

	boolean shutdownStarted;

	public OnyxiumProxy(@NotNull NettyNetworkManager networkManager) {
		this.networkManager = Objects.requireNonNull(networkManager, "networkManager");

		this.commandDispatcher.argumentParser()
			.register(Player.class, (string) ->
				this.networkManager.players().player(string).orElse(null)
			);

		this.commandDispatcher.registerHandler(new ProxyCommands(this));
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
	public PermissionService permissionService() {
		return permissions;
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

	/// The JVM hook is registered before startup, so shutdown must wait until the
	/// current startup attempt, including any plugin rollback, has finished.
	@Override
	public synchronized void start() throws LifecycleException {
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

		if (permissions.registration() == null) {
			LOGGER.warn("No permission provider is registered. Players cannot use commands that require permissions. "
					+ "Install a permissions plugin to enable them.");
		}
	}

	/// The JVM hook must wait for the virtual console thread's cleanup to finish;
	/// returning early could let the JVM exit while plugins are still disabling.
	@Override
	public synchronized void shutdown() {
		if (shutdownStarted) {
			return;
		}
		shutdownStarted = true;
		try {
			this.networkManager.stop();
		}
		finally {
			pluginManager.shutdown();
		}
	}

}
