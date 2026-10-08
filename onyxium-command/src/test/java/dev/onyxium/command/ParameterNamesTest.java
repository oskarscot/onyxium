package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import javax.tools.ToolProvider;

import module java.base;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ParameterNamesTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void rejectsRealBytecodeWithoutParameterNames() throws Exception {
		var directory = folder.newFolder().toPath();
		var source = directory.resolve("UnnamedHandler.java");
		Files.writeString(source, """
				import dev.onyxium.command.Command;
				import dev.onyxium.command.CommandSource;
				public class UnnamedHandler {
				    @Command(name = "foo")
				    public void execute(CommandSource source, String argument) {}
				}
				""");
		var classpath = Command.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();

		assertThat(ToolProvider.getSystemJavaCompiler()
			.run(null, null, null, "-classpath", classpath, "-d", directory.toString(), source.toString()))
			.isEqualTo(0);
		try (var loader = new URLClassLoader(new URL[] { directory.toUri().toURL() },
				Command.class.getClassLoader())) {
			var handler = loader.loadClass("UnnamedHandler").getConstructor().newInstance();
			var dispatcher = new CommandDispatcher();
			assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(handler))
				.withMessageContaining("-parameters");
		}
	}

}
