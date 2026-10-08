package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThat;

import module java.base;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import dev.onyxium.command.CommandFixtures.ConsoleSource;
import dev.onyxium.command.CommandFixtures.PlayerSource;

@RunWith(Parameterized.class)
public class CommandDispatchTest {

	Scenario scenario;

	public CommandDispatchTest(Scenario scenario) {
		this.scenario = scenario;
	}

	@Parameterized.Parameters(name = "{0}")
	public static List<Scenario> cases() {
		return List.of(
				Scenario.command("ops", "root"),
				Scenario.command("/O missing", """
						Too many arguments. Usage: /ops | /ops console | /ops count <amount> [enabled] | /ops echo <text> | /ops fail | /ops raw [text...]"""),
				Scenario.command("/O C 2 true", "2:true"),
				Scenario.command("ops count 2", "2:none"),
				Scenario.command("ops echo \"hello world\"", "hello world"),
				Scenario.command("ops echo \"\"", ""),
				Scenario.command("ops raw Let's say \"hello  ", "Let's say \"hello  "),
				Scenario.command("ops raw", "none"),
				Scenario.command("ops count", "Missing argument 'amount'. Usage: /ops count <amount> [enabled]"),
				Scenario.command("ops count bad", "Invalid value for 'amount'. Usage: /ops count <amount> [enabled]"),
				Scenario.command("ops count 2 true extra", "Too many arguments. Usage: /ops count <amount> [enabled]"),
				Scenario.command("ops echo \"unfinished", "Unclosed quote. Usage: /ops echo <text>"),
				Scenario.command("group missing", "Unknown or incomplete command. Usage: /group nested child"),
				Scenario.command("group nested", "Unknown or incomplete command. Usage: /group nested child"),
				Scenario.command("ops console", "console"),
				Scenario.command("ops fail", "Command failed."),
				new Scenario("/backend \"unfinished", new ConsoleSource(Set.of()), false, List.of()),
				new Scenario("ops missing", new PlayerSource(Set.of()), true, List.of("""
						Too many arguments. Usage: /ops | /ops echo <text> | /ops fail | /ops raw [text...]""")),
				new Scenario("ops count 2", new PlayerSource(Set.of()), true,
						List.of("You do not have permission to use this command.")),
				new Scenario("ops console \"unfinished", new PlayerSource(Set.of("test.use")), true,
						List.of("This command is not available to this source.")));
	}

	@Test
	public void dispatchesOwnedCommandsAndLeavesUnknownRootsUntouched() {
		var dispatcher = new CommandDispatcher();
		dispatcher.registerHandler(new Commands());

		assertThat(dispatcher.dispatch(scenario.source(), scenario.input())).isEqualTo(scenario.handled());

		var messages = switch (scenario.source()) {
			case ConsoleSource source -> source.messages();
			case PlayerSource source -> source.messages();
			default -> throw new AssertionError("Unexpected fixture source");
		};
		assertThat(messages).containsExactlyElementsOf(scenario.messages());
	}

	record Scenario(String input, CommandSource source, boolean handled, List<String> messages) {
		static Scenario command(String input, String message) {
			return new Scenario(input, new ConsoleSource(Set.of("test.use")), true, List.of(message));
		}

		@Override
		public String toString() {
			return input;
		}
	}

	public static class Commands {

		@Command(name = "ops", aliases = { "o" })
		public void root(CommandSource source) {
			source.sendMessage("root");
		}

		@Command(name = "ops count", aliases = { "c" }, permission = "test.use")
		public void count(CommandSource source, int amount, Optional<Boolean> enabled) {
			source.sendMessage(amount + ":" + enabled.map(Object::toString).orElse("none"));
		}

		@Command(name = "ops echo")
		public void echo(CommandSource source, String text) {
			source.sendMessage(text);
		}

		@Command(name = "ops raw")
		public void raw(CommandSource source, @Greedy Optional<String> text) {
			source.sendMessage(text.orElse("none"));
		}

		@Command(name = "ops console")
		public void console(ConsoleSource source) {
			source.sendMessage("console");
		}

		@Command(name = "ops fail")
		public void fail(CommandSource source) {
			throw new IllegalArgumentException("Handler failure, not an argument error");
		}

		@Command(name = "group nested child")
		public void child(CommandSource source) {
			source.sendMessage("child");
		}
	}
}
