package dev.onyxium.example;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.plugin.Plugin;

public final class ExamplePlugin extends Plugin {

	public ExamplePlugin(ProxyServer proxyServer) {
		super(proxyServer);
	}

	@Override
	public void load() {
		logger().info("Loading Example Plugin...");
	}

	@Override
	public void enable() {
		logger().info("Enabling Example Plugin...");
	}

	@Override
	public void disable() {
		logger().info("Disabling Example Plugin...");
	}

}
