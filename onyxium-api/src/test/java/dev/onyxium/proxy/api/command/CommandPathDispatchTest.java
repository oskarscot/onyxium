package dev.onyxium.proxy.api.command;

import static org.assertj.core.api.Assertions.assertThat;

import module java.base;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import dev.onyxium.proxy.api.command.CommandFixtures.ConsoleSource;

@RunWith(Parameterized.class)
public class CommandPathDispatchTest {

	String input;

	String expected;

	public CommandPathDispatchTest(String input, String expected) {
		this.input = input;
		this.expected = expected;
	}

	@Parameterized.Parameters(name = "{0}")
	public static List<Object[]> cases() {
		return List.of(
				new Object[] { "foo 42 bar", "middle:42" },
				new Object[] { "foo bar 42", "trailing:42" },
				new Object[] { "/FOO -7 B", "middle:-7" },
				new Object[] { "foo b 7", "trailing:7" },
				new Object[] { "foo bad bar", "Invalid value for 'value'. Usage: /foo <value> bar" },
				new Object[] { "foo bar bad", "Invalid value for 'value'. Usage: /foo bar <value>" },
				new Object[] { "foo 42", "Unknown or incomplete command. Usage: /foo <value> bar" },
				new Object[] { "foo 42 wrong", "Unknown or incomplete command. Usage: /foo <value> bar" },
				new Object[] { "foo 42 bar extra", "Too many arguments. Usage: /foo <value> bar" },
				new Object[] { "foo bar", "Missing argument 'value'. Usage: /foo bar <value>" },
				new Object[] { "swap hello then 42", "42:hello" },
				new Object[] { "mix Alice with true", "Alice:true" },
				new Object[] { "user \"Alice Smith\" show", "show:Alice Smith" },
				new Object[] { "user Alice\\ Smith show", "show:Alice Smith" },
				new Object[] { "user \"\" show", "show:" },
				new Object[] { "user Alice kick", "kick:Alice:none" },
				new Object[] { "user Alice remove Let's say \"hello  ", "kick:Alice:Let's say \"hello  " },
				new Object[] { "maybe", "none" },
				new Object[] { "maybe 3", "3" },
				new Object[] { "maybe bad", "Invalid value for 'value'. Usage: /maybe [value]" },
				new Object[] { "say Let's say \"hello  ", "Let's say \"hello  " },
				new Object[] { "say", "Missing argument 'text'. Usage: /say <text...>" },
				new Object[] { "choose anything end", "anything" },
				new Object[] { "choose fixed 2", "fixed:2" },
				new Object[] { "choose fixed end", "Invalid value for 'number'. Usage: /choose fixed <number>" },
				new Object[] { "ordered 7", "7:none" },
				new Object[] { "ordered 7 label", "7:label" });
	}

	@Test
	public void dispatchesArgumentsAtTheirDeclaredPositions() {
		var dispatcher = new CommandDispatcher();
		dispatcher.registerHandler(new Commands());
		var source = new ConsoleSource(Set.of());

		assertThat(dispatcher.dispatch(source, input)).isTrue();
		assertThat(source.messages()).containsExactly(expected);
	}

	public static class Commands {

		@Command(name = "foo {value} bar", aliases = "b")
		public void middle(CommandSource source, Integer value) {
			source.sendMessage("middle:" + value);
		}

		@Command(name = "foo bar", aliases = "b")
		public void trailing(CommandSource source, Integer value) {
			source.sendMessage("trailing:" + value);
		}

		@Command(name = "swap {second} then {first}")
		public void reordered(CommandSource source, int first, String second) {
			source.sendMessage(first + ":" + second);
		}

		@Command(name = "mix {target} with")
		public void mixed(CommandSource source, boolean enabled, String target) {
			source.sendMessage(target + ":" + enabled);
		}

		@Command(name = "user {name} show")
		public void show(CommandSource source, String name) {
			source.sendMessage("show:" + name);
		}

		@Command(name = "user {player} kick", aliases = "remove")
		public void kick(CommandSource source, String player, @Greedy Optional<String> reason) {
			source.sendMessage("kick:" + player + ":" + reason.orElse("none"));
		}

		@Command(name = "maybe {value}")
		public void optional(CommandSource source, Optional<Integer> value) {
			source.sendMessage(value.map(Object::toString).orElse("none"));
		}

		@Command(name = "say {text}")
		public void greedy(CommandSource source, @Greedy String text) {
			source.sendMessage(text);
		}

		@Command(name = "choose {value} end")
		public void stringBranch(CommandSource source, String value) {
			source.sendMessage(value);
		}

		@Command(name = "choose fixed")
		public void literalBranch(CommandSource source, int number) {
			source.sendMessage("fixed:" + number);
		}

		@Command(name = "ordered {number} {label}")
		public void optionalReordered(CommandSource source, Optional<String> label, int number) {
			source.sendMessage(number + ":" + label.orElse("none"));
		}

	}

}
