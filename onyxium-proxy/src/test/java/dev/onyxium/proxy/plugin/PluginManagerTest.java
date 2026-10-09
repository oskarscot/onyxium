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
import dev.onyxium.proxy.lifecycle.LifecycleException;

public class PluginManagerTest {

	ProxyServer proxy = mock(ProxyServer.class);

	@Rule
	public TemporaryFolder pluginsDirectory = new TemporaryFolder();

	PluginManager manager;

	List<String> callbacks = new ArrayList<>();

	@Before
	public void createManager() {
		manager = new PluginManager(proxy, pluginsDirectory.getRoot().toPath());
		when(proxy.pluginService()).thenReturn(manager);
	}

	@Test
	public void directoryCreationFailureAbortsBeforeThePluginCallback() throws IOException {
		register("core");
		var directory = pluginsDirectory.getRoot().toPath().resolve("core");
		Files.writeString(directory, "existing file");

		assertThatThrownBy(manager::start).isInstanceOf(LifecycleException.class)
			.hasMessage("Plugin startup failed: Could not create data directory for plugin 'core'")
			.hasRootCauseInstanceOf(FileAlreadyExistsException.class);

		assertThat(callbacks).isEmpty();
		assertThat(manager.plugins()).isEmpty();
		assertThat(Files.readString(directory)).isEqualTo("existing file");
	}

	@Test
	public void preventsRuntimeTransitionsFromBreakingDependencies() {
		register("addon", "core");
		register("core");
		manager.start();
		callbacks.clear();

		assertThatThrownBy(() -> manager.disable("core")).isInstanceOf(IllegalStateException.class)
			.hasMessage("Plugin 'core' is required by enabled plugins [addon]");
		manager.disable("addon");
		manager.disable("addon");
		manager.disable("core");
		assertThat(manager.isLoaded("addon")).isTrue();
		assertThat(manager.isEnabled("addon")).isFalse();
		assertThatThrownBy(() -> manager.enable("addon")).isInstanceOf(IllegalStateException.class)
			.hasMessage("Plugin 'addon' requires enabled plugins [core]");
		manager.enable("core");
		manager.enable("addon");
		manager.enable("addon");

		assertThat(callbacks).containsExactly("addon.disable", "core.disable", "core.enable", "addon.enable");
		assertThat(manager.isEnabled("core")).isTrue();
		assertThat(manager.isEnabled("addon")).isTrue();
	}

	@Test
	public void failedReenableCleansUpAndAllowsAnotherAttempt() {
		var plugin = register("core");
		manager.start();
		manager.disable("core");
		callbacks.clear();
		plugin.failures = Set.of(PluginProbe.Callback.ENABLE);

		assertThatThrownBy(() -> manager.enable("core")).isInstanceOf(LifecycleException.class)
			.hasRootCauseMessage("core.enable");
		assertThat(manager.isLoaded("core")).isTrue();
		assertThat(manager.isEnabled("core")).isFalse();
		plugin.failures = Set.of();
		manager.enable("core");

		assertThat(callbacks).containsExactly("core.enable", "core.disable", "core.enable");
		assertThat(manager.isEnabled("core")).isTrue();
	}

	PluginProbe register(String id, String... dependencies) {
		var plugin = new PluginProbe(proxy, id, callbacks);
		manager.register(PluginProbe.manifest(id, dependencies), plugin);
		return plugin;
	}

}
