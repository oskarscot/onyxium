package dev.onyxium.command;

import module java.base;

/// Registers command handlers and dispatches input to them.
///
/// A command is available only when the source matches its declared type and has its required permission.
/// Handlers can be registered or removed while commands are being dispatched. An in-progress
/// dispatch may finish using a removed handler. Register argument converters before concurrent use.
public final class CommandDispatcher {

	static final System.Logger LOGGER = System.getLogger(CommandDispatcher.class.getName());

	final ArgumentParser argumentParser = new ArgumentParser();

	private final ReentrantLock registrationLock = new ReentrantLock();

	volatile CommandTree tree = CommandTree.build(List.of());

	/// Creates a dispatcher with no registered commands.
	public CommandDispatcher() {
	}

	/// {@return the parser used to convert command arguments}
	///
	/// Register custom converters before their handlers.
	public ArgumentParser argumentParser() {
		return argumentParser;
	}

	/// Registers the object's declared [Command] methods, ignoring inherited methods.
	///
	/// If any method is invalid or conflicts with an existing command, no commands are added.
	/// This overload registers without an owner. Use the returned registration to remove the commands.
	///
	/// @param handler the object whose command methods will be invoked
	/// @return the handler's registered commands and their unregistration handle
	/// @throws IllegalArgumentException if the handler has no commands or any declaration is invalid or conflicts
	public CommandRegistration registerHandler(Object handler) {
		return register(null, handler);
	}

	/// Registers a handler's commands under an owner so they can be removed together.
	///
	/// Owners are compared by identity. The returned handle can still remove this handler separately.
	/// If any declaration is invalid or conflicts with an existing command, no commands are added.
	///
	/// @param owner the object responsible for the commands
	/// @param handler the object whose declared [Command] methods will be invoked
	/// @return the handler's registered commands and their unregistration handle
	/// @throws IllegalArgumentException if the handler has no commands or any declaration is invalid or conflicts
	public CommandRegistration registerHandler(Object owner, Object handler) {
		return register(Objects.requireNonNull(owner, "owner"), handler);
	}

	CommandRegistration register(Object owner, Object handler) {
		Objects.requireNonNull(handler, "handler");

		var additions = Stream.of(handler.getClass().getDeclaredMethods())
			.filter(method -> method.isAnnotationPresent(Command.class))
			.sorted(Comparator.comparing(Method::toGenericString))
			.map(method -> RegisteredCommand.compile(owner, handler, method, argumentParser))
			.toList();

		if (additions.isEmpty()) {
			throw new IllegalArgumentException("Handler has no declared @Command methods.");
		}

		registrationLock.lock();
		try {
			var updated = Stream.concat(tree.commands().stream(), additions.stream()).toList();
			tree = CommandTree.build(updated);
		}
		finally {
			registrationLock.unlock();
		}

		return new CommandRegistration(additions.stream().map(RegisteredCommand::definition).toList(),
				() -> remove(additions::contains));
	}

	/// Removes every command and alias registered under this owner, compared by identity.
	/// Other owners and registrations without an owner are unaffected. Repeated calls have no further effect.
	///
	/// @param owner the owner whose commands should be removed
	public void unregister(Object owner) {
		Objects.requireNonNull(owner, "owner");
		remove(command -> command.owner() == owner);
	}

	/// {@return all registered commands, regardless of source type or permission}
	public List<CommandDefinition> commands() {
		return tree.commands().stream().map(RegisteredCommand::definition).toList();
	}

	/// Processes command input on the calling thread. A leading `/` is optional.
	///
	/// Returns `false` when the first word matches no registered command or alias, so the host can forward it.
	/// A recognised command returns `true` even if its arguments are invalid, access is denied or its handler fails.
	///
	/// @param source the caller executing the command
	/// @param command the complete command input
	/// @return whether the dispatcher handled the input
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
			var values = new ArrayList<String>();

			while (true) {
				var literal = input.literal();
				var child = node.children().get(literal.toLowerCase(Locale.ROOT));

				if (child != null) {
					input.read();
					node = child;
					continue;
				}

				if (node.command() != null && (literal.isEmpty() || node.argument() == null)) {
					execute(source, input, node, values);
					break;
				}

				var branch = node.argument();
				if (branch == null) {
					sendUsage(source, node, "Unknown or incomplete command.");
					break;
				}

				if (!accessible(source, branch.node())) {
					break;
				}

				var argument = branch.definition();
				if (literal.isEmpty() && !argument.optional()) {
					sendUsage(source, node, "Missing argument '" + argument.name() + "'.");
					break;
				}

				try {
					values.add(literal.isEmpty() ? null : argument.greedy() ? input.remaining() : input.read().value());
				}
				catch (IllegalArgumentException failure) {
					sendUsage(source, node, failure.getMessage());
					break;
				}

				node = branch.node();
			}
		}
		catch (RuntimeException failure) {
			LOGGER.log(System.Logger.Level.ERROR, "Command /" + root + " failed", failure);
			source.sendMessage("Command failed.");
		}

		return true;
	}

	void execute(CommandSource source, CommandInput input, CommandTree.Node node, List<String> values) {
		var command = node.command();
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
			arguments = arguments(source, input, definition, values);
		}
		catch (IllegalArgumentException failure) {
			sendUsage(source, node, failure.getMessage());
			return;
		}

		command.invoke(arguments);
	}

	/// Checks whether the source can use any command beneath this node before reading an argument.
	boolean accessible(CommandSource source, CommandTree.Node node) {
		var definitions = node.definitions().filter(command -> command.sourceType().isInstance(source)).toList();
		if (definitions.isEmpty()) {
			source.sendMessage("This command is not available to this source.");
			return false;
		}

		if (definitions.stream().noneMatch(command -> permitted(command, source))) {
			source.sendMessage("You do not have permission to use this command.");
			return false;
		}

		return true;
	}

	void sendUsage(CommandSource source, CommandTree.Node node, String message) {
		var usage = node.definitions()
			.filter(command -> command.sourceType().isInstance(source))
			.filter(command -> permitted(command, source))
			.map(CommandDefinition::usage)
			.sorted()
			.collect(Collectors.joining(" | "));

		source.sendMessage(usage.isEmpty() ? message : message + " Usage: " + usage);
	}

	Object[] arguments(CommandSource source, CommandInput input, CommandDefinition command, List<String> values) {
		var definitions = command.arguments();
		var arguments = new Object[definitions.size() + 1];
		arguments[0] = source;

		var index = 0;
		for (var segment : command.path()) {
			if (segment instanceof CommandSegment.Argument argument) {
				var definition = argument.argumentDefinition();
				arguments[definitions.indexOf(definition) + 1] = argument(values.get(index++), definition);
			}
		}

		if (!input.remaining().isEmpty()) {
			throw new IllegalArgumentException("Too many arguments.");
		}

		return arguments;
	}

	Object argument(String value, ArgumentDefinition argument) {
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

	/// {@return commands the source can use, checking both its type and permissions}
	///
	/// @param source the caller to check
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

	void remove(Predicate<RegisteredCommand> matches) {
		registrationLock.lock();
		try {
			tree = CommandTree.build(tree.commands().stream().filter(matches.negate()).toList());
		}
		finally {
			registrationLock.unlock();
		}
	}

}
