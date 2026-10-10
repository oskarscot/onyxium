package dev.onyxium.proxy.api.permission;

import org.jetbrains.annotations.Nullable;

import dev.onyxium.proxy.api.plugin.Plugin;

/// A shared permission service used by the proxy to determine whether a player has the
/// correct permission to execute a command. Only one registration can exist at a time and at
/// least one is required for Onyxium to work. If no provider is registered then only console commands will work.
/// Implementing plugins should implement this interface and register it with the proxy by using the register function.
public interface PermissionService extends PermissionProvider {

	/// Registers a provider without a plugin owner. The caller must close the handle.
	/// Rejects registration if another provider is already active.
	Registration register(PermissionProvider provider);

	/// Registers a provider owned by a plugin registered with this proxy.
	/// Plugin-owned providers are also removed after disable, including failed
	/// activation or cleanup callbacks. Only one provider may be active at a time.
	Registration register(Plugin owner, PermissionProvider provider);

	/// Removes the given plugin's registration. Other owners are unaffected.
	void unregister(Plugin owner);

	/// Returns the active registration, or null when no provider is registered.
	@Nullable
	Registration registration();

	/// Handle for releasing one provider registration.
	interface Registration extends AutoCloseable {

		/// Removes this provider if still active. Safe to repeat; closing an old
		/// handle cannot remove a newer provider. Does not throw checked exceptions.
		@Override
		void close();

	}

}
