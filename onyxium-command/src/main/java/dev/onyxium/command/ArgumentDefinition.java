package dev.onyxium.command;

/// Describes the parsed value type after unwrapping Optional and boxing primitive types.
public record ArgumentDefinition(String name, Class<?> type, boolean optional, boolean greedy) {

	public String usage() {
		var value = name + (greedy ? "..." : "");
		return optional ? "[" + value + "]" : "<" + value + ">";
	}
}
