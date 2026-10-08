package dev.onyxium.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Makes the final String or Optional<String> argument consume the original remaining text.
/// Quotes, escapes and whitespace within the tail are preserved rather than tokenized.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Greedy {

}
