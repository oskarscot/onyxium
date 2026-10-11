package dev.onyxium.proxy.api.command;

/// Describes a command argument.
///
/// @param name the handler's parameter name
/// @param type the value type, with primitives boxed and `Optional` unwrapped
/// @param optional whether the caller can omit the argument
/// @param greedy whether the argument reads all remaining input
public record ArgumentDefinition(String name, Class<?> type, boolean optional, boolean greedy) {

	/// Formats the argument as `<name>` or `[name]`, adding `...` for a greedy argument.
	///
	/// @return the argument's usage text
	public String usage() {
		var value = name + (greedy ? "..." : "");

		return optional ? "[" + value + "]" : "<" + value + ">";
	}
}
