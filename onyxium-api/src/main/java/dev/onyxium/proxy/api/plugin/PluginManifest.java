package dev.onyxium.proxy.api.plugin;

import module java.base;

/// Metadata read from a plugin JAR's root `onyxium.json` resource to identify its
/// entry class, authors, and startup order. Proxy internals can also register this
/// record alongside an already constructed [Plugin].
///
/// Dependencies are required plugin IDs. Their load and enable callbacks run before
/// this plugin's corresponding callbacks, and their cleanup runs afterward. Missing
/// dependencies or dependency cycles prevent startup before any callback is invoked.
/// The dependency list is copied so callers cannot change the registered graph later.
///
/// @param id case-sensitive identity used for lookups and dependency declarations
/// @param version descriptive plugin version; no version constraints are evaluated
/// @param main FQN of the entry class, such as `dev.onyxium.plugin.WelcomePlugin`
/// @param author names of the plugin's authors; at least one nonblank name is required
/// @param dependencies optional required IDs; an omitted list becomes empty
public record PluginManifest(String id, String version, String main, List<String> author, List<String> dependencies) {

	public static final String RESOURCE_NAME = "onyxium.json";

	public PluginManifest(String id, String version, String main, List<String> author) {
		this(id, version, main, author, List.of());
	}

	public PluginManifest {
		requireText(id, "id");
		requireDirectoryName(id);
		requireText(version, "version");
		requireText(main, "main");
		if (author == null || author.isEmpty()) {
			throw new IllegalArgumentException("Plugin author must contain at least one name");
		}
		for (var name : author) {
			requireText(name, "author name");
		}
		author = List.copyOf(author);
		dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
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

	/// IDs also name data directories, so they must not resolve outside the plugins directory.
	static void requireDirectoryName(String id) {
		if (!isDirectoryName(id)) {
			throw new IllegalArgumentException("Plugin id must be a single directory name");
		}
	}

	static boolean isDirectoryName(String id) {
		var path = Path.of(id);
		return path.getRoot() == null && path.getNameCount() == 1 && !id.equals(".") && !id.equals("..") && !id.contains("/") && !id.contains("\\");
	}

	static void requireText(String value, String field) {
		if (value == null || value.isBlank() || !value.equals(value.strip())) {
			throw new IllegalArgumentException("Plugin " + field + " must be nonblank without surrounding whitespace");
		}
	}

}
