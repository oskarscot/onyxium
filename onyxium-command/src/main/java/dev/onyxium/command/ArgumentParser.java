package dev.onyxium.command;

import java.util.List;

/// A source-aware strategy for converting one decoded token, or one raw greedy tail.
/// Throw IllegalArgumentException for invalid input; successful parsing must return a non-null value.
@FunctionalInterface
public interface ArgumentParser<S, T> {

	T parse(S source, String input);

	/// Candidates need not be prefix-filtered; the dispatcher filters them without regard
	/// to case.
	default List<String> suggest(S source, String partial) {
		return List.of();
	}

}
