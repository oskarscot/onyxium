package dev.onyxium.proxy.api.permission;

import java.util.UUID;

/// Checks proxy permissions for verified player UUIDs. Implementations must be
/// thread-safe and non-blocking: checks may run on network event loops.
@FunctionalInterface
public interface PermissionProvider {

	boolean hasPermission(UUID player, String permission);

}
