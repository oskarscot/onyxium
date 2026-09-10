package dev.onyxium.proxy.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import dev.onyxium.proxy.api.network.NetworkManager;
import dev.onyxium.proxy.api.player.Player;

public interface ProxyServer {

	NetworkManager networkManager();

	Collection<Player> players();

	Optional<Player> player(UUID uniqueId);

}
