package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class CommandInputBoundaryTest {

	@Test
	public void greedyTailRetainsRawTextAfterLookahead() {
		var input = new CommandInput(" /foo bar   a  \"quoted value\" \\text  ");
		input.read();
		input.read();
		input.peek();

		assertThat(input.remaining()).isEqualTo("a  \"quoted value\" \\text  ");
		assertThat(input.read()).isNull();
	}

	@Test
	public void greedyTailDoesNotRequireBalancedQuotes() {
		var input = new CommandInput("foo Let's say \"hello");
		input.read();

		assertThat(input.remaining()).isEqualTo("Let's say \"hello");
	}

}
