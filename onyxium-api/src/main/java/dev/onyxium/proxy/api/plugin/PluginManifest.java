package dev.onyxium.proxy.api.plugin;

import module java.base;

/// Metadata used to identify a plugin and determine its startup order. The planned
/// JAR loader will read these fields from a root `onyxium.json` resource; currently
/// proxy internals supply this record alongside an already constructed [Plugin].
///
/// Dependencies are required plugin IDs. Their load and enable callbacks run before
/// this plugin's corresponding callbacks, and their cleanup runs afterward. Missing
/// dependencies or dependency cycles prevent startup before any callback is invoked.
/// The dependency list is copied so callers cannot change the registered graph later.
///
/// @param id case-sensitive identity used for lookups and dependency declarations
/// @param version descriptive plugin version; no version constraints are evaluated
/// @param main binary name of the entry class, such as `example.WelcomePlugin`; currently metadata only
/// @param dependencies required IDs, or an empty list for an independent plugin
public record PluginManifest(String id, String version, String main, List<String> dependencies) {

	/// The manifest belongs at the root of a plugin JAR, not in the entry class's package.
	public static final String RESOURCE_NAME = "onyxium.json";

	public PluginManifest {
		requireText(id, "id");
		requireText(version, "version");
		requireText(main, "main");
		dependencies = List.copyOf(dependencies);
		for (var dependency : dependencies) {
			requireText(dependency, "dependency");
		}
		if (dependencies.contains(id)) {
			throw new IllegalArgumentException("Plugin '" + id + "' cannot depend on itself");
		}
		if (dependencies.stream().distinct().count() != dependencies.size()) {
			throw new IllegalArgumentException("Plugin '" + id + "' has duplicate dependencies");
		}
	}

	static void requireText(String value, String field) {
		if (value == null || value.isBlank() || !value.equals(value.strip())) {
			throw new IllegalArgumentException("Plugin " + field + " must be nonblank without surrounding whitespace");
		}
	}

}
