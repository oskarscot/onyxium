package dev.onyxium.proxy.api.plugin;

import module java.base;
import org.slf4j.Logger;

import dev.onyxium.proxy.api.ProxyServer;

/// Base class for code that adds behavior to the proxy. The supplied [ProxyServer]
/// provides access to commands, events, players, networking, and other plugins.
///
/// The proxy calls [#load()] once during startup, then [#enable()] to activate the
/// plugin. Disabling and re-enabling a loaded plugin repeats the activation callbacks
/// without loading it again. Use [PluginService] to request these transitions; calling
/// the callbacks directly does not update the manager's state or check dependencies.
///
/// Callbacks run synchronously on the caller's thread. They may query the plugin
/// service, but cannot initiate another lifecycle transition. Each plugin owns the
/// resources and API registrations it creates and must release them in [#disable()].
public abstract class Plugin {

	ProxyServer proxyServer;

	protected Plugin(ProxyServer proxyServer) {
		this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
	}

	/// The same proxy supplied at construction, so plugins do not need to obtain
	/// individual services separately. Its listener has not started during startup callbacks.
	public final ProxyServer proxyServer() {
		return proxyServer;
	}

	/// The plugin's persistent storage directory, named after its manifest ID under
	/// the proxy's plugins directory. Registration assigns the path, and startup creates
	/// the directory before [#load()] runs. Disable and shutdown leave its contents intact.
	///
	/// @return the absolute, normalized directory path
	/// @throws IllegalStateException if called before the plugin has been registered
	public final Path dataDirectory() {
		return proxyServer.pluginService().dataDirectory(this);
	}

	/// Logs under this plugin's manifest ID using the proxy's logging configuration.
	/// Available during lifecycle callbacks and retained across disable, re-enable,
	/// and shutdown. The constructor runs before registration, so use this from [#load()] onward.
	///
	/// @throws IllegalStateException if called before the plugin has been registered
	public final Logger logger() {
		return proxyServer.pluginService().logger(this);
	}

	/// Prepare state needed for activation, such as validating configuration or building
	/// command handlers. The manager marks the plugin loaded only after this callback returns.
	///
	/// Dependencies have finished loading, but no plugin has been enabled yet. Defer work
	/// that needs an active dependency to [#enable()]. This callback runs once; subsequent
	/// re-enables reuse the loaded state. If it fails, [#disable()] still runs for cleanup.
	public void load() { }

	/// Activate the plugin's behavior, for example by registering commands or starting
	/// tasks. All plugins have loaded and this plugin's declared dependencies are enabled.
	/// On initial startup, the network listener starts only after every plugin enables.
	///
	/// A plugin becomes enabled only after this callback returns successfully. This
	/// callback can run again after a runtime disable, so resources created here must
	/// be released by [#disable()]. An activation failure also invokes that cleanup callback.
	public void enable() { }

	/// Remove the plugin's active behavior and release owned resources: unregister
	/// commands, cancel tasks, and close connections or files. Registration cleanup
	/// is the plugin's responsibility; the manager does not remove handlers automatically.
	///
	/// This callback also runs after a partially completed load or enable, so tolerate
	/// resources that were never created. The manager clears the enabled flag before
	/// calling it. Runtime disabling keeps the instance loaded for later re-enabling.
	///
	/// During proxy shutdown the listener stops first, then dependents are disabled
	/// before their dependencies. Each activation receives one cleanup attempt, even
	/// if cleanup throws; repeated shutdown does not retry callbacks.
	public void disable() { }

}
