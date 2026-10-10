package dev.onyxium.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import module java.base;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.onyxium.command.Command;
import dev.onyxium.command.CommandSource;
import dev.onyxium.proxy.api.command.ConsoleSource;
import dev.onyxium.proxy.io.NettyNetworkManager;
import dev.onyxium.proxy.lifecycle.LifecycleException;
import dev.onyxium.proxy.plugin.PluginManager;

public class PermissionsPluginIntegrationTest {

	@Rule
	public TemporaryFolder directory = new TemporaryFolder();

	private final OnyxiumProxy proxy = new OnyxiumProxy(mock(NettyNetworkManager.class));

	private final TestConsole console = new TestConsole();

	@Before
	public void installBuiltPluginJar() throws IOException {
		var jar = Path.of(System.getProperty("onyxium.permissions.jar"));
		Files.copy(jar, directory.getRoot().toPath().resolve("permissions.jar"));
		proxy.pluginManager = new PluginManager(proxy, directory.getRoot().toPath());

		try (var archive = new JarFile(jar.toFile())) {
			assertThat(archive.stream().map(ZipEntry::getName))
				.noneMatch(name -> name.startsWith("dev/onyxium/proxy/") || name.startsWith("dev/onyxium/command/"));
			assertThat(archive.getEntry("dev/onyxium/permissions/internal/yaml/Yaml.class")).isNotNull();
			assertThat(archive.getEntry("org/yaml/snakeyaml/Yaml.class")).isNull();
		}
	}

	@Test
	public void realPluginPersistsPermissionsAndReleasesItsApiRegistrations() {
		var player = UUID.randomUUID();
		proxy.start();

		try {
			var plugin = proxy.pluginService().plugin("permissions").orElseThrow();
			assertThat(plugin.getClass().getClassLoader()).isNotSameAs(getClass().getClassLoader());
			assertThat(proxy.pluginService().isEnabled("permissions")).isTrue();
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.kick")).isFalse();
			assertThat(plugin.dataDirectory().resolve("defaults.yml")).exists();
			assertThat(plugin.dataDirectory().resolve("groups.yml")).exists();
			assertThat(plugin.dataDirectory().resolve("players")).isDirectory();

			command("permissions group create helper");
			command("permissions group set helper onyxium.players true");
			command("permissions group create moderator");
			command("permissions group inherit moderator helper");
			command("permissions group set moderator onyxium.kick true");
			command("permissions user join " + player + " moderator");
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.players")).isTrue();
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.kick")).isTrue();

			command("permissions user set " + player + " onyxium.kick false");
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.kick")).isFalse();
			command("permissions user unset " + player + " onyxium.kick");
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.kick")).isTrue();

			proxy.pluginService().disable("permissions");
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.kick")).isFalse();
			assertThat(proxy.commandDispatcher().dispatch(console, "permissions")).isFalse();

			try (var alternative = proxy.permissionService().register((uuid, permission) -> permission.equals("other.node"))) {
				assertThat(proxy.permissionService().hasPermission(player, "other.node")).isTrue();
				assertThatThrownBy(() -> proxy.pluginService().enable("permissions"))
					.isInstanceOf(LifecycleException.class)
					.hasRootCauseInstanceOf(IllegalStateException.class);
				assertThat(proxy.permissionService().hasPermission(player, "other.node")).isTrue();
			}

			proxy.pluginService().enable("permissions");
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.players")).isTrue();
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.kick")).isTrue();
			command("permissions group delete helper");
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.players")).isFalse();
		}
		finally {
			proxy.shutdown();
		}

		assertThat(proxy.permissionService().hasPermission(player, "onyxium.kick")).isFalse();
		assertThat(proxy.commandDispatcher().dispatch(console, "permissions")).isFalse();
	}

	@Test
	public void reloadsPlayerFilesThroughThePluginCommand() throws IOException {
		proxy.start();

		try {
			var player = UUID.randomUUID();
			var plugin = proxy.pluginService().plugin("permissions").orElseThrow();
			var file = plugin.dataDirectory().resolve("players").resolve(player + ".yml");
			Files.writeString(file, "permissions: {'*': true}\n");
			proxy.commandDispatcher().dispatch(console, "permissions reload");
			assertThat(console.messages.getLast()).isEqualTo("Permissions reloaded.");
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.kick")).isTrue();

			Files.writeString(file, "permissions: [");
			proxy.commandDispatcher().dispatch(console, "permissions reload");
			assertThat(console.messages.getLast()).contains("Previous permissions are still active");
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.kick")).isTrue();

			Files.delete(file);
			proxy.commandDispatcher().dispatch(console, "permissions reload");
			assertThat(console.messages.getLast()).isEqualTo("Permissions reloaded.");
			assertThat(proxy.permissionService().hasPermission(player, "onyxium.kick")).isFalse();
		}
		finally {
			proxy.shutdown();
		}
	}

	@Test
	public void rejectsInvalidCommandsAndKeepsAdministrationConsoleOnly() {
		proxy.start();

		try {
			command("permissions group create staff");
			proxy.commandDispatcher().dispatch(console, "permissions group inherit staff staff");
			assertThat(console.messages.getLast()).contains("inheritance cycle");

			proxy.commandDispatcher().dispatch(console, "permissions group set staff bad.*.node true");
			assertThat(console.messages.getLast()).contains("Permission names");

			proxy.commandDispatcher().dispatch(console, "permissions user join not-a-uuid staff");
			assertThat(console.messages.getLast()).contains("Invalid value");

			var messages = new ArrayList<String>();
			var playerSource = new CommandSource() {
				@Override
				public boolean hasPermission(String permission) {
					return true;
				}

				@Override
				public void sendMessage(String message) {
					messages.add(message);
				}
			};

			proxy.commandDispatcher().dispatch(playerSource, "permissions default set * true");
			assertThat(messages).containsExactly("This command is not available to this source.");
			assertThat(proxy.permissionService().hasPermission(UUID.randomUUID(), "onyxium.kick")).isFalse();
		}
		finally {
			proxy.shutdown();
		}
	}

	@Test
	public void failedCommandRegistrationReleasesThePermissionProvider() {
		proxy.commandDispatcher().registerHandler(new ConflictingCommands());

		assertThatThrownBy(proxy::start).isInstanceOf(LifecycleException.class);
		assertThat(proxy.pluginService().plugins()).isEmpty();

		try (var alternative = proxy.permissionService().register((uuid, permission) -> true)) {
			assertThat(proxy.permissionService().hasPermission(UUID.randomUUID(), "test.permission")).isTrue();
		}

		console.messages.clear();
		proxy.commandDispatcher().dispatch(console, "permissions");
		assertThat(console.messages).containsExactly("Original command.");
	}

	public static final class ConflictingCommands {

		@Command(name = "permissions")
		public void permissions(ConsoleSource source) {
			source.sendMessage("Original command.");
		}
	}

	private void command(String input) {
		console.messages.clear();
		assertThat(proxy.commandDispatcher().dispatch(console, input)).isTrue();
		assertThat(console.messages).containsExactly("Saved permissions.");
	}

	private static final class TestConsole implements ConsoleSource {

		private final List<String> messages = new ArrayList<>();

		@Override
		public boolean hasPermission(String permission) {
			return true;
		}

		@Override
		public void sendMessage(String message) {
			messages.add(message);
		}
	}
}
