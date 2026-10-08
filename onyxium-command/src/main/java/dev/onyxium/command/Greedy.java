package dev.onyxium.command;

import module java.base;

/// Makes the final String or Optional<String> argument consume the original remaining text.
/// Quotes, escapes and whitespace within the tail are preserved rather than tokenized.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Greedy {

}
