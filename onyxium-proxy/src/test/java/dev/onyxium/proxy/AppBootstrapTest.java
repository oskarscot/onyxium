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
        var bootstrap = new AppBootstrap();
        var command = bootstrap.commandLine();
        command.parseArgs();
        assertEquals(Path.of("onyxium.yml"), bootstrap.configurationPath);
        command.parseArgs("--config", "custom path/proxy.yaml");
        assertEquals(Path.of("custom path/proxy.yaml"), bootstrap.configurationPath);
        command.parseArgs("--config=other.yml");
        assertEquals(Path.of("other.yml"), bootstrap.configurationPath);
        assertThrows(picocli.CommandLine.ParameterException.class, () -> command.parseArgs("5520"));
        assertThrows(picocli.CommandLine.ParameterException.class, () -> command.parseArgs("--config"));
        assertThrows(picocli.CommandLine.ParameterException.class, () -> command.parseArgs("--config", ""));
        assertThrows(picocli.CommandLine.ParameterException.class, () -> command.parseArgs("--unknown"));
    }

    @Test
    public void helpAndVersionDoNotStartTheProxy() {
        var directory = Path.of("build", "must-not-create", "onyxium.yml");
        var output = new StringWriter();
        var command = new AppBootstrap().commandLine().setOut(new PrintWriter(output));
        assertEquals(0, command.execute("--config", directory.toString(), "--help"));
        assertThat(output.toString()).contains("Usage:", "--config", "--version");
        output.getBuffer().setLength(0);
        assertEquals(0, command.execute("--version"));
        assertThat(output.toString()).contains("Onyxium v", "commit:");
        assertThat(Files.exists(directory)).isFalse();
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
