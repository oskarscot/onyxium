package dev.onyxium.proxy.api;

import module java.base;

import dev.onyxium.command.CommandDispatcher;
import dev.onyxium.eventbus.EventBus;
import dev.onyxium.proxy.api.command.ConsoleSource;
import dev.onyxium.proxy.api.network.NetworkManager;
import dev.onyxium.proxy.api.player.Player;

public interface ProxyServer {

	/// Returns the proxy's network manager.
	///
	/// @return the network manager
	NetworkManager networkManager();

	/// Returns the shared event bus for the proxy
	///
	/// @return the proxy's event bus
	EventBus eventBus();

	/// Register handlers before starting the proxy. Dispatch runs on the calling thread.
	CommandDispatcher commandDispatcher();

	ConsoleSource console();

	/// Stops the proxy and waits for network and authentication cleanup.
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
