package dev.onyxium.proxy.io;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.util.Objects;

import dev.onyxium.proxy.api.network.NetworkInfo;
import dev.onyxium.proxy.api.network.NetworkManager;
import dev.onyxium.proxy.io.connection.HytaleProtocolConnectionFactory;
import dev.onyxium.proxy.lifecycle.Lifecycle;
import dev.onyxium.proxy.lifecycle.LifecycleException;
import dev.onyxium.proxy.util.SelfSignedCertificate;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import tech.kwik.core.log.Logger;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;

@ApiStatus.Internal
public final class KwikNetworkManager implements NetworkManager, Lifecycle {

    private static final String KEY_ALIAS = "onyxium";
    private static final char[] KEY_PASSWORD = "onyxium".toCharArray();

    private final NetworkInfo networkInfo;
    private final Logger logger;

    private ServerConnector serverConnector;
    private volatile boolean running;

    public KwikNetworkManager(@NotNull InetSocketAddress bindAddress, @NotNull Logger logger) {
        this.networkInfo = NetworkInfo.of(Objects.requireNonNull(bindAddress, "bindAddress"));
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    @NotNull
    public NetworkInfo networkInfo() {
        return this.networkInfo;
    }

    @Override
    public boolean running() {
        return this.running;
    }

    @Override
    public void start() throws LifecycleException {
        if (this.running) {
            throw new LifecycleException("Listener is already running on " + networkInfo.bindAddress());
        }

        try {
            this.serverConnector = createConnector();

            var connectionFactory = new HytaleProtocolConnectionFactory(logger);
            for (var applicationProtocol : networkInfo.applicationProtocols()) {
                this.serverConnector.registerApplicationProtocol(applicationProtocol, connectionFactory);
            }

            this.serverConnector.start();
            this.running = true;
        } catch (GeneralSecurityException | SocketException e) {
            throw new LifecycleException("Failed to start the listener on " + networkInfo.bindAddress(), e);
        }
    }

    @Override
    public void stop() {
        if (!this.running) {
            return;
        }

        this.running = false;
        this.serverConnector.close();
        this.serverConnector = null;
    }

    private ServerConnector createConnector() throws GeneralSecurityException, SocketException {
        var certificate = SelfSignedCertificate.generate(SelfSignedCertificate.DEFAULT_COMMON_NAME);
        var keyStore = certificate.toKeyStore(KEY_ALIAS, KEY_PASSWORD);

        var connectionConfig = ServerConnectionConfig.builder()
                .maxIdleTimeoutInSeconds((int) QuicTransportParameters.IDLE_TIMEOUT.toSeconds())
                .maxOpenPeerInitiatedBidirectionalStreams(QuicTransportParameters.MAX_CONCURRENT_BIDIRECTIONAL_STREAMS)
                .maxOpenPeerInitiatedUnidirectionalStreams(QuicTransportParameters.MAX_CONCURRENT_UNIDIRECTIONAL_STREAMS)
                .maxConnectionBufferSize(QuicTransportParameters.CONNECTION_BUFFER_SIZE)
                .maxBidirectionalStreamBufferSize(QuicTransportParameters.STREAM_BUFFER_SIZE)
                .maxUnidirectionalStreamBufferSize(QuicTransportParameters.STREAM_BUFFER_SIZE)
                .build();

        return ServerConnector.builder()
                .withSocket(new DatagramSocket(networkInfo.bindAddress()))
                .withConfiguration(connectionConfig)
                .withKeyStore(keyStore, KEY_ALIAS, KEY_PASSWORD)
                .withLogger(logger)
                .build();
    }
}
