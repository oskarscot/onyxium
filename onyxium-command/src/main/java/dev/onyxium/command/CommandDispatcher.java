package dev.onyxium.command;

import module java.base;

/// Registration and removal must not run concurrently with other operations.
/// Source subtypes restrict commands: a console-only handler accepts the host's console source type.
public final class CommandDispatcher {

	static final System.Logger LOGGER = System.getLogger(CommandDispatcher.class.getName());

	ArgumentParser argumentParser = new ArgumentParser();

	CommandTree tree = CommandTree.build(List.of());

	public ArgumentParser argumentParser() {
		return argumentParser;
	}

	/// Inherited methods are ignored. Validation is atomic: a failure leaves
	/// existing registrations intact. Close the returned registration to remove it.
	public CommandRegistration registerHandler(Object handler) {
		Objects.requireNonNull(handler, "handler");

		var additions = Stream.of(handler.getClass().getDeclaredMethods())
			.filter(method -> method.isAnnotationPresent(Command.class))
			.sorted(Comparator.comparing(Method::toGenericString))
			.map(method -> RegisteredCommand.compile(handler, method, argumentParser))
			.toList();

		if (additions.isEmpty()) {
			throw new IllegalArgumentException("Handler has no declared @Command methods.");
		}

		var updated = Stream.concat(tree.commands().stream(), additions.stream()).toList();
		tree = CommandTree.build(updated);

		return new CommandRegistration(additions.stream().map(RegisteredCommand::definition).toList(),
				() -> remove(additions));
	}

	public List<CommandDefinition> commands() {
		return tree.commands().stream().map(RegisteredCommand::definition).toList();
	}

	/// Executes on the calling thread. Returns false only for an unknown root, allowing
	/// the host to forward it. An owned root is consumed even when validation or execution fails.
	public boolean dispatch(CommandSource source, String command) {
		Objects.requireNonNull(source, "source");
		var input = new CommandInput(Objects.requireNonNull(command, "command"));
		var root = input.literal().toLowerCase(Locale.ROOT);
		var node = tree.roots().get(root);

		if (node == null) {
			return false;
		}

		try {
			input.read();
			while (!node.children().isEmpty()) {
				var child = node.children().get(input.literal().toLowerCase(Locale.ROOT));
				if (child == null) {
					break;
				}

				input.read();
				node = child;
			}

			execute(source, input, node.command());
		}
		catch (RuntimeException failure) {
			LOGGER.log(System.Logger.Level.ERROR, "Command /" + root + " failed", failure);
			source.sendMessage("Command failed.");
		}

		return true;
	}

	void execute(CommandSource source, CommandInput input, RegisteredCommand command) {
		if (command == null) {
			source.sendMessage("Unknown or incomplete command.");
			return;
		}

		var definition = command.definition();
		if (!definition.sourceType().isInstance(source)) {
			source.sendMessage("This command is not available to this source.");
			return;
		}

		if (!permitted(definition, source)) {
			source.sendMessage("You do not have permission to use this command.");
			return;
		}

		Object[] arguments;
		try {
			arguments = arguments(source, input, definition);
		}
		catch (IllegalArgumentException failure) {
			source.sendMessage(failure.getMessage() + " Usage: " + definition.usage());
			return;
		}

		command.invoke(arguments);
	}

	Object[] arguments(CommandSource source, CommandInput input, CommandDefinition command) {
		var definitions = command.arguments();
		var arguments = new Object[definitions.size() + 1];
		arguments[0] = source;

		for (var index = 0; index < definitions.size(); index++) {
			arguments[index + 1] = argument(input, definitions.get(index));
		}

		if (!input.remaining().isEmpty()) {
			throw new IllegalArgumentException("Too many arguments.");
		}

		return arguments;
	}

	Object argument(CommandInput input, ArgumentDefinition argument) {
		String value;
		if (argument.greedy()) {
			value = input.remaining();
			if (value.isEmpty()) {
				value = null;
			}
		}
		else {
			var token = input.read();
			value = token == null ? null : token.value();
		}

		if (value == null) {
			if (argument.optional()) {
				return Optional.empty();
			}

			throw new IllegalArgumentException("Missing argument '" + argument.name() + "'.");
		}

		var parsed = argumentParser.parse(value, argument.type(),
				() -> new IllegalArgumentException("Invalid value for '" + argument.name() + "'."));

		return argument.optional() ? Optional.of(parsed) : parsed;
	}

	/// Filters by the declared source type as well as permission, for source-specific
	/// help and trees.
	public List<CommandDefinition> commands(CommandSource source) {
		Objects.requireNonNull(source, "source");

		return tree.commands()
			.stream()
			.map(RegisteredCommand::definition)
			.filter(command -> command.sourceType().isInstance(source))
			.filter(command -> permitted(command, source))
			.toList();
	}

	static boolean permitted(CommandDefinition command, CommandSource source) {
		return command.permission().isEmpty() || source.hasPermission(command.permission());
	}

	void remove(List<RegisteredCommand> registrations) {
		tree = CommandTree.build(tree.commands().stream().filter(command -> !registrations.contains(command)).toList());
	}

}
