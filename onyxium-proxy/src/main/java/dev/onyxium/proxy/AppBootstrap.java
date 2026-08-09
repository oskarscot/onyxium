package dev.onyxium.proxy;

import java.net.InetSocketAddress;

import dev.onyxium.proxy.io.KwikNetworkManager;

import tech.kwik.core.log.Logger;
import tech.kwik.core.log.SysOutLogger;

public final class AppBootstrap {

    private static final String DEFAULT_BIND_HOST = "0.0.0.0";
    private static final int DEFAULT_BIND_PORT = 5520;

    // TODO: Actual args parsing
    void main(String... args) {
        var port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_BIND_PORT;

        var logger = createLogger();
        var bindAddress = new InetSocketAddress(DEFAULT_BIND_HOST, port);

        var proxy = new OnyxiumProxy(new KwikNetworkManager(bindAddress, logger));

        Runtime.getRuntime().addShutdownHook(new Thread(proxy::stop, "onyxium-shutdown"));
        proxy.start();
    }

    // TODO: this is a kwik ONLY logger, replace it with logback and then create a logback kwik logger
    private static Logger createLogger() {
        var logger = new SysOutLogger();
        logger.logInfo(true);
        logger.logWarning(true);
        logger.timeFormat(Logger.TimeFormat.Long);

        return logger;
    }
}
