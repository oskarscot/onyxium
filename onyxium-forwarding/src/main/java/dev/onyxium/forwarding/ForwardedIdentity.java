package dev.onyxium.forwarding;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;

public record ForwardedIdentity(UUID uuid, String username, String skin, InetSocketAddress address) {

	public ForwardedIdentity {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(username);
		Objects.requireNonNull(address);
		if (username.isBlank() || username.length() > 64 || address.isUnresolved()) {
			throw new IllegalArgumentException("Invalid forwarded identity");
		}
	}

	@Override
	public String toString() {
		return "ForwardedIdentity[uuid=" + uuid + ", username=" + username + "]";
	}
}
