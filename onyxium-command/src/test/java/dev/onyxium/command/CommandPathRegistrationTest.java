package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import module java.base;
import org.junit.Test;

import dev.onyxium.command.CommandFixtures.ConsoleSource;
import dev.onyxium.command.CommandFixtures.PlayerSource;

public class CommandPathRegistrationTest {

	@Test
	public void normalisesLiteralsAndAppendsOnlyUnreferencedParameters() {
		var dispatcher = new CommandDispatcher();
		var registration = dispatcher.registerHandler(new Normalised());
		var definition = registration.commands().getFirst();

		assertThat(definition.name()).isEqualTo("foo {playerName} bar");
		assertThat(definition.usage()).isEqualTo("/foo <playerName> bar <amount>");
		assertThat(definition.aliases()).containsExactly("b");
		assertThat(definition.path()).containsExactly(new CommandSegment.Literal("foo"),
				new CommandSegment.Argument(definition.arguments().get(1)), new CommandSegment.Literal("bar"),
				new CommandSegment.Argument(definition.arguments().getFirst()));

		var source = new ConsoleSource(Set.of());
		dispatcher.dispatch(source, "FOO Alice B 5");
		assertThat(source.messages()).containsExactly("Alice:5");
	}

	@Test
	public void rejectsDuplicateShapesAtomicallyEvenWithDifferentParameterNames() {
		var dispatcher = new CommandDispatcher();
		var original = dispatcher.registerHandler(new Normalised()).commands();

		assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(new Duplicate()))
			.withMessageContaining("Duplicate command");
		assertThat(dispatcher.commands()).containsExactlyElementsOf(original);
	}

	@Test
	public void explicitAndImplicitArgumentsCannotRegisterTheSamePath() {
		var dispatcher = new CommandDispatcher();
		dispatcher.registerHandler(new Implicit());

		assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(new Explicit()))
			.withMessageContaining("Duplicate command");
	}

	@Test
	public void aliasesShareArgumentDescendantsAndUnregisterCleanly() {
		var dispatcher = new CommandDispatcher();
		var registration = dispatcher.registerHandler(new Parent());
		dispatcher.registerHandler(new Child());
		var source = new ConsoleSource(Set.of());

		dispatcher.dispatch(source, "p 7 c");
		assertThat(source.messages()).containsExactly("7");

		registration.unregister();
		assertThat(dispatcher.dispatch(source, "p 7 c")).isFalse();
		assertThat(dispatcher.dispatch(source, "parent 8 c")).isTrue();
		assertThat(source.messages()).containsExactly("7", "8");
	}

	@Test
	public void checksAccessBeforeConvertingArgumentsAndHidesRestrictedUsage() {
		var dispatcher = new CommandDispatcher();
		var conversions = new AtomicInteger();
		dispatcher.argumentParser().register(Value.class, input -> {
			conversions.incrementAndGet();
			return new Value(input);
		});
		dispatcher.registerHandler(new Secured());
		var source = new PlayerSource(Set.of());

		dispatcher.dispatch(source, "secret example run");
		dispatcher.dispatch(source, "console \"unfinished");
		dispatcher.dispatch(source, "secret example missing");

		assertThat(conversions.get()).isZero();
		assertThat(source.messages()).containsExactly("You do not have permission to use this command.",
				"This command is not available to this source.",
				"Unknown or incomplete command. Usage: /secret <value> show");

		dispatcher.dispatch(source, "secret example show");
		assertThat(conversions.get()).isEqualTo(1);
		assertThat(source.messages().getLast()).isEqualTo("example");
	}

	public static class Normalised {

		@Command(name = "  FoO   {playerName}\tBAR  ", aliases = "B")
		public void execute(CommandSource source, int amount, String playerName) {
			source.sendMessage(playerName + ":" + amount);
		}

	}

	public static class Duplicate {

		@Command(name = "foo {other} bar {count}")
		public void execute(CommandSource source, String other, int count) {
		}

	}

	public static class Implicit {

		@Command(name = "foo")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class Explicit {

		@Command(name = "foo {value}")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class Parent {

		@Command(name = "parent {value}", aliases = "p")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class Child {

		@Command(name = "parent {number} child", aliases = "c")
		public void execute(CommandSource source, int number) {
			source.sendMessage(Integer.toString(number));
		}

	}

	public record Value(String text) {
	}

	public static class Secured {

		@Command(name = "secret {value} run", permission = "test.secret")
		public void restricted(CommandSource source, Value value) {
			source.sendMessage(value.text());
		}

		@Command(name = "secret {value} show")
		public void available(CommandSource source, Value value) {
			source.sendMessage(value.text());
		}

		@Command(name = "console {value} run")
		public void console(ConsoleSource source, Value value) {
		}

	}

}
