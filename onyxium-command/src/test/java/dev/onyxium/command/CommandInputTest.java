package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThat;

import module java.base;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CommandInputTest {

	String raw;

	List<String> expected;

	public CommandInputTest(String raw, List<String> expected) {
		this.raw = raw;
		this.expected = expected;
	}

	@Parameterized.Parameters(name = "case={index}")
	public static List<Object[]> cases() {
		return List.of(new Object[] { " /foo\tbar  hello\nworld ", List.of("foo", "bar", "hello", "world") },
				new Object[] { "foo \"hello world\" '世界 👋'", List.of("foo", "hello world", "世界 👋") },
				new Object[] { "foo \"\" ''", List.of("foo", "", "") },
				new Object[] { "foo hello\\ world \"a\\\"b\" \\\\path",
						List.of("foo", "hello world", "a\"b", "\\path") },
				new Object[] { "foo Let's go --foo=bar", List.of("foo", "Let's", "go", "--foo=bar") },
				new Object[] { "foo pre\"hello world\"tail", List.of("foo", "prehello worldtail") },
				new Object[] { "", List.of() });
	}

	@Test
	public void decodesTokensAndPreservesPeek() {
		var input = new CommandInput(raw);
		for (var token : expected) {
			var firstPeek = input.peek();
			assertThat(input.peek()).isSameAs(firstPeek);
			assertThat(input.read().value()).isEqualTo(token);
		}
		assertThat(input.read()).isNull();
		assertThat(input.remaining()).isEqualTo("");
	}

}
