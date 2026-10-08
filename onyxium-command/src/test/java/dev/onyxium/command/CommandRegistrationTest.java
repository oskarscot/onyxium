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
		dispatcher.registerHandler(new Commands());
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
	}

	@Test
	public void filtersConsoleOnlyAndPlayerOnlyMetadata() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);
		dispatcher.registerHandler(new Commands());
		var console = new ConsoleSource(Set.of("onyxium.kick"));
		var player = new PlayerSource(Set.of());

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
	public void resolvesNestedCommandsThroughParentAndLeafAliases() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);
		dispatcher.registerHandler(new Commands());
		dispatcher.registerHandler(new Parent());

		var command = dispatcher.tree.roots().get("f").children().get("barfoo").command();

		assertThat(command.definition().name()).isEqualTo("foo bar");
	}

	@Test
	public void aliasesCannotShadowImplicitParentPaths() {
		var dispatcher = new CommandDispatcher<>(Source.class, Source::hasPermission);

		assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(new ParentConflict()));
		assertThat(dispatcher.commands()).isEmpty();
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
