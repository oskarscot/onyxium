package dev.onyxium.command;

import module java.base;

/// The commands registered by one handler, with a handle to unregister them together.
public final class CommandRegistration {

	List<CommandDefinition> commands;

	Runnable removal;

	CommandRegistration(List<CommandDefinition> commands, Runnable removal) {
		this.commands = List.copyOf(commands);
		this.removal = removal;
	}

	/// {@return the commands originally registered by this handler, even after unregistration}
	public List<CommandDefinition> commands() {
		return commands;
	}

	/// Removes this handler's commands and aliases. Calling this more than once has no further effect.
	public void unregister() {
		if (removal != null) {
			removal.run();
			removal = null;
		}
	}

}
