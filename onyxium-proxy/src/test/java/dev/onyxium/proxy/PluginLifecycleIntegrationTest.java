package dev.onyxium.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import module java.base;
import org.junit.Test;

import dev.onyxium.proxy.api.plugin.Plugin;
import dev.onyxium.proxy.api.plugin.PluginManifest;
import dev.onyxium.proxy.io.NettyNetworkManager;
import dev.onyxium.proxy.lifecycle.LifecycleException;

public class PluginLifecycleIntegrationTest {

	NettyNetworkManager network = mock(NettyNetworkManager.class);

	Plugin plugin = mock(Plugin.class);

	OnyxiumProxy proxy = new OnyxiumProxy(network);

	@Test
	public void proxyOwnsPluginLifecycleAroundTheNetworkListener() {
		proxy.pluginManager.register(manifest(), plugin);

		proxy.start();
		proxy.shutdown();

		var order = inOrder(network, plugin);
		order.verify(plugin).load();
		order.verify(plugin).enable();
		order.verify(network).start(proxy);
		order.verify(network).stop();
		order.verify(plugin).disable();
		order.verifyNoMoreInteractions();
		assertThat(proxy.pluginService()).isSameAs(proxy.pluginManager);
		assertThat(proxy.pluginService().plugins()).isEmpty();
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
		return new PluginManifest("sample", "1.0", "example.SamplePlugin", List.of());
	}

}
