package dev.onyxium.proxy.command;

import module java.base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.onyxium.command.CommandDispatcher;
import dev.onyxium.proxy.api.command.ConsoleSource;

public final class ProxyConsole implements ConsoleSource {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConsole.class);

	CommandDispatcher commands;

	public ProxyConsole(CommandDispatcher commands) {
		this.commands = Objects.requireNonNull(commands, "commands");
	}

	@Override
	public boolean hasPermission(String permission) {
		return true;
	}

	@Override
	public void sendMessage(String message) {
		LOGGER.info("{}", message);
	}

	/// Reuses the login reader so buffered input after profile selection is preserved.
	/// EOF detaches the console without shutting down the proxy or closing stdin.
	public void read(BufferedReader input) {
		try {
			while (true) {
				var line = input.readLine();
				if (line == null) {
					return;
				}

				if (line.isBlank()) {
					continue;
				}

				var handled = commands.dispatch(this, line);
				if (!handled) {
					sendMessage("Unknown command.");
				}
			}
		}
		catch (IOException failure) {
			LOGGER.error("Could not read console input", failure);
		}
	}
}
