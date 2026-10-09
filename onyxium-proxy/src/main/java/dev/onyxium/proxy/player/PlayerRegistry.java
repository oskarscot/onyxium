package dev.onyxium.proxy.player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerRegistry {

	private final ConcurrentHashMap<UUID, ProxyPlayer> players = new ConcurrentHashMap<>();

	public boolean register(ProxyPlayer player) {
		if (players.putIfAbsent(player.uuid(), player) != null)
			return false;
		player.connection().onClose(() -> players.remove(player.uuid(), player));
		return true;
	}

	public Optional<ProxyPlayer> player(String username) {
		return players.values().stream().filter(proxyPlayer -> proxyPlayer.username().equals(username)).findFirst();
	}

	public Optional<ProxyPlayer> player(UUID uuid) {
		return Optional.ofNullable(players.get(uuid));
	}

	public Collection<ProxyPlayer> players() {
		return List.copyOf(players.values());
	}

}
