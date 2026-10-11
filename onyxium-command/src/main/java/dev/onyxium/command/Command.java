package dev.onyxium.command;

import module java.base;

/// Marks a method as a command handler.
///
/// The method must be public, non-static and return `void`. Its first parameter must be
/// [CommandSource] or a subtype; the remaining parameters define its arguments.
/// Compile handlers with `-parameters` to retain their argument names.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {

	/// The command path, with optional `{parameterName}` placeholders.
	///
	/// For example, `foo {value} bar` accepts `foo 42 bar` when `value` is an integer.
	/// Parameters without a placeholder follow the declared path in method parameter order.
	/// Each placeholder must name a parameter and can appear only once.
	///
	/// The path must start with a literal word. Optional arguments must follow all required
	/// arguments and literals. A [Greedy] argument must be last.
	///
	/// Literal matches take priority over arguments. Commands with the same prefix share an argument branch.
	/// Those arguments must use the same type and optional/greedy settings, though their names can differ.
	///
	/// @return the command path
	String name();

	/// {@return the permission required to use this command, or an empty string to skip the permission check}
	String permission() default "";

	/// Alternative names for the last literal word in the path.
	///
	/// For example, alias `baz` makes `foo {value} bar` also accept `foo {value} baz`.
	/// Aliases also work for subcommands registered beneath that word.
	///
	/// @return alternative names for the last literal word
	String[] aliases() default {};

	/// {@return a short description for help listings}
	String description() default "";

}
