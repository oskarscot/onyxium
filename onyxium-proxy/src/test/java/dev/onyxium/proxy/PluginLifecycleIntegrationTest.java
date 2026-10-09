package dev.onyxium.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import module java.base;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.onyxium.proxy.api.plugin.Plugin;
import dev.onyxium.proxy.api.plugin.PluginManifest;
import dev.onyxium.proxy.io.NettyNetworkManager;
import dev.onyxium.proxy.lifecycle.LifecycleException;
import dev.onyxium.proxy.plugin.PluginManager;

public class PluginLifecycleIntegrationTest {

	@Rule
	public TemporaryFolder pluginsDirectory = new TemporaryFolder();

	NettyNetworkManager network = mock(NettyNetworkManager.class);

	Plugin plugin = mock(Plugin.class);

	OnyxiumProxy proxy = new OnyxiumProxy(network);

	@Before
	public void useTemporaryPluginsDirectory() {
		proxy.pluginManager = new PluginManager(proxy, pluginsDirectory.getRoot().toPath());
	}

	@Test
	public void failedNetworkStartupCleansUpPlugins() {
		proxy.pluginManager.register(manifest(), plugin);
		var failure = new LifecycleException("Listener failed");
		doThrow(failure).when(network).start(proxy);

		assertThatThrownBy(proxy::start).isSameAs(failure);

		var order = inOrder(network, plugin);
		order.verify(plugin).load();
		order.verify(plugin).enable();
		order.verify(network).start(proxy);
		order.verify(plugin).disable();
		order.verifyNoMoreInteractions();
		assertThat(proxy.pluginService().plugins()).isEmpty();
	}

	PluginManifest manifest() {
		return new PluginManifest("sample", "1.0", "example.SamplePlugin", List.of("Oskar"));
	}

}
