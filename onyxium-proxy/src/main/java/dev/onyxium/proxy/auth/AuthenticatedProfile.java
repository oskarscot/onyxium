package dev.onyxium.proxy.auth;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/// Identity derived exclusively from validated, signed claims. Skin is retained for backend setup.
public record AuthenticatedProfile(UUID uniqueId, String username, String skin, List<String> entitlements) {
	public AuthenticatedProfile {
		Objects.requireNonNull(uniqueId, "uniqueId");
		Objects.requireNonNull(username, "username");
		entitlements = List.copyOf(entitlements);
	}
}
