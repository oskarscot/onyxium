package dev.onyxium.proxy.api;

import module java.base;

import dev.onyxium.eventbus.EventBus;
import dev.onyxium.proxy.api.command.CommandDispatcher;
import dev.onyxium.proxy.api.command.ConsoleSource;
import dev.onyxium.proxy.api.network.NetworkManager;
import dev.onyxium.proxy.api.permission.PermissionService;
import dev.onyxium.proxy.api.player.Player;
import dev.onyxium.proxy.api.plugin.PluginService;

public interface ProxyServer {

	/// Returns the proxy's network manager.
	///
	/// @return the network manager
	NetworkManager networkManager();

	/// Returns the shared event bus for the proxy
	///
	/// @return the proxy's event bus
	EventBus eventBus();

	/// The shared command dispatcher. Plugins should call `registerHandler(this, handler)`
	/// in their enable callback so the proxy removes their commands after disable.
	/// Dispatch runs on the calling thread.
	CommandDispatcher commandDispatcher();

	/// Returns the instance of [dev.onyxium.proxy.api.command.CommandSource] responsible for console command handling.
	ConsoleSource console();

	/// Shared plugin management API. Use it to look up loaded plugins or request
	/// activation changes with dependency checks instead of invoking plugin callbacks directly.
	PluginService pluginService();

	/// Checks player permissions through the active plugin provider.
	PermissionService permissionService();

	/// Stops the proxy and waits for network, authentication, and plugin cleanup.
	/// Concurrent callers wait for that cleanup to finish; repeated calls do not run it again.
	/// Call from outside a network event loop, such as the console or a shutdown hook.
	void shutdown();

	/// Returns an unmodifiable snapshot of the registered players.
	///
	/// @return the players registered when the snapshot was taken
	Collection<Player> players();

	/// Looks up a registered player by UUID.
	///
	/// @param uniqueId the player's UUID
	/// @return the player, or an empty optional if none is registered
	Optional<Player> player(UUID uniqueId);

	/// Looks up a registered player by their username. Avoid searching by name if you have the player's uuid.
	///
	/// @param username the player's username
	/// @return the player, or an empty optional if none is registered
	Optional<Player> player(String username);

}
