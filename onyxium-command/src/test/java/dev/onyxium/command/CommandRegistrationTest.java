package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import module java.base;
import org.junit.Test;

import dev.onyxium.command.CommandFixtures.ConsoleSource;
import dev.onyxium.command.CommandFixtures.PlayerSource;
import dev.onyxium.command.CommandFixtures.Source;

public class CommandRegistrationTest {

	@Test
	public void reflectsParameterNamesAndOptionalGreedyUsage() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);
		var registration = dispatcher.registerHandler(new Commands());
		var kick = dispatcher.commands()
			.stream()
			.filter(command -> command.name().equals("foo bar"))
			.findFirst()
			.orElseThrow();

		assertThat(kick.aliases()).containsExactly("barfoo");
		assertThat(kick.permission()).isEqualTo("onyxium.kick");
		assertThat(kick.arguments()).containsExactly(new ArgumentDefinition("player", String.class, false, false),
				new ArgumentDefinition("reason", String.class, true, true));

		assertThat(kick.usage()).isEqualTo("/foo bar <player> [reason...]");
		assertThat(registration.commands()).containsExactlyElementsOf(dispatcher.commands());
	}

	@Test
	public void filtersConsoleOnlyAndPlayerOnlyMetadata() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);
		dispatcher.registerHandler(new Commands());
		var console = new ConsoleSource(new ArrayList<>(), Set.of("onyxium.kick"));
		var player = new PlayerSource(new ArrayList<>(), Set.of());

		assertThat(dispatcher.commands(console)).extracting(CommandDefinition::name).containsExactly("foo bar", "stop");
		assertThat(dispatcher.commands(player)).extracting(CommandDefinition::name).containsExactly("where");
	}

	@Test
	public void conflictingAliasLeavesPreviousRegistrationIntact() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);
		dispatcher.registerHandler(new Commands());
		var original = dispatcher.commands();

		assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(new Conflict()));
		assertThat(dispatcher.commands()).containsExactlyElementsOf(original);
	}

	@Test
	public void invalidHandlerPublishesNoneOfItsValidMethods() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);

		assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(new PartiallyInvalid()));
		assertThat(dispatcher.commands()).isEmpty();
	}

	@Test
	public void closingRegistrationRemovesOnlyItsOwnedCommands() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);
		var registration = dispatcher.registerHandler(new Commands());
		dispatcher.registerHandler(new Other());
		registration.close();
		registration.close();

		assertThat(dispatcher.commands()).extracting(CommandDefinition::name).containsExactly("other");
	}

	@Test
	public void parentAliasesShareDescendantsAndLeavePreviousSnapshotsIntact() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);
		dispatcher.registerHandler(new Commands());
		var before = dispatcher.tree;
		var parent = dispatcher.registerHandler(new Parent());
		var withAlias = dispatcher.tree;
		var canonical = withAlias.roots().get("foo");

		assertThat(withAlias.roots().get("f")).isSameAs(canonical);
		assertThat(canonical.children().keySet()).containsExactlyInAnyOrder("bar", "barfoo");
		assertThat(canonical.children().get("barfoo")).isSameAs(canonical.children().get("bar"));
		assertThat(before.roots().keySet()).containsExactlyInAnyOrder("foo", "stop", "where");
		parent.close();

		assertThat(dispatcher.tree.roots().keySet()).containsExactlyInAnyOrder("foo", "stop", "where");
		assertThat(withAlias.roots().keySet()).containsExactlyInAnyOrder("foo", "f", "stop", "where");
	}

	@Test
	public void aliasesCannotShadowImplicitParentPaths() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);

		assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(new ParentConflict()));
		assertThat(dispatcher.commands()).isEmpty();
	}

	@Test
	public void compiledHandleBindsTheHandlerAndUnboxesArguments() throws Throwable {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);
		var handler = new Counter();
		dispatcher.registerHandler(handler);
		var console = new ConsoleSource(new ArrayList<>(), Set.of());
		var invocation = dispatcher.tree.commands().getFirst().invocation();
		invocation.invokeExact(new Object[] { console, 3, Optional.of("hello") });

		assertThat(handler.total).isEqualTo(3);
		assertThat(console.calls()).containsExactly("hello");
	}

	public static class Counter {

		int total;

		@Command(name = "count")
		public void count(ConsoleSource source, int amount, Optional<String> note) {
			total += amount;
			source.calls().add(note.orElse(""));
		}

	}

	public static class Parent {

		@Command(name = "foo", aliases = { "f" })
		public void execute(Source source) {
		}

	}

	public static class ParentConflict {

		@Command(name = "foo nested")
		public void nested(Source source) {
		}

		@Command(name = "other", aliases = { "foo" })
		public void other(Source source) {
		}

	}

	public static class Commands {

		@Command(name = "foo bar", permission = "onyxium.kick", aliases = { "barfoo" })
		public void kick(Source source, String player, @Greedy Optional<String> reason) {
		}

		@Command(name = "stop")
		public void stop(ConsoleSource source) {
		}

		@Command(name = "where")
		public void where(PlayerSource source) {
		}

	}

	public static class Conflict {

		@Command(name = "foo different", aliases = { "barfoo" })
		public void execute(Source source) {
		}

	}

	public static class Other {

		@Command(name = "other")
		public void execute(Source source) {
		}

	}

	public static class PartiallyInvalid {

		@Command(name = "valid")
		public void aaaValid(Source source) {
		}

		@Command(name = "invalid")
		public void zzzInvalid(Source source, @Greedy int number) {
		}

	}

}
