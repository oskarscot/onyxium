package dev.onyxium.proxy.io.connection;

import tech.kwik.core.QuicConnection;
import tech.kwik.core.log.Logger;
import tech.kwik.core.server.ApplicationProtocolConnection;
import tech.kwik.core.server.ApplicationProtocolConnectionFactory;

public class HytaleProtocolConnectionFactory implements ApplicationProtocolConnectionFactory {

    private final Logger logger;

    public HytaleProtocolConnectionFactory(Logger logger) {
        this.logger = logger;
    }

    @Override
    public ApplicationProtocolConnection createConnection(String protocol, QuicConnection quicConnection) {
        return new HytaleProtocolConnection(quicConnection, logger);
    }

}
