package dev.onyxium.proxy.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import module java.base;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.command.ConsoleSource;

public class PluginJarLoadingTest {

	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	ProxyServer proxy = mock(ProxyServer.class);

	ConsoleSource console = mock(ConsoleSource.class);

	Path directory;

	PluginManager manager;

	@Before
	public void createManager() {
		directory = temporary.getRoot().toPath().resolve("plugins");
		manager = new PluginManager(proxy, directory);
		when(proxy.pluginService()).thenReturn(manager);
		when(proxy.console()).thenReturn(console);
	}

	@Test
	public void startsJarPluginsWithSharedApiTypesAndIsolatedLibrariesAndResources() throws Exception {
		var core = PluginJars.create(directory.resolve("z-core.jar"), Map.of(
			"fixture.CorePlugin", PluginJars.simplePlugin("CorePlugin", "core"),
			"fixture.Greeting", """
				package fixture;
				public final class Greeting {
					public static String text() { return "from core"; }
				}
				""",
			"fixture.Library", library("core library")),
			Map.of("onyxium.json", PluginJars.utf8(PluginJars.manifest("core", "fixture.CorePlugin")),
				"marker.txt", PluginJars.utf8("core resource")));
		byte[] api;
		try (var input = ProxyServer.class.getResourceAsStream("ProxyServer.class")) {
			api = input.readAllBytes();
		}
		PluginJars.create(directory.resolve("a-addon.JAR"), Map.of(
			"fixture.AddonPlugin", """
				package fixture;
				import module java.base;
				import dev.onyxium.proxy.api.ProxyServer;
				import dev.onyxium.proxy.api.plugin.Plugin;
				public final class AddonPlugin extends Plugin {
					public AddonPlugin(ProxyServer proxy) {
						super(proxy);
						checkContext();
					}
					public void load() {
						checkContext();
						if (!Files.isDirectory(dataDirectory())) throw new IllegalStateException("Missing data directory");
						try (var input = getClass().getClassLoader().getResourceAsStream("marker.txt")) {
							proxyServer().console().sendMessage("addon.load:" + Greeting.text() + ":" + Library.text()
								+ ":" + new String(input.readAllBytes(), StandardCharsets.UTF_8));
						} catch (IOException failure) { throw new UncheckedIOException(failure); }
					}
					public void enable() { checkContext(); proxyServer().console().sendMessage("addon.enable"); }
					public void disable() { checkContext(); proxyServer().console().sendMessage("addon.disable:" + Greeting.text()); }
					void checkContext() {
						if (Thread.currentThread().getContextClassLoader() != getClass().getClassLoader())
							throw new IllegalStateException("Wrong context loader");
					}
				}
				""",
			"fixture.Library", library("addon library")), Map.of(
				"onyxium.json", PluginJars.utf8(PluginJars.manifest("addon", "fixture.AddonPlugin", "core")),
				"marker.txt", PluginJars.utf8("addon resource"),
				"dev/onyxium/proxy/api/ProxyServer.class", api), core);
		PluginJars.create(directory.resolve("unrelated.jar"), Map.of(
			"fixture.UnrelatedPlugin", PluginJars.simplePlugin("UnrelatedPlugin", "unrelated"),
			"fixture.Secret", "package fixture; public final class Secret {}"),
			Map.of("onyxium.json", PluginJars.utf8(PluginJars.manifest("unrelated", "fixture.UnrelatedPlugin")),
				"marker.txt", PluginJars.utf8("unrelated resource")));
		Files.writeString(directory.resolve("notes.txt"), "ignored");
		var context = Thread.currentThread().getContextClassLoader();

		manager.start();

		var provider = manager.plugin("core").orElseThrow();
		var addon = manager.plugin("addon").orElseThrow();
		var providerLoader = (PluginClassLoader) provider.getClass().getClassLoader();
		var addonLoader = (PluginClassLoader) addon.getClass().getClassLoader();
		assertThat(manager.plugins().stream().map(plugin -> plugin.getClass().getName()).toList())
			.containsExactly("fixture.CorePlugin", "fixture.AddonPlugin", "fixture.UnrelatedPlugin");
		assertThat(addon.dataDirectory()).isEqualTo(directory.resolve("addon"));
		assertThat(addonLoader.loadClass(ProxyServer.class.getName())).isSameAs(ProxyServer.class);
		assertThat(addonLoader.loadClass("fixture.Greeting")).isSameAs(providerLoader.loadClass("fixture.Greeting"));
		assertThat(addonLoader.loadClass("fixture.Library")).isNotSameAs(providerLoader.loadClass("fixture.Library"));
		assertThatThrownBy(() -> addonLoader.loadClass("fixture.Secret")).isInstanceOf(ClassNotFoundException.class);
		assertThat(Collections.list(addonLoader.getResources("marker.txt")).stream().map(PluginJarLoadingTest::read).toList())
			.containsExactly("addon resource", "core resource");
		assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(context);

		var state = addon.dataDirectory().resolve("state.txt");
		Files.writeString(state, "persistent state");
		manager.shutdown();

		var messages = ArgumentCaptor.forClass(String.class);
		verify(console, atLeastOnce()).sendMessage(messages.capture());
		assertThat(messages.getAllValues()).containsExactly("core.load", "addon.load:from core:addon library:addon resource",
			"unrelated.load", "core.enable", "addon.enable", "unrelated.enable", "unrelated.disable",
			"addon.disable:from core", "core.disable");
		assertThat(providerLoader.findResource("marker.txt")).isNull();
		assertThat(addonLoader.findResource("marker.txt")).isNull();
		assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(context);
		assertThat(Files.readString(state)).isEqualTo("persistent state");
	}

	static String library(String value) {
		return """
			package fixture;
			public final class Library {
				public static String text() { return "%s"; }
			}
			""".formatted(value);
	}

	static String read(URL resource) {
		try (var input = resource.openStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

}
