package dev.onyxium.proxy.api.plugin;

import module java.base;

/// Queries loaded plugins and controls whether their behavior is active. Obtain this
/// service from [dev.onyxium.proxy.api.ProxyServer#pluginService()].
///
/// "Loaded" means [Plugin#load()] returned successfully; merely registering an
/// instance does not make it visible here. "Enabled" means [Plugin#enable()] also
/// completed and the plugin has not since been disabled. Disabled instances remain
/// loaded until shutdown, which removes them from loaded-plugin queries. Storage paths
/// remain available for registered instances after shutdown.
///
/// Management calls run synchronously without locking and must not run concurrently.
/// Enable and disable are available only after plugin startup finishes and before
/// shutdown begins. Queries are also available during lifecycle callbacks, but those
/// callbacks cannot request further lifecycle transitions.
public interface PluginService {

	/// Finds a plugin whose load callback completed, including a disabled instance.
	/// Returns an empty optional for an unknown ID, a registered instance that has
	/// not finished loading, or a plugin removed by shutdown. IDs are case sensitive.
	Optional<Plugin> plugin(String id);

	/// Captures the currently loaded instances in dependency order, including disabled
	/// plugins. The list is unmodifiable and later transitions do not change its contents;
	/// the referenced plugin instances are still the original objects.
	List<Plugin> plugins();

	/// Finds the metadata registered for a loaded plugin. Visibility follows [#plugin(String)],
	/// so a known but not yet loaded plugin has no manifest in this query either.
	Optional<PluginManifest> manifest(String id);

	/// The persistent storage path assigned to a registered instance. Unlike loaded-plugin
	/// queries, this is available before its load callback and remains available after shutdown.
	/// The directory is created before loading, and its contents are kept across activations.
	///
	/// @return the absolute, normalized path under the proxy's plugins directory
	/// @throws IllegalStateException if the instance has not been registered with this service
	Path dataDirectory(Plugin plugin);

	/// Whether the ID currently identifies an instance that completed loading.
	/// Remains true after a runtime disable and becomes false after shutdown.
	boolean isLoaded(String id);

	/// Whether the loaded plugin completed activation and has not since been disabled.
	/// Returns false during its enable callback and for unknown or unloaded IDs.
	boolean isEnabled(String id);

	/// Activates an already loaded plugin without calling [Plugin#load()] again.
	/// Enable its declared dependencies first; this operation does not enable them
	/// automatically. An already enabled plugin receives no additional callback.
	///
	/// If activation fails, the manager invokes [Plugin#disable()] to clean up and
	/// leaves the instance loaded but disabled. Callback failures propagate as unchecked
	/// exceptions, with any cleanup failure retained as a suppressed exception.
	///
	/// @throws IllegalArgumentException if the ID does not identify a loaded plugin
	/// @throws IllegalStateException if a dependency is disabled or lifecycle management is unavailable
	void enable(String id);

	/// Deactivates the plugin while keeping its loaded instance available. Disable its
	/// enabled dependents first; this operation does not cascade to other plugins.
	/// An already disabled plugin receives no additional callback.
	///
	/// The enabled flag is cleared before cleanup. If the callback throws, the instance
	/// stays loaded and disabled, and cleanup is not retried automatically.
	///
	/// @throws IllegalArgumentException if the ID does not identify a loaded plugin
	/// @throws IllegalStateException if an enabled plugin depends on this ID or lifecycle management is unavailable
	void disable(String id);

}
