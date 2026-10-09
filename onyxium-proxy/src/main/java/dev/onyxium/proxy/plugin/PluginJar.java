package dev.onyxium.proxy.plugin;

import com.nimbusds.jose.util.JSONObjectUtils;
import module java.base;

import dev.onyxium.proxy.api.plugin.PluginManifest;
import dev.onyxium.proxy.lifecycle.LifecycleException;

/// Read metadata without loading plugin code or inheriting another JAR's manifest.
record PluginJar(Path path, PluginManifest manifest) {

	static PluginJar read(Path path) {
		try (var jar = new JarFile(path.toFile())) {
			var entry = jar.getJarEntry(PluginManifest.RESOURCE_NAME);
			if (entry == null || entry.isDirectory()) {
				throw new IllegalArgumentException("Missing root " + PluginManifest.RESOURCE_NAME + " resource");
			}
			try (var input = jar.getInputStream(entry)) {
				var json = JSONObjectUtils.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
				return new PluginJar(path, new PluginManifest(JSONObjectUtils.getString(json, "id"),
					JSONObjectUtils.getString(json, "version"), JSONObjectUtils.getString(json, "main"),
					JSONObjectUtils.getStringList(json, "authors"), JSONObjectUtils.getStringList(json, "dependencies")));
			}
		}
		catch (IOException | ParseException | IllegalArgumentException | NullPointerException failure) {
			throw new LifecycleException("Could not read plugin manifest from " + path + ": " + failure.getMessage(), failure);
		}
	}

}
