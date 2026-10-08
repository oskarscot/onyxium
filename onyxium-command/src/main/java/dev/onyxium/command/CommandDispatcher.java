package dev.onyxium.command;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/// Reflection and signature validation happen during registration. Mutations publish a complete,
/// immutable snapshot so concurrent readers never observe a partially registered handler.
/// Source subtypes restrict commands: a console-only handler accepts the host's console source type.
public final class CommandDispatcher<S> {

	Class<S> sourceType;

	BiPredicate<S, String> permissions;

	ArgumentParser argumentParser = new ArgumentParser();

	volatile CommandTree<S> tree = CommandTree.build(List.of());

	public CommandDispatcher(Class<S> sourceType, BiPredicate<S, String> permissions) {
		this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
		this.permissions = Objects.requireNonNull(permissions, "permissions");
	}

	public ArgumentParser argumentParser() {
		return argumentParser;
	}

	/// Only annotated methods declared on the handler class are scanned. The whole
	/// handler is
	/// validated before publishing; closing the returned registration removes its
	/// commands.
	public synchronized CommandRegistration registerHandler(Object handler) {
		Objects.requireNonNull(handler, "handler");
		var additions = Stream.of(handler.getClass().getDeclaredMethods())
			.filter(method -> method.isAnnotationPresent(Command.class))
			.sorted(Comparator.comparing(Method::toGenericString))
			.map(method -> RegisteredCommand.compile(handler, method, sourceType, argumentParser))
			.toList();
		if (additions.isEmpty())
			throw new IllegalArgumentException("Handler has no declared @Command methods.");
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
			.filter(command -> permitted(command, source))
			.map(RegisteredCommand::definition)
			.toList();
	}

	boolean permitted(RegisteredCommand<S> command, S source) {
		return command.definition().sourceType().isInstance(source) && (command.definition().permission().isEmpty()
				|| permissions.test(source, command.definition().permission()));
	}

	synchronized void remove(List<RegisteredCommand<S>> registrations) {
		tree = CommandTree.build(tree.commands().stream().filter(command -> !registrations.contains(command)).toList());
	}

}
