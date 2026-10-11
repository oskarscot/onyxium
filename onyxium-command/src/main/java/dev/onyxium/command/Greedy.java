package dev.onyxium.command;

import module java.base;

/// Reads all remaining input into a `String` or `Optional<String>` argument.
///
/// The argument must be last in the command path. Leading whitespace is skipped;
/// quotes, escapes and all subsequent whitespace are preserved as written.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Greedy {

}
