package dev.onyxium.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import module java.base;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;

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

	CountDownLatch disableEntered = new CountDownLatch(1);

	CountDownLatch releaseDisable = new CountDownLatch(1);

	CountDownLatch shutdownRequested = new CountDownLatch(1);

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

	@Test
	public void concurrentShutdownWaitsForPluginCleanupAndRunsItOnce() throws InterruptedException, ExecutionException, TimeoutException {
		proxy.pluginManager.register(manifest(), plugin);
		doAnswer(this::pauseDisable).when(plugin).disable();
		proxy.start();

		try (var callers = Executors.newFixedThreadPool(2)) {
			var console = callers.submit(proxy::shutdown);
			try {
				assertThat(disableEntered.await(5, TimeUnit.SECONDS)).isTrue();
				var hook = callers.submit(this::requestShutdown);
				assertThat(shutdownRequested.await(5, TimeUnit.SECONDS)).isTrue();
				assertThatThrownBy(() -> hook.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
				releaseDisable.countDown();
				console.get(5, TimeUnit.SECONDS);
				hook.get(5, TimeUnit.SECONDS);
			}
			finally {
				releaseDisable.countDown();
			}
		}
		proxy.shutdown();

		var order = inOrder(network, plugin);
		order.verify(plugin).load();
		order.verify(plugin).enable();
		order.verify(network).start(proxy);
		order.verify(network).stop();
		order.verify(plugin).disable();
		order.verifyNoMoreInteractions();
		assertThat(proxy.pluginService().plugins()).isEmpty();
	}

	Object pauseDisable(InvocationOnMock invocation) throws InterruptedException {
		disableEntered.countDown();
		if (!releaseDisable.await(5, TimeUnit.SECONDS)) {
			throw new AssertionError("Plugin cleanup was not released");
		}
		return null;
	}

	void requestShutdown() {
		shutdownRequested.countDown();
		proxy.shutdown();
	}

	PluginManifest manifest() {
		return new PluginManifest("sample", "1.0", "example.SamplePlugin", List.of("Oskar"));
	}

}
