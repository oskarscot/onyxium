package dev.onyxium.proxy.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import module java.base;
import org.junit.Test;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.lifecycle.LifecycleException;

public class PluginManagerTest {

	ProxyServer proxy = mock(ProxyServer.class);

	PluginManager manager = new PluginManager();

	List<String> callbacks = new ArrayList<>();

	@Test
	public void loadsBeforeEnablingAndShutsDownInReverseDependencyOrder() {
		var addon = register("addon", "bridge", "core");
		var bridge = register("bridge", "core");
		var core = register("core");
		assertThat(manager.isLoaded("core")).isFalse();

		manager.start();

		assertThat(callbacks).containsExactly("core.load", "bridge.load", "addon.load", "core.enable", "bridge.enable", "addon.enable");
		assertThat(manager.plugins()).containsExactly(core, bridge, addon);
		assertThat(manager.plugin("addon")).contains(addon);
		assertThat(manager.manifest("addon")).contains(PluginProbe.manifest("addon", "bridge", "core"));
		assertThat(manager.isLoaded("addon")).isTrue();
		assertThat(manager.isEnabled("addon")).isTrue();
		var snapshot = manager.plugins();

		manager.shutdown();
		manager.shutdown();

		assertThat(callbacks).containsExactly("core.load", "bridge.load", "addon.load", "core.enable", "bridge.enable", "addon.enable", "addon.disable", "bridge.disable", "core.disable");
		assertThat(manager.plugins()).isEmpty();
		assertThat(manager.plugin("addon")).isEmpty();
		assertThat(manager.manifest("addon")).isEmpty();
		assertThat(manager.isLoaded("addon")).isFalse();
		assertThat(manager.isEnabled("addon")).isFalse();
		assertThat(snapshot).containsExactly(core, bridge, addon);
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

	@Test
	public void rejectsDuplicateRegistrationsWithoutReplacingTheOriginal() {
		var original = register("core");
		var replacement = new PluginProbe(proxy, "replacement", callbacks);

		assertThatThrownBy(() -> manager.register(PluginProbe.manifest("core"), replacement))
			.isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate plugin ID 'core'");
		assertThatThrownBy(() -> manager.register(PluginProbe.manifest("other"), original))
			.isInstanceOf(IllegalArgumentException.class).hasMessage("Plugin instance is already registered");
		manager.start();

		assertThat(manager.plugins()).containsExactly(original);
		assertThatThrownBy(() -> manager.register(PluginProbe.manifest("late"), replacement))
			.isInstanceOf(IllegalStateException.class).hasMessage("Plugin manager is RUNNING");
	}

	@Test
	public void shutdownContinuesAfterADisableFailureAndDoesNotRetryCallbacks() {
		var addon = register("addon", "core");
		register("core");
		manager.start();
		callbacks.clear();
		addon.failures = Set.of(PluginProbe.Callback.DISABLE);

		assertThatThrownBy(manager::shutdown).isInstanceOf(LifecycleException.class)
			.hasMessage("One or more plugins failed to disable")
			.satisfies(failure -> assertThat(failure.getSuppressed()).singleElement()
				.satisfies(suppressed -> assertThat(suppressed).hasMessage("Could not disable plugin 'addon'")
					.hasRootCauseMessage("addon.disable")));
		manager.shutdown();

		assertThat(callbacks).containsExactly("addon.disable", "core.disable");
		assertThat(manager.plugins()).isEmpty();
		assertThat(manager.isEnabled("addon")).isFalse();
	}

	PluginProbe register(String id, String... dependencies) {
		var plugin = new PluginProbe(proxy, id, callbacks);
		manager.register(PluginProbe.manifest(id, dependencies), plugin);
		return plugin;
	}

}
