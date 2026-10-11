package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import module java.base;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


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
				new Object[] { new NonVoidHandler(), "returning void" },
				new Object[] { new WrongSource(), "first parameter" },
				new Object[] { new UnsupportedType(), "No argument parser" },
				new Object[] { new OptionalBeforeRequired(), "Required arguments must precede" },
				new Object[] { new GreedyBeforeArgument(), "Greedy arguments must be last" },
				new Object[] { new WildcardArgument(), "concrete classes" },
				new Object[] { new PathAlias(), "Invalid command segment" },
				new Object[] { new EmptyPath(), "cannot be empty" },
				new Object[] { new VarargsHandler(), "without varargs" },
				new Object[] { new UnknownPlaceholder(), "Unknown argument placeholder" },
				new Object[] { new UnclosedPlaceholder(), "Invalid argument placeholder" },
				new Object[] { new PartialPlaceholder(), "Invalid argument placeholder" },
				new Object[] { new NestedPlaceholder(), "Invalid argument placeholder" },
				new Object[] { new EmptyPlaceholder(), "Invalid argument placeholder" },
				new Object[] { new DuplicatePlaceholder(), "Duplicate argument placeholder" },
				new Object[] { new ArgumentRoot(), "must start with a literal" },
				new Object[] { new OptionalBeforeLiteral(), "Literal segments cannot follow optional" },
				new Object[] { new GreedyBeforeLiteral(), "Greedy arguments must be last" },
				new Object[] { new ConflictingTypes(), "Conflicting argument branches" },
				new Object[] { new ConflictingOptional(), "Conflicting argument branches" },
				new Object[] { new ConflictingGreedy(), "Conflicting argument branches" },
				new Object[] { new InvalidLiteral(), "Invalid command segment" },
				new Object[] { new DuplicateAlias(), "Aliases must be distinct" });
	}

	@Test
	public void rejectsAmbiguousOrUnsupportedDeclarations() {
		var dispatcher = new CommandDispatcher();

		assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(handler))
			.withMessageContaining(message);
	}

	public static class MissingSource {

		@Command(name = "foo")
		public void execute() {
		}

	}

	public static class NonVoidHandler {

		@Command(name = "foo")
		public int execute(CommandSource source) {
			return 1;
		}

	}

	public static class WrongSource {

		@Command(name = "foo")
		public void execute(String source) {
		}

	}

	public static class UnsupportedType {

		@Command(name = "foo")
		public void execute(CommandSource source, Path path) {
		}

	}

	public static class OptionalBeforeRequired {

		@Command(name = "foo")
		public void execute(CommandSource source, Optional<String> first, int second) {
		}

	}

	public static class GreedyBeforeArgument {

		@Command(name = "foo")
		public void execute(CommandSource source, @Greedy String first, String second) {
		}

	}

	public static class WildcardArgument {

		@Command(name = "foo")
		public void execute(CommandSource source, Optional<?> first) {
		}

	}

	public static class PathAlias {

		@Command(name = "foo bar", aliases = { "foo baz" })
		public void execute(CommandSource source) {
		}

	}

	public static class EmptyPath {

		@Command(name = " ")
		public void execute(CommandSource source) {
		}

	}

	public static class VarargsHandler {

		@Command(name = "foo")
		public void execute(CommandSource source, String... values) {
		}

	}

	public static class UnknownPlaceholder {

		@Command(name = "foo {missing}")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class UnclosedPlaceholder {

		@Command(name = "foo {value")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class PartialPlaceholder {

		@Command(name = "foo {value}bar")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class NestedPlaceholder {

		@Command(name = "foo {{value}}")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class EmptyPlaceholder {

		@Command(name = "foo {}")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class DuplicatePlaceholder {

		@Command(name = "foo {value} bar {value}")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class ArgumentRoot {

		@Command(name = "{value} foo")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class OptionalBeforeLiteral {

		@Command(name = "foo {value} bar")
		public void execute(CommandSource source, Optional<Integer> value) {
		}

	}

	public static class GreedyBeforeLiteral {

		@Command(name = "foo {value} bar")
		public void execute(CommandSource source, @Greedy String value) {
		}

	}

	public static class ConflictingTypes {

		@Command(name = "foo {value} bar")
		public void first(CommandSource source, int value) {
		}

		@Command(name = "foo {value} baz")
		public void second(CommandSource source, String value) {
		}

	}

	public static class ConflictingOptional {

		@Command(name = "foo {value}")
		public void first(CommandSource source, Optional<Integer> value) {
		}

		@Command(name = "foo {value} bar")
		public void second(CommandSource source, int value) {
		}

	}

	public static class ConflictingGreedy {

		@Command(name = "foo {value}")
		public void first(CommandSource source, @Greedy String value) {
		}

		@Command(name = "foo {value} bar")
		public void second(CommandSource source, String value) {
		}

	}

	public static class InvalidLiteral {

		@Command(name = "foo bad! {value}")
		public void execute(CommandSource source, int value) {
		}

	}

	public static class DuplicateAlias {

		@Command(name = "FOO {value}", aliases = "foo")
		public void execute(CommandSource source, int value) {
		}

	}

}
