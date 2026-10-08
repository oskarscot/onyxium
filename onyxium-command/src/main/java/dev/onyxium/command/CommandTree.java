package dev.onyxium.command;

import module java.base;

/// Aliases share their canonical node and its descendants in the immutable tree.
record CommandTree(Map<String, Node> roots, List<RegisteredCommand> commands) {

	static CommandTree build(List<RegisteredCommand> commands) {
		var root = new MutableNode();

		for (var command : commands) {
			var node = root;
			for (var segment : command.definition().name().split(" ")) {
				node = node.children.computeIfAbsent(segment, _ -> new MutableNode());
			}

			if (node.command != null) {
				throw new IllegalArgumentException("Duplicate command: " + command.definition().name());
			}

			node.command = command;
		}

		return new CommandTree(root.freeze().children(), List.copyOf(commands));
	}

	record Node(Map<String, Node> children, RegisteredCommand command) {
	}

	static final class MutableNode {

		Map<String, MutableNode> children = new HashMap<>();

		RegisteredCommand command;

		Node freeze() {
			var frozen = new HashMap<String, Node>();

			for (var entry : children.entrySet()) {
				var child = entry.getValue().freeze();
				add(frozen, entry.getKey(), child);

				if (child.command() != null) {
					for (var alias : child.command().definition().aliases()) {
						add(frozen, alias, child);
					}
				}
			}

			return new Node(Map.copyOf(frozen), command);
		}

		static void add(Map<String, Node> children, String name, Node child) {
			var previous = children.putIfAbsent(name, child);
			if (previous != null) {
				throw new IllegalArgumentException("Conflicting command name or alias: " + name);
			}
		}

	}
}
