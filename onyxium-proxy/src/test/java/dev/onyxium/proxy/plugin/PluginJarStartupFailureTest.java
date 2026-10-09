package dev.onyxium.proxy.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import module java.base;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.command.ConsoleSource;
import dev.onyxium.proxy.lifecycle.LifecycleException;

@RunWith(Parameterized.class)
public class PluginJarStartupFailureTest {

	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Parameterized.Parameters(name = "{0}")
	public static List<Object[]> failures() {
		return List.of(
			new Object[] { Failure.CONSTRUCTOR, List.of("core.load", "core.disable") },
			new Object[] { Failure.LOAD, List.of("core.load", "broken.load", "broken.disable", "core.disable") },
			new Object[] { Failure.ENABLE, List.of("core.load", "broken.load", "core.enable", "broken.enable", "broken.disable", "core.disable") }
		);
	}

	@Parameterized.Parameter
	public Failure failure;

	@Parameterized.Parameter(1)
	public List<String> expected;

	@Test
	public void startupFailureClosesEveryCreatedLoaderAndRestoresTheCallerContext() throws IOException {
		var proxy = mock(ProxyServer.class);
		var console = mock(ConsoleSource.class);
		var directory = temporary.getRoot().toPath().resolve("plugins");
		var manager = new PluginManager(proxy, directory);
		when(proxy.pluginService()).thenReturn(manager);
		when(proxy.console()).thenReturn(console);
		PluginJars.create(directory.resolve("core.jar"), Map.of("fixture.CorePlugin", PluginJars.simplePlugin("CorePlugin", "core")),
			Map.of("onyxium.json", PluginJars.utf8(PluginJars.manifest("core", "fixture.CorePlugin")), "marker.txt", PluginJars.utf8("core")));
		PluginJars.create(directory.resolve("broken.jar"), Map.of("fixture.BrokenPlugin", """
			package fixture;
			import dev.onyxium.proxy.api.ProxyServer;
			import dev.onyxium.proxy.api.plugin.Plugin;
			public final class BrokenPlugin extends Plugin {
				public BrokenPlugin(ProxyServer proxy) {
					super(proxy);
					checkContext();
					if ("%s".equals("CONSTRUCTOR")) throw new IllegalStateException("CONSTRUCTOR");
				}
				public void load() {
					checkContext();
					proxyServer().console().sendMessage("broken.load");
					if ("%s".equals("LOAD")) throw new IllegalStateException("LOAD");
				}
				public void enable() {
					checkContext();
					proxyServer().console().sendMessage("broken.enable");
					if ("%s".equals("ENABLE")) throw new IllegalStateException("ENABLE");
				}
				public void disable() {
					checkContext();
					proxyServer().console().sendMessage("broken.disable");
					throw new IllegalStateException("cleanup failed");
				}
				void checkContext() {
					if (Thread.currentThread().getContextClassLoader() != getClass().getClassLoader())
						throw new IllegalStateException("Wrong context loader");
				}
			}
			""".formatted(failure, failure, failure)),
			Map.of("onyxium.json", PluginJars.utf8(PluginJars.manifest("broken", "fixture.BrokenPlugin", "core")), "marker.txt", PluginJars.utf8("broken")));
		var context = Thread.currentThread().getContextClassLoader();

		assertThatThrownBy(manager::start).isInstanceOf(LifecycleException.class).hasRootCauseMessage(failure.name())
			.satisfies(startup -> {
				if (failure != Failure.CONSTRUCTOR) {
					assertThat(startup.getSuppressed()).singleElement()
						.satisfies(cleanup -> assertThat(cleanup.getSuppressed()).singleElement()
							.satisfies(suppressed -> assertThat(suppressed).hasRootCauseMessage("cleanup failed")));
				}
			});
		manager.shutdown();

		var messages = ArgumentCaptor.forClass(String.class);
		verify(console, atLeastOnce()).sendMessage(messages.capture());
		assertThat(messages.getAllValues()).containsExactlyElementsOf(expected);
		assertThat(manager.plugins()).isEmpty();
		assertThat(manager.registrations.get("core").classLoader.findResource("marker.txt")).isNull();
		assertThat(manager.registrations.get("broken").classLoader.findResource("marker.txt")).isNull();
		assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(context);
	}

	public enum Failure {
		CONSTRUCTOR, LOAD, ENABLE
	}

}
