package dev.onyxium.proxy.io.connection;

import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.log.Logger;
import tech.kwik.core.server.ApplicationProtocolConnection;

public class HytaleProtocolConnection implements ApplicationProtocolConnection {

    private final Logger logger;

    public HytaleProtocolConnection(QuicConnection quicConnection, Logger logger) {
        this.logger = logger;
    }

    @Override
    public void acceptPeerInitiatedStream(QuicStream stream) {
        
    }

}
