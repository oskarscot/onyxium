package dev.onyxium.proxy.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import dev.onyxium.eventbus.EventBus;
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

	/// Returns an unmodifiable snapshot of the registered players.
	///
	/// @return the players registered when the snapshot was taken
	Collection<Player> players();

	/// Looks up a registered player by UUID.
	///
	/// @param uniqueId the player's UUID
	/// @return the player, or an empty optional if none is registered
	Optional<Player> player(UUID uniqueId);

}
