package dev.onyxium.proxy.command;

import module java.base;

import dev.onyxium.command.Command;
import dev.onyxium.command.CommandSource;
import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.command.ConsoleSource;
import dev.onyxium.proxy.api.player.Player;

public final class ProxyCommands {

	ProxyServer proxy;

	public ProxyCommands(ProxyServer proxy) {
		this.proxy = proxy;
	}

	@Command(name = "onyxium", aliases = { "proxy" }, description = "Show proxy status.")
	public void status(CommandSource source) {
		source.sendMessage("Onyxium proxy: %d player(s) connected.".formatted(proxy.players().size()));
	}

	@Command(name = "onyxium players", description = "List players connected to the proxy.")
	public void players(CommandSource source) {
		var players = proxy.players();
		var names = players.stream()
				.map(Player::username)
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.collect(Collectors.joining(", "));

		source.sendMessage("Online players (%d): %s".formatted(players.size(), names.isEmpty() ? "none" : names));
	}

	@Command(name = "onyxium stop", description = "Shuts down the proxy")
	public void stop(ConsoleSource source) {
		source.sendMessage("Stopping proxy...");
		proxy.shutdown();
	}
}
