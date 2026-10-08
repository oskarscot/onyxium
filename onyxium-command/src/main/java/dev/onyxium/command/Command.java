package dev.onyxium.command;

import module java.base;

/// Marks a public instance method returning void. Its first parameter is the source;
/// remaining parameter names become argument names and require compilation with `-parameters`.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {

	String name();

	String permission() default "";

	/// Aliases replace only the last segment: `foo bar` with alias `baz` also matches
	/// `foo baz`.
	String[] aliases() default {};

	String description() default "";

}
