package dev.onyxium.proxy.command;

import module java.base;

import dev.onyxium.command.Command;
import dev.onyxium.command.CommandSource;
import dev.onyxium.command.Greedy;
import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.command.ConsoleSource;
import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.api.player.Player;

// TODO: Clean me up once permission handling is added
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

	@Command(name = "onyxium kick", description = "Kicks the target player from the proxy")
	public void kick(Player source, Player target, @Greedy Optional<String> reason) {
		if(target == null) {
			source.sendMessage(FormattedMessage.builder()
					.text("This player is not online.")
					.color("#ff5555")
					.build()
			);
			return;
		}

		target.disconnect(FormattedMessage.builder()
				.text("You have been kicked from the proxy: ")
				.color("#777777")
				.append(reason.orElse("No reason provided."))
				.color("#fff154")
			.build());
	}
}
