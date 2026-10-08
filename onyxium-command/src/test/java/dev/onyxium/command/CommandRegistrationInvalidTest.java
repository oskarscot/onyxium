package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import module java.base;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import dev.onyxium.command.CommandFixtures.Source;

@RunWith(Parameterized.class)
public class CommandRegistrationInvalidTest {

	Object handler;

	String message;

	public CommandRegistrationInvalidTest(Object handler, String message) {
		this.handler = handler;
		this.message = message;
	}

	@Parameterized.Parameters(name = "case={index}")
	public static List<Object[]> cases() {
		return List.of(new Object[] { new MissingSource(), "first parameter" },
				new Object[] { new StaticHandler(), "public instance" },
				new Object[] { new NonVoidHandler(), "returning void" },
				new Object[] { new InaccessibleHandler(), "public instance" },
				new Object[] { new WrongSource(), "first parameter" },
				new Object[] { new UnsupportedType(), "No argument parser" },
				new Object[] { new OptionalBeforeRequired(), "Required arguments must precede" },
				new Object[] { new GreedyBeforeArgument(), "Greedy arguments must be last" },
				new Object[] { new WildcardArgument(), "concrete classes" },
				new Object[] { new PathAlias(), "Invalid command segment" },
				new Object[] { new DuplicateAliases(), "Aliases must be distinct" },
				new Object[] { new EmptyPath(), "cannot be empty" },
				new Object[] { new VarargsHandler(), "without varargs" });
	}

	@Test
	public void rejectsAmbiguousOrUnsupportedDeclarations() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);

		assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(handler))
			.withMessageContaining(message);
	}

	public static class MissingSource {

		@Command(name = "foo")
		public void execute() {
		}

	}

	public static class StaticHandler {

		@Command(name = "foo")
		public static void execute(Source source) {
		}

	}

	public static class NonVoidHandler {

		@Command(name = "foo")
		public int execute(Source source) {
			return 1;
		}

	}

	public static class InaccessibleHandler {

		@Command(name = "foo")
		void execute(Source source) {
		}

	}

	public static class WrongSource {

		@Command(name = "foo")
		public void execute(String source) {
		}

	}

	public static class UnsupportedType {

		@Command(name = "foo")
		public void execute(Source source, Path path) {
		}

	}

	public static class OptionalBeforeRequired {

		@Command(name = "foo")
		public void execute(Source source, Optional<String> first, int second) {
		}

	}

	public static class GreedyBeforeArgument {

		@Command(name = "foo")
		public void execute(Source source, @Greedy String first, String second) {
		}

	}

	public static class WildcardArgument {

		@Command(name = "foo")
		public void execute(Source source, Optional<?> first) {
		}

	}

	public static class PathAlias {

		@Command(name = "foo bar", aliases = { "foo baz" })
		public void execute(Source source) {
		}

	}

	public static class DuplicateAliases {

		@Command(name = "foo bar", aliases = { "bar" })
		public void execute(Source source) {
		}

	}

	public static class EmptyPath {

		@Command(name = " ")
		public void execute(Source source) {
		}

	}

	public static class VarargsHandler {

		@Command(name = "foo")
		public void execute(Source source, String... values) {
		}

	}

}
