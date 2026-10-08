package dev.onyxium.command;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/// Published trees are immutable; aliases share their canonical node and its descendants.
record CommandTree<S>(Map<String, Node<S>> roots, List<RegisteredCommand<S>> commands) {

	static <S> CommandTree<S> build(List<RegisteredCommand<S>> commands) {
		var root = new MutableNode<S>();
		for (var command : commands) {
			var node = root;
			for (var segment : command.definition().name().split(" "))
				node = node.child(segment);
			if (node.command != null)
				throw new IllegalArgumentException("Duplicate command: " + command.definition().name());
			node.command = command;
		}
		for (var command : commands)
			addAliases(root, command);
		return new CommandTree<>(root.freeze(new IdentityHashMap<>()).children(), List.copyOf(commands));
	}

	static <S> void addAliases(MutableNode<S> root, RegisteredCommand<S> command) {
		var path = command.definition().name().split(" ");
		var parent = root;
		for (var index = 0; index < path.length - 1; index++)
			parent = parent.children.get(path[index]);
		var target = parent.children.get(path[path.length - 1]);
		for (var alias : command.definition().aliases()) {
			if (parent.children.putIfAbsent(alias, target) != null) {
				throw new IllegalArgumentException(
						"Conflicting alias '" + alias + "' for " + command.definition().name());
			}
		}
	}

	record Node<S>(Map<String, Node<S>> children, RegisteredCommand<S> command) {
	}

	static final class MutableNode<S> {

		Map<String, MutableNode<S>> children = new HashMap<>();

		RegisteredCommand<S> command;

		MutableNode<S> child(String name) {
			return children.computeIfAbsent(name, _ -> new MutableNode<>());
		}

		Node<S> freeze(IdentityHashMap<MutableNode<S>, Node<S>> memo) {
			var cached = memo.get(this);
			if (cached != null)
				return cached;
			var frozen = new HashMap<String, Node<S>>();
			for (var entry : children.entrySet())
				frozen.put(entry.getKey(), entry.getValue().freeze(memo));
			var result = new Node<>(Map.copyOf(frozen), command);
			memo.put(this, result);
			return result;
		}

	}
}
