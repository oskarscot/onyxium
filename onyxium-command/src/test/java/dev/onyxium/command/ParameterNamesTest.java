package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URLClassLoader;
import java.nio.file.Files;

import javax.tools.ToolProvider;

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
				public class UnnamedHandler {
				    @Command(name = "foo")
				    public void execute(String source, String argument) {}
				}
				""");
		var classpath = Command.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
		assertThat(ToolProvider.getSystemJavaCompiler()
			.run(null, null, null, "-classpath", classpath, "-d", directory.toString(), source.toString()))
			.isEqualTo(0);
		try (var loader = new URLClassLoader(new java.net.URL[] { directory.toUri().toURL() },
				Command.class.getClassLoader())) {
			var handler = loader.loadClass("UnnamedHandler").getConstructor().newInstance();
			var dispatcher = new CommandDispatcher<>(String.class, (_, _) -> true);
			assertThatIllegalArgumentException().isThrownBy(() -> dispatcher.registerHandler(handler))
				.withMessageContaining("-parameters");
		}
	}

}
