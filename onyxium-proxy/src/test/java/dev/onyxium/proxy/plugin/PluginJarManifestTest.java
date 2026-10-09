package dev.onyxium.proxy.plugin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import module java.base;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.onyxium.proxy.lifecycle.LifecycleException;

public class PluginJarManifestTest {

	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void manifestMustBeAtTheJarRoot() throws IOException {
		var jar = PluginJars.create(temporary.getRoot().toPath().resolve("core.jar"), Map.of(),
			Map.of("nested/onyxium.json", PluginJars.utf8(PluginJars.manifest("core", "fixture.CorePlugin"))));

		assertThatThrownBy(() -> PluginJar.read(jar)).isInstanceOf(LifecycleException.class)
			.hasMessage("Could not read plugin manifest from " + jar + ": Missing root onyxium.json resource");
	}

}
