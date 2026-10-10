package dev.onyxium.proxy.permission;

import module java.base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.onyxium.proxy.api.permission.PermissionProvider;
import dev.onyxium.proxy.api.permission.PermissionService;
import dev.onyxium.proxy.api.plugin.Plugin;

public final class ProxyPermissionService implements PermissionService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyPermissionService.class);

	private final ReentrantLock lock = new ReentrantLock();

	private volatile ProviderRegistration registration;

	/// Rejects a second active provider. Closing an old handle again cannot
	/// remove a newer registration.
	@Override
	public Registration register(PermissionProvider provider) {
		return register(null, provider);
	}

	/// Plugin-owned providers are also removed by the proxy after disable,
	/// including failed activation and cleanup callbacks.
	@Override
	public Registration register(Plugin owner, PermissionProvider provider) {
		lock.lock();
		try {
			if (owner != null) {
				if (owner.proxyServer().permissionService() != this) {
					throw new IllegalArgumentException("Plugin belongs to another permission service");
				}

				owner.dataDirectory(); // Require a registered plugin.
			}

			Objects.requireNonNull(provider, "provider");

			if (provider == this) {
				throw new IllegalArgumentException("The permission service cannot be its own provider");
			}

			if (registration != null) {
				throw new IllegalStateException("A permission provider is already registered");
			}

			registration = new ProviderRegistration(owner, provider);
			return registration;
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void unregister(Plugin owner) {
		Objects.requireNonNull(owner, "owner");

		lock.lock();
		try {
			if (registration != null && registration.owner == owner) {
				registration = null;
			}
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public boolean hasPermission(UUID player, String permission) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(permission, "permission");

		if (permission.isBlank()) {
			return false;
		}

		var current = registration;

		try {
			return current != null && current.provider.hasPermission(player, permission);
		}
		catch (RuntimeException failure) {
			LOGGER.error("Permission provider failed for {}", permission, failure);
			return false;
		}
	}

	@Override
	public Registration registration() {
		return this.registration;
	}

	private final class ProviderRegistration implements Registration {

		private final Plugin owner;

		private final PermissionProvider provider;

		private ProviderRegistration(Plugin owner, PermissionProvider provider) {
			this.owner = owner;
			this.provider = provider;
		}

		@Override
		public void close() {
			lock.lock();
			try {
				if (registration == this) {
					registration = null;
				}
			}
			finally {
				lock.unlock();
			}
		}
	}
}
