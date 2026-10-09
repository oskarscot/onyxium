package dev.onyxium.proxy.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import module java.base;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.lifecycle.LifecycleException;

@RunWith(Parameterized.class)
public class PluginDependencyValidationTest {

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
		var manager = new PluginManager();
		var proxy = mock(ProxyServer.class);
		var callbacks = new ArrayList<String>();
		manager.register(PluginProbe.manifest("core", "addon"), new PluginProbe(proxy, "core", callbacks));
		manager.register(PluginProbe.manifest("addon", dependency), new PluginProbe(proxy, "addon", callbacks));

		assertThatThrownBy(manager::start).isInstanceOf(LifecycleException.class).hasMessage(message);

		assertThat(callbacks).isEmpty();
		assertThat(manager.plugins()).isEmpty();
	}

}
