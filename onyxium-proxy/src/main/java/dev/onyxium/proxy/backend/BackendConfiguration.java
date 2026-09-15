package dev.onyxium.proxy.backend;

import java.net.InetSocketAddress;

import dev.onyxium.forwarding.ForwardingToken;

public record BackendConfiguration(InetSocketAddress address, ForwardingToken tokens) {

	public BackendConfiguration {
		if (address == null || address.isUnresolved() || address.getPort() == 0 || tokens == null) {
			throw new IllegalArgumentException("Invalid backend configuration");
		}
	}

}
