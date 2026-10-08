package dev.onyxium.command;

import module java.base;

/// Registration and removal must not run concurrently with other operations.
/// Source subtypes restrict commands: a console-only handler accepts the host's console source type.
public final class CommandDispatcher {

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
