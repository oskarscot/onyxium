package dev.onyxium.proxy.io;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dev.onyxium.proxy.util.SelfSignedCertificate;

public class ClientCertificateHandshakeTest {

	private static final InetSocketAddress BIND_ADDRESS = new InetSocketAddress("127.0.0.1", 5520);

	private static final long TIMEOUT_SECONDS = 10;

	private static final long BUFFER_SIZE = 128 * 1024L;

	private NettyNetworkManager networkManager;

	private EventLoopGroup clientGroup;

	private Channel clientChannel;

	@Before
	public void startServer() {
		this.networkManager = new NettyNetworkManager(BIND_ADDRESS);
		this.networkManager.start();

		this.clientGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
	}

	@After
	public void stopServer() {
		if (this.clientChannel != null) {
			this.clientChannel.close().syncUninterruptibly();
		}

		this.clientGroup.shutdownGracefully().syncUninterruptibly();
		this.networkManager.stop();
	}

	@Test
	public void clientWithCertificateCanOpenStream() throws Exception {
		var certificate = SelfSignedCertificate.generate("Onyxium Test Client");
		var quicChannel = connect(QuicSslContextBuilder.forClient()
			.keyManager(certificate.privateKey(), null, certificate.certificate()));

		var stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInboundHandlerAdapter())
			.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		assertNotNull("expected a stream to be created", stream);
		assertTrue("connection was closed despite presenting a certificate", quicChannel.isActive());
	}

	@Test
	public void clientWithoutCertificateIsRejected() throws Exception {
		QuicChannel quicChannel;
		try {
			quicChannel = connect(QuicSslContextBuilder.forClient());
		}
		catch (ExecutionException e) {
			return;
		}

		assertTrue("connection survived without a client certificate",
				quicChannel.closeFuture().await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
	}

	private QuicChannel connect(QuicSslContextBuilder sslContextBuilder) throws Exception {
		QuicSslContext sslContext = sslContextBuilder
			.applicationProtocols(this.networkManager.networkInfo().applicationProtocols().toArray(String[]::new))
			.trustManager(InsecureTrustManagerFactory.INSTANCE)
			.build();

		var codec = new QuicClientCodecBuilder().sslContext(sslContext)
			.maxIdleTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.initialMaxData(BUFFER_SIZE)
			.initialMaxStreamDataBidirectionalLocal(BUFFER_SIZE)
			.initialMaxStreamDataBidirectionalRemote(BUFFER_SIZE)
			.initialMaxStreamsBidirectional(4)
			.build();

		this.clientChannel = new Bootstrap().group(this.clientGroup)
			.channel(NioDatagramChannel.class)
			.handler(codec)
			.bind(0)
			.sync()
			.channel();

		return QuicChannel.newBootstrap(this.clientChannel)
			.streamHandler(new ChannelInboundHandlerAdapter())
			.remoteAddress(BIND_ADDRESS)
			.connect()
			.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

}
