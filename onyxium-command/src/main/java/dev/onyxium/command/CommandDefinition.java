package dev.onyxium.command;

import java.util.List;
import java.util.stream.Collectors;

/// A canonical command path with leaf aliases, independent of its reflected method and transport.
public record CommandDefinition(String name, List<String> aliases, String permission, String description,
		Class<?> sourceType, List<ArgumentDefinition> arguments) {

	public CommandDefinition {
		aliases = List.copyOf(aliases);
		arguments = List.copyOf(arguments);
	}

	public String usage() {
		return "/" + name
				+ arguments.stream()
					.map(ArgumentDefinition::usage)
					.collect(Collectors.joining(" ", arguments.isEmpty() ? "" : " ", ""));
	}
}
