package dev.onyxium.command;

import java.util.List;

/// Owns one handler's registrations. Closing is idempotent and removes its commands and aliases.
public final class CommandRegistration implements AutoCloseable {

	List<CommandDefinition> commands;

	Runnable removal;

	CommandRegistration(List<CommandDefinition> commands, Runnable removal) {
		this.commands = List.copyOf(commands);
		this.removal = removal;
	}

	public List<CommandDefinition> commands() {
		return commands;
	}

	@Override
	public synchronized void close() {
		if (removal != null) {
			removal.run();
			removal = null;
		}
	}

}
