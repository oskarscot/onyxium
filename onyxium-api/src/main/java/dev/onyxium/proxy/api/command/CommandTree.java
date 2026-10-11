package dev.onyxium.proxy.api.command;

import module java.base;

/// An immutable tree of registered command paths. Each alias points to the same node as its original literal.
record CommandTree(Map<String, Node> roots, List<RegisteredCommand> commands) {

	static CommandTree build(List<RegisteredCommand> commands) {
		var root = new MutableNode();

		for (var command : commands) {
			var node = root;
			var lastLiteral = root;

			for (var segment : command.definition().path()) {
				switch (segment) {
					case CommandSegment.Literal literal -> {
						node = node.children.computeIfAbsent(literal.text(), _ -> new MutableNode());
						lastLiteral = node;
					}
					case CommandSegment.Argument argument -> node = node.argument(argument.argumentDefinition());
				}
			}

			if (node.command != null) {
				throw new IllegalArgumentException("Duplicate command: " + command.definition().name());
			}

			node.command = command;
			lastLiteral.aliases.addAll(command.definition().aliases());
		}

		return new CommandTree(root.freeze().children(), List.copyOf(commands));
	}

	record Node(Map<String, Node> children, ArgumentNode argument, RegisteredCommand command) {

		Stream<CommandDefinition> definitions() {
			var descendants = Stream.concat(children.values().stream().distinct(),
					Stream.ofNullable(argument).map(ArgumentNode::node));

			return Stream.concat(Stream.ofNullable(command).map(RegisteredCommand::definition),
					descendants.flatMap(Node::definitions));
		}

	}

	record ArgumentNode(ArgumentDefinition definition, Node node) {
	}

	static final class MutableNode {

		Map<String, MutableNode> children = new HashMap<>();

		Set<String> aliases = new HashSet<>();

		ArgumentDefinition argumentDefinition;

		MutableNode argument;

		RegisteredCommand command;

		MutableNode argument(ArgumentDefinition definition) {
			if (argument == null) {
				argumentDefinition = definition;
				argument = new MutableNode();
			}
			else if (argumentDefinition.type() != definition.type()
					|| argumentDefinition.optional() != definition.optional()
					|| argumentDefinition.greedy() != definition.greedy()) {
				throw new IllegalArgumentException("Conflicting argument branches: '" + argumentDefinition.name()
						+ "' and '" + definition.name() + "' must use the same type, optional and greedy settings.");
			}

			return argument;
		}

		Node freeze() {
			var frozen = new HashMap<String, Node>();

			for (var entry : children.entrySet()) {
				var child = entry.getValue().freeze();
				add(frozen, entry.getKey(), child);

				for (var alias : entry.getValue().aliases) {
					add(frozen, alias, child);
				}
			}

			var frozenArgument = argument == null ? null : new ArgumentNode(argumentDefinition, argument.freeze());

			return new Node(Map.copyOf(frozen), frozenArgument, command);
		}

		static void add(Map<String, Node> children, String name, Node child) {
			var previous = children.putIfAbsent(name, child);
			if (previous != null && previous != child) {
				throw new IllegalArgumentException("Conflicting command name or alias: " + name);
			}
		}

	}
}
