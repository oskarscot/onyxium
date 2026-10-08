package dev.onyxium.command;

import module java.base;

/// Registration and removal must not run concurrently with other operations.
/// Source subtypes restrict commands: a console-only handler accepts the host's console source type.
public final class CommandDispatcher<S> {

	Class<S> sourceType;

	BiPredicate<S, String> permissions;

	ArgumentParser argumentParser = new ArgumentParser();

	CommandTree tree = CommandTree.build(List.of());

	public CommandDispatcher(Class<S> sourceType, BiPredicate<S, String> permissions) {
		this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
		this.permissions = Objects.requireNonNull(permissions, "permissions");
	}

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
			.map(method -> RegisteredCommand.compile(handler, method, sourceType, argumentParser))
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

	/// Filters by the declared source type as well as permission, for source-specific
	/// help and trees.
	public List<CommandDefinition> commands(S source) {
		sourceType.cast(Objects.requireNonNull(source, "source"));

		return tree.commands()
			.stream()
			.map(RegisteredCommand::definition)
			.filter(command -> command.sourceType().isInstance(source))
			.filter(command -> permitted(command, source))
			.toList();
	}

	boolean permitted(CommandDefinition command, S source) {
		return command.permission().isEmpty() || permissions.test(source, command.permission());
	}

	void remove(List<RegisteredCommand> registrations) {
		tree = CommandTree.build(tree.commands().stream().filter(command -> !registrations.contains(command)).toList());
	}

}
