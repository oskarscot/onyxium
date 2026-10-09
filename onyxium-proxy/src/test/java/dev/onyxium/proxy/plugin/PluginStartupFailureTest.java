package dev.onyxium.proxy.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import module java.base;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.lifecycle.LifecycleException;

@RunWith(Parameterized.class)
public class PluginStartupFailureTest {

	@Rule
	public TemporaryFolder pluginsDirectory = new TemporaryFolder();

	@Parameterized.Parameters(name = "{0}")
	public static List<Object[]> failures() {
		return List.of(
			new Object[] { PluginProbe.Callback.LOAD, List.of("core.load", "broken.load", "broken.disable", "core.disable") },
			new Object[] { PluginProbe.Callback.ENABLE, List.of("core.load", "broken.load", "last.load", "core.enable", "broken.enable", "last.disable", "broken.disable", "core.disable") }
		);
	}

	@Parameterized.Parameter
	public PluginProbe.Callback failure;

	@Parameterized.Parameter(1)
	public List<String> expected;

	@Test
	public void startupFailureCleansUpEveryAttemptedPluginAndPreservesBothFailures() {
		var proxy = mock(ProxyServer.class);
		var manager = new PluginManager(pluginsDirectory.getRoot().toPath());
		when(proxy.pluginService()).thenReturn(manager);
		var callbacks = new ArrayList<String>();
		var broken = new PluginProbe(proxy, "broken", callbacks);
		broken.failures = Set.of(failure, PluginProbe.Callback.DISABLE);
		manager.register(PluginProbe.manifest("last", "broken"), new PluginProbe(proxy, "last", callbacks));
		manager.register(PluginProbe.manifest("broken", "core"), broken);
		manager.register(PluginProbe.manifest("core"), new PluginProbe(proxy, "core", callbacks));

		assertThatThrownBy(manager::start).isInstanceOf(LifecycleException.class)
			.hasMessage("Plugin startup failed: Could not " + failure.name().toLowerCase(Locale.ROOT) + " plugin 'broken'")
			.hasRootCauseMessage("broken." + failure.name().toLowerCase(Locale.ROOT))
			.satisfies(startup -> assertThat(startup.getSuppressed()).singleElement()
				.satisfies(cleanup -> assertThat(cleanup.getSuppressed()).singleElement()
					.satisfies(suppressed -> assertThat(suppressed).hasRootCauseMessage("broken.disable"))));
		manager.shutdown();

		assertThat(callbacks).containsExactlyElementsOf(expected);
		assertThat(manager.plugins()).isEmpty();
		assertThat(manager.isLoaded("core")).isFalse();
		assertThat(manager.isEnabled("core")).isFalse();
	}

}
