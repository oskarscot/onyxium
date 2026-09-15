package dev.onyxium.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.nio.file.Path;

import org.junit.Test;

public class AppBootstrapTest {

	@Test
	public void acceptsDefaultAndExplicitConfigurationPaths() {
		assertEquals(Path.of("onyxium.yml"), AppBootstrap.configPath());
		assertEquals(Path.of("custom path/proxy.yaml"), AppBootstrap.configPath("--config", "custom path/proxy.yaml"));
		assertThrows(IllegalArgumentException.class, () -> AppBootstrap.configPath("5520"));
		assertThrows(IllegalArgumentException.class, () -> AppBootstrap.configPath("--config"));
		assertThrows(IllegalArgumentException.class, () -> AppBootstrap.configPath("--config", ""));
	}

}
