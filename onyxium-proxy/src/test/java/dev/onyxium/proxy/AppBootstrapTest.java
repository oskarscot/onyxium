package dev.onyxium.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import module java.base;
import org.junit.Test;

import dev.onyxium.command.Command;
import dev.onyxium.command.CommandDispatcher;
import dev.onyxium.proxy.api.command.ConsoleSource;
import dev.onyxium.proxy.auth.HytaleDeviceLogin.GameProfile;
import dev.onyxium.proxy.command.ProxyConsole;

public class AppBootstrapTest {

	@Test
	public void acceptsDefaultAndExplicitConfigurationPaths() {
		assertEquals(Path.of("onyxium.yml"), AppBootstrap.configPath());
		assertEquals(Path.of("custom path/proxy.yaml"), AppBootstrap.configPath("--config", "custom path/proxy.yaml"));
		assertThrows(IllegalArgumentException.class, () -> AppBootstrap.configPath("5520"));
		assertThrows(IllegalArgumentException.class, () -> AppBootstrap.configPath("--config"));
		assertThrows(IllegalArgumentException.class, () -> AppBootstrap.configPath("--config", ""));
	}

	@Test
	public void readsConsoleCommandsBufferedDuringProfileSelection() {
		var bootstrap = new AppBootstrap();
		bootstrap.input = new BufferedReader(new StringReader("""
				2

				probe first
				unknown
				probe
				/probe second
				"""));
		var profiles = List.of(new GameProfile(UUID.randomUUID(), "First"), new GameProfile(UUID.randomUUID(), "Second"));
		var commands = new CommandDispatcher();
		var handler = new ConsoleCommands();
		commands.registerHandler(handler);

		assertThat(bootstrap.selectProfile(profiles)).isEqualTo(profiles.get(1).uuid());
		new ProxyConsole(commands).read(bootstrap.input);

		assertThat(handler.values).containsExactly("first", "second");
	}

	public static final class ConsoleCommands {

		List<String> values = new ArrayList<>();

		@Command(name = "probe", permission = "test.console")
		public void probe(ConsoleSource source, String value) {
			values.add(value);
		}

	}

}
