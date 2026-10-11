package dev.onyxium.proxy.api.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import module java.base;
import org.junit.Test;

import dev.onyxium.proxy.api.command.CommandFixtures.ConsoleSource;

public class CommandOwnershipTest {

	@Test
	public void removesAllHandlersForOneOwnerByIdentityAndPreservesSharedPaths() {
		var dispatcher = new CommandDispatcher();
		var owner = new Owner("plugin");
		var other = new Owner("plugin");
		var source = new ConsoleSource(Set.of());
		dispatcher.registerHandler(owner, new Parent());
		dispatcher.registerHandler(owner, new OwnedChild());
		dispatcher.registerHandler(other, new OtherChild());
		dispatcher.registerHandler(new Core());
		assertThat(dispatcher.dispatch(source, "n owned")).isTrue();
		assertThat(dispatcher.dispatch(source, "network o")).isTrue();

		dispatcher.unregister(owner);
		dispatcher.unregister(owner);

		assertThat(dispatcher.commands()).extracting(CommandDefinition::name)
			.containsExactly("network other", "core");
		assertThat(dispatcher.dispatch(source, "n other")).isFalse();
		source.messages().clear();
		assertThat(dispatcher.dispatch(source, "network other")).isTrue();
		assertThat(source.messages()).containsExactly("other");
	}

	@Test
	public void oldHandleCannotRemoveReplacementAfterOwnerCleanup() {
		var dispatcher = new CommandDispatcher();
		var owner = new Object();
		var handler = new Parent();
		var old = dispatcher.registerHandler(owner, handler);
		dispatcher.unregister(owner);
		dispatcher.registerHandler(owner, handler);

		old.unregister();
		old.unregister();

		assertThat(dispatcher.commands()).extracting(CommandDefinition::name).containsExactly("network");
		assertThat(dispatcher.dispatch(new ConsoleSource(Set.of()), "n")).isTrue();
	}

	@Test
	public void failedRegistrationDoesNotClaimAnotherOwnersCommands() {
		var dispatcher = new CommandDispatcher();
		var owner = new Object();
		var other = new Object();
		dispatcher.registerHandler(other, new Parent());

		assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(owner, new Parent()));
		dispatcher.unregister(owner);

		assertThat(dispatcher.commands()).extracting(CommandDefinition::name).containsExactly("network");
	}

	@Test
	public void removingHandlerSeparatelyKeepsOtherHandlersUnderTheSameOwner() {
		var dispatcher = new CommandDispatcher();
		var owner = new Object();
		var parent = dispatcher.registerHandler(owner, new Parent());
		dispatcher.registerHandler(owner, new OwnedChild());

		parent.unregister();

		assertThat(dispatcher.commands()).extracting(CommandDefinition::name).containsExactly("network owned");
		dispatcher.unregister(owner);
		assertThat(dispatcher.commands()).isEmpty();
	}

	@Test
	public void removalDuringDispatchStopsNewCallsAndLetsTheCurrentCallFinish() throws Exception {
		var dispatcher = new CommandDispatcher();
		var owner = new Object();
		var handler = new BlockingHandler();
		var source = new ConsoleSource(Set.of());
		dispatcher.registerHandler(owner, handler);

		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var dispatch = executor.submit(() -> dispatcher.dispatch(source, "blocking"));
			try {
				assertThat(handler.entered.await(5, TimeUnit.SECONDS)).isTrue();
				dispatcher.unregister(owner);
				assertThat(dispatcher.dispatch(new ConsoleSource(Set.of()), "blocking")).isFalse();
			}
			finally {
				handler.release.countDown();
			}

			assertThat(dispatch.get(5, TimeUnit.SECONDS)).isTrue();
			assertThat(source.messages()).containsExactly("finished");
		}
	}

	record Owner(String name) {
	}

	public static class Parent {

		@Command(name = "network", aliases = "n")
		public void execute(CommandSource source) {
			source.sendMessage("parent");
		}
	}

	public static class OwnedChild {

		@Command(name = "network owned", aliases = "o")
		public void execute(CommandSource source) {
			source.sendMessage("owned");
		}
	}

	public static class OtherChild {

		@Command(name = "network other")
		public void execute(CommandSource source) {
			source.sendMessage("other");
		}
	}

	public static class Core {

		@Command(name = "core")
		public void execute(CommandSource source) {
		}
	}

	public static class BlockingHandler {

		final CountDownLatch entered = new CountDownLatch(1);

		final CountDownLatch release = new CountDownLatch(1);

		@Command(name = "blocking")
		public void execute(CommandSource source) throws InterruptedException {
			entered.countDown();
			release.await();
			source.sendMessage("finished");
		}
	}
}
