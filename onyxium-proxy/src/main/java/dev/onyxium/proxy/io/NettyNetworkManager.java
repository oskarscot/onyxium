package dev.onyxium.proxy.io;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import dev.onyxium.proxy.api.network.NetworkInfo;
import dev.onyxium.proxy.api.network.NetworkManager;
import dev.onyxium.proxy.io.connection.QuicConnectionInitializer;
import dev.onyxium.proxy.io.connection.QuicStreamInitializer;
import dev.onyxium.proxy.lifecycle.Lifecycle;
import dev.onyxium.proxy.lifecycle.LifecycleException;
import dev.onyxium.proxy.util.SelfSignedCertificate;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicCongestionControlAlgorithm;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiStatus.Internal
public final class NettyNetworkManager implements NetworkManager, Lifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyNetworkManager.class);

    private final NetworkInfo networkInfo;

    private EventLoopGroup eventLoopGroup;
    private Channel channel;
    private volatile boolean running;

    public NettyNetworkManager(@NotNull InetSocketAddress bindAddress) {
        this.networkInfo = NetworkInfo.of(Objects.requireNonNull(bindAddress, "bindAddress"));
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
            throw new LifecycleException("Listener is already running on " + this.networkInfo.bindAddress());
        }

        this.eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        try {
            this.channel = new Bootstrap()
                    .group(this.eventLoopGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .handler(createCodec())
                    .bind(this.networkInfo.bindAddress())
                    .sync()
                    .channel();

            this.running = true;
            LOGGER.info("Listening on {} (ALPN: {})",
                    this.networkInfo.bindAddress(), String.join(", ", this.networkInfo.applicationProtocols()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownEventLoopGroup();

            throw new LifecycleException("Interrupted while binding to " + this.networkInfo.bindAddress(), e);
        } catch (Exception e) {
            shutdownEventLoopGroup();

            throw new LifecycleException("Failed to start the listener on " + this.networkInfo.bindAddress(), e);
        }
    }

    @Override
    public void stop() {
        if (!this.running) {
            return;
        }

        this.running = false;
        this.channel.close().syncUninterruptibly();
        this.channel = null;

        shutdownEventLoopGroup();
    }

    private void shutdownEventLoopGroup() {
        this.eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        this.eventLoopGroup = null;
    }

    private ChannelHandler createCodec() throws GeneralSecurityException {
        return new QuicServerCodecBuilder()
                .sslContext(createSslContext())
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
                .activeMigration(false)
                .discoverPmtu(true)
                .maxIdleTimeout(QuicTransportParameters.IDLE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .initialMaxData(QuicTransportParameters.CONNECTION_BUFFER_SIZE)
                .initialMaxStreamDataBidirectionalLocal(QuicTransportParameters.STREAM_BUFFER_SIZE)
                .initialMaxStreamDataBidirectionalRemote(QuicTransportParameters.STREAM_BUFFER_SIZE)
                .initialMaxStreamDataUnidirectional(QuicTransportParameters.STREAM_BUFFER_SIZE)
                .initialMaxStreamsBidirectional(QuicTransportParameters.MAX_CONCURRENT_BIDIRECTIONAL_STREAMS)
                .initialMaxStreamsUnidirectional(QuicTransportParameters.MAX_CONCURRENT_UNIDIRECTIONAL_STREAMS)
                .handler(new QuicConnectionInitializer())
                .streamHandler(new QuicStreamInitializer())
                .build();
    }

    private QuicSslContext createSslContext() throws GeneralSecurityException {
        var certificate = SelfSignedCertificate.generate(SelfSignedCertificate.DEFAULT_COMMON_NAME);

        return QuicSslContextBuilder
                .forServer(certificate.privateKey(), null, certificate.certificate())
                .applicationProtocols(this.networkInfo.applicationProtocols().toArray(String[]::new))
                .earlyData(false)
                .clientAuth(ClientAuth.REQUIRE)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
    }
}
