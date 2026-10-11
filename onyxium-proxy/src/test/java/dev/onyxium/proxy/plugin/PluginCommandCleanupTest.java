package dev.onyxium.proxy.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import module java.base;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.command.Command;
import dev.onyxium.proxy.api.command.CommandDefinition;
import dev.onyxium.proxy.api.command.CommandDispatcher;
import dev.onyxium.proxy.api.command.CommandSource;
import dev.onyxium.proxy.api.plugin.Plugin;
import dev.onyxium.proxy.lifecycle.LifecycleException;
import dev.onyxium.proxy.permission.ProxyPermissionService;

public class PluginCommandCleanupTest {

	@Rule
	public TemporaryFolder directory = new TemporaryFolder();

	final ProxyServer proxy = mock(ProxyServer.class);

	final CommandDispatcher commands = new CommandDispatcher();

	final ProxyPermissionService permissions = new ProxyPermissionService();

	PluginManager manager;

	@Before
	public void createManager() {
		manager = new PluginManager(proxy, directory.getRoot().toPath());
		when(proxy.pluginService()).thenReturn(manager);
		when(proxy.commandDispatcher()).thenReturn(commands);
		when(proxy.permissionService()).thenReturn(permissions);
		commands.registerHandler(new CoreCommands());
	}

	@Test
	public void disableRemovesOnlyThatPluginsCommandsAndReenableRestoresThem() {
		register("first", new FirstCommands());
		register("second", new SecondCommands());
		manager.start();
		assertThat(commands.dispatch(mock(CommandSource.class), "f")).isTrue();

		manager.disable("first");

		assertThat(commands.commands()).extracting(CommandDefinition::name).containsExactly("core", "second");
		assertThat(commands.dispatch(mock(CommandSource.class), "f")).isFalse();
		assertThat(manager.isEnabled("second")).isTrue();
		manager.enable("first");
		assertThat(commands.dispatch(mock(CommandSource.class), "f")).isTrue();

		manager.shutdown();
		assertThat(commands.commands()).extracting(CommandDefinition::name).containsExactly("core");
	}

	@Test
	public void failedDisableStillRemovesCommandsAndPermissionProvider() {
		var plugin = register("first", new FirstCommands());
		plugin.providePermissions = true;
		plugin.failures = Set.of(Failure.DISABLE);
		register("second", new SecondCommands());
		manager.start();

		assertThatThrownBy(() -> manager.disable("first")).isInstanceOf(LifecycleException.class);

		assertThat(commands.commands()).extracting(CommandDefinition::name).containsExactly("core", "second");
		assertThat(permissions.registration()).isNull();
		assertThat(manager.isEnabled("first")).isFalse();
		manager.shutdown();
	}

	@Test
	public void failedLoadRemovesCommandsRegisteredDuringLoad() {
		assertStartupCleanup(Failure.LOAD);
	}

	@Test
	public void failedEnableRemovesCommandsFromPreviouslyEnabledPluginsToo() {
		assertStartupCleanup(Failure.ENABLE);
	}

	@Test
	public void failedReenableCanBeRetriedWithoutStaleCommands() {
		var plugin = register("first", new FirstCommands());
		manager.start();
		manager.disable("first");
		plugin.failures = Set.of(Failure.ENABLE, Failure.DISABLE);

		assertThatThrownBy(() -> manager.enable("first")).isInstanceOf(LifecycleException.class);
		assertThat(commands.commands()).extracting(CommandDefinition::name).containsExactly("core");

		plugin.failures = Set.of();
		manager.enable("first");
		assertThat(commands.dispatch(mock(CommandSource.class), "f")).isTrue();
		manager.shutdown();
	}

	void assertStartupCleanup(Failure failure) {
		register("first", new FirstCommands());
		var plugin = register("second", new SecondCommands());
		plugin.registerDuringLoad = failure == Failure.LOAD;
		plugin.failures = Set.of(failure, Failure.DISABLE);

		assertThatThrownBy(manager::start).isInstanceOf(LifecycleException.class);

		assertThat(commands.commands()).extracting(CommandDefinition::name).containsExactly("core");
		assertThat(manager.plugins()).isEmpty();
	}

	TestPlugin register(String id, Object handler) {
		var plugin = new TestPlugin(proxy, handler);
		manager.register(PluginProbe.manifest(id), plugin);
		return plugin;
	}

	enum Failure {
		LOAD, ENABLE, DISABLE
	}

	static final class TestPlugin extends Plugin {

		final Object handler;

		Set<Failure> failures = Set.of();

		boolean registerDuringLoad;

		boolean providePermissions;

		TestPlugin(ProxyServer proxy, Object handler) {
			super(proxy);
			this.handler = handler;
		}

		@Override
		public void load() {
			if (registerDuringLoad) {
				proxyServer().commandDispatcher().registerHandler(this, handler);
			}
			fail(Failure.LOAD);
		}

		@Override
		public void enable() {
			proxyServer().commandDispatcher().registerHandler(this, handler);
			if (providePermissions) {
				proxyServer().permissionService().register(this, (player, permission) -> true);
			}
			fail(Failure.ENABLE);
		}

		@Override
		public void disable() {
			fail(Failure.DISABLE);
		}

		void fail(Failure failure) {
			if (failures.contains(failure)) {
				throw new IllegalStateException(failure.name());
			}
		}
	}

	public static class FirstCommands {

		@Command(name = "first", aliases = "f")
		public void execute(CommandSource source) {
		}
	}

	public static class SecondCommands {

		@Command(name = "second")
		public void execute(CommandSource source) {
		}
	}

	public static class CoreCommands {

		@Command(name = "core")
		public void execute(CommandSource source) {
		}
	}
}
