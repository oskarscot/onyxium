package dev.onyxium.command;

import module java.base;

/// Describes a registered command.
///
/// @param name the declared path with normalised literals and any named placeholders
/// @param aliases alternative names for the last literal word
/// @param permission the required permission, or an empty string if none is required
/// @param description a short description for help listings
/// @param sourceType the source type accepted by the handler
/// @param arguments argument definitions in method parameter order
/// @param path all literals and arguments in input order, including arguments without placeholders
public record CommandDefinition(String name, List<String> aliases, String permission, String description,
		Class<? extends CommandSource> sourceType, List<ArgumentDefinition> arguments, List<CommandSegment> path) {

	/// Creates a definition with immutable copies of its aliases, arguments and path.
	public CommandDefinition

	{
		aliases = List.copyOf(aliases);
		arguments = List.copyOf(arguments);
		path = List.copyOf(path);
	}

	/// Formats the full path for help messages, for example `/foo <value> bar
	/// [reason...]`.
	///
	/// @return the command's usage text, including the leading slash
	public String usage() {
		return "/" + path.stream().map(segment -> switch (segment) {
			case CommandSegment.Literal literal -> literal.text();
			case CommandSegment.Argument argument -> argument.argumentDefinition().usage();
		}).collect(Collectors.joining(" "));
	}
}
