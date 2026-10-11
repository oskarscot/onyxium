package dev.onyxium.proxy.api.command;

/// A literal word or argument in a command path.
public sealed interface CommandSegment permits CommandSegment.Literal, CommandSegment.Argument {

	/// A fixed word, such as `foo` or `bar` in `foo {value} bar`.
	///
	/// @param text the normalised word to match
	record Literal(String text) implements CommandSegment {
	}

	/// An argument supplied by the caller, such as `{value}` in `foo {value} bar`.
	///
	/// @param argumentDefinition the argument's name, type and parsing options
	record Argument(ArgumentDefinition argumentDefinition) implements CommandSegment {
	}

}
