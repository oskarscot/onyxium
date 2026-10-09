package dev.onyxium.proxy.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import module java.base;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.lifecycle.LifecycleException;

@RunWith(Parameterized.class)
public class PluginDependencyValidationTest {

	@Rule
	public TemporaryFolder pluginsDirectory = new TemporaryFolder();

	@Parameterized.Parameters(name = "{0}")
	public static List<Object[]> dependencies() {
		return List.of(
			new Object[] { "missing", "Plugin 'addon' requires missing plugin 'missing'" },
			new Object[] { "core", "Plugin dependency cycle: [core, addon] -> core" }
		);
	}

	@Parameterized.Parameter
	public String dependency;

	@Parameterized.Parameter(1)
	public String message;

	@Test
	public void invalidDependencyGraphFailsBeforeAnyCallback() {
		var proxy = mock(ProxyServer.class);
		var manager = new PluginManager(proxy, pluginsDirectory.getRoot().toPath());
		var callbacks = new ArrayList<String>();
		manager.register(PluginProbe.manifest("core", "addon"), new PluginProbe(proxy, "core", callbacks));
		manager.register(PluginProbe.manifest("addon", dependency), new PluginProbe(proxy, "addon", callbacks));

		assertThatThrownBy(manager::start).isInstanceOf(LifecycleException.class).hasMessage("Plugin startup failed: " + message);

		assertThat(callbacks).isEmpty();
		assertThat(manager.plugins()).isEmpty();
	}

}
