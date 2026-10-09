package dev.onyxium.proxy.api.plugin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import module java.base;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PluginManifestValidationTest {

	@Parameterized.Parameters(name = "id={0}, author={1}")
	public static List<Object[]> invalidMetadata() {
		return List.of(
			new Object[] { "core", List.of(), "Plugin author must contain at least one name" },
			new Object[] { "../outside", List.of("Oskar"), "Plugin id must be a single directory name" }
		);
	}

	@Parameterized.Parameter
	public String id;

	@Parameterized.Parameter(1)
	public List<String> author;

	@Parameterized.Parameter(2)
	public String message;

	@Test
	public void rejectsMissingAuthorNamesAndIdsThatCannotNameTheirDataDirectory() {
		assertThatThrownBy(() -> new PluginManifest(id, "1.0", "example.CorePlugin", author))
			.isInstanceOf(IllegalArgumentException.class).hasMessage(message);
	}

}
