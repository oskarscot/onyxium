package dev.onyxium.command;

import module java.base;

/// Owns one handler's registrations. Unregistering removes its commands and aliases; repeated calls are safe.
public final class CommandRegistration {

	List<CommandDefinition> commands;

	Runnable removal;

	CommandRegistration(List<CommandDefinition> commands, Runnable removal) {
		this.commands = List.copyOf(commands);
		this.removal = removal;
	}

	public List<CommandDefinition> commands() {
		return commands;
	}

	public void unregister() {
		if (removal != null) {
			removal.run();
			removal = null;
		}
	}

}
