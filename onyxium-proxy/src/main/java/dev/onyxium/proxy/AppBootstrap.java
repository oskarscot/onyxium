package dev.onyxium.proxy;

import java.net.InetSocketAddress;

import dev.onyxium.proxy.io.NettyNetworkManager;

public final class AppBootstrap {

	private static final String DEFAULT_BIND_HOST = "0.0.0.0";

	private static final int DEFAULT_BIND_PORT = 5520;

	// TODO: Actual args parsing
	void main(String... args) {
		var port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_BIND_PORT;
		var bindAddress = new InetSocketAddress(DEFAULT_BIND_HOST, port);

		var proxy = new OnyxiumProxy(new NettyNetworkManager(bindAddress));

		Runtime.getRuntime().addShutdownHook(new Thread(proxy::stop, "onyxium-shutdown"));
		proxy.start();
	}

}
