package dev.onyxium.proxy.api.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import module java.base;
import org.junit.Test;

public class PluginManifestTest {

	@Test
	public void omittedDependenciesDefaultToEmpty() {
		var author = List.of("Oskar");
		var omitted = new PluginManifest("core", "1.0", "example.CorePlugin", author);
		var absent = new PluginManifest("core", "1.0", "example.CorePlugin", author, null);

		assertThat(omitted.dependencies()).isEmpty();
		assertThat(absent).isEqualTo(omitted);
	}

	@Test
	public void metadataListsAreSnapshotsOfTheSuppliedValues() {
		var author = new ArrayList<>(List.of("Oskar", "Contributor"));
		var dependencies = new ArrayList<>(List.of("shared"));
		var manifest = new PluginManifest("core", "1.0", "example.CorePlugin", author, dependencies);
		author.clear();
		dependencies.clear();

		assertThat(manifest.author()).containsExactly("Oskar", "Contributor");
		assertThat(manifest.dependencies()).containsExactly("shared");
	}

}
