package dev.onyxium.proxy.backend;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.After;
import org.junit.Test;

import dev.onyxium.forwarding.ForwardingToken;
import dev.onyxium.proxy.auth.AuthenticatedProfile;
import dev.onyxium.proxy.auth.AuthenticationService;
import dev.onyxium.proxy.io.NettyNetworkManager;
import dev.onyxium.proxy.io.packet.ClientType;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.PacketDecoder;
import dev.onyxium.proxy.io.packet.PacketEncoder;
import dev.onyxium.proxy.io.packet.ProtocolVersion;
import dev.onyxium.proxy.io.packet.UnknownPacket;
import dev.onyxium.proxy.io.packet.auth.AuthGrant;
import dev.onyxium.proxy.io.packet.auth.AuthToken;
import dev.onyxium.proxy.io.packet.auth.Connect;
import dev.onyxium.proxy.io.packet.auth.ServerAuthToken;
import dev.onyxium.proxy.io.packet.connection.ServerDisconnect;
import dev.onyxium.proxy.util.SelfSignedCertificate;

public class ForwardingIntegrationTest {

	private final MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

	private final ForwardingToken tokens = new ForwardingToken(Base64.getEncoder().encodeToString(new byte[32]));

	private final AuthenticatedProfile profile = new AuthenticatedProfile(UUID.randomUUID(), "Oskar", null, List.of());

	private final LinkedBlockingQueue<Packet> received = new LinkedBlockingQueue<>();

	private final LinkedBlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();

	private final CompletableFuture<QuicChannel> upstream = new CompletableFuture<>();

	private final CompletableFuture<QuicStreamChannel> upstreamGame = new CompletableFuture<>();

	private final CompletableFuture<Void> verified = new CompletableFuture<>();

	private Channel backendSocket;

	private Channel clientSocket;

	private Channel rejectingBackendSocket;

	private final CompletableFuture<QuicChannel> rejectedUpstream = new CompletableFuture<>();

	private NettyNetworkManager proxy;

	@After
	public void close() {
		if (clientSocket != null)
			clientSocket.close().syncUninterruptibly();
		if (proxy != null)
			proxy.stop();
		if (backendSocket != null)
			backendSocket.close().syncUninterruptibly();
		if (rejectingBackendSocket != null)
			rejectingBackendSocket.close().syncUninterruptibly();
		group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
	}

	@Test
	public void forwardsIdentityGamePacketsAndServerStreamsAndClosesBothSides() throws Exception {
		var game = start(false);
		verified.get(10, TimeUnit.SECONDS);
		assertTrue(received.poll(10, TimeUnit.SECONDS) instanceof ServerAuthToken);
		var welcome = (UnknownPacket) received.poll(10, TimeUnit.SECONDS);
		assertNotNull(welcome);
		assertArrayEquals(frame(1000, new byte[] { 9, 8, 7 }), welcome.bytes());
		game.writeAndFlush(new UnknownPacket(frame(1001, new byte[] { 1, 2, 3 }))).sync();
		var echo = (UnknownPacket) received.poll(10, TimeUnit.SECONDS);
		assertNotNull(echo);
		assertArrayEquals(frame(1001, new byte[] { 1, 2, 3 }), echo.bytes());

		var largePayload = new byte[256 * 1024];
		new Random(1).nextBytes(largePayload);
		game.writeAndFlush(new UnknownPacket(frame(1002, largePayload))).sync();
		var largeEcho = (UnknownPacket) received.poll(10, TimeUnit.SECONDS);
		assertNotNull(largeEcho);
		assertArrayEquals(frame(1002, largePayload), largeEcho.bytes());

		var server = upstream.get(10, TimeUnit.SECONDS);
		var chunkStream = server.createStream(QuicStreamType.UNIDIRECTIONAL, new ChannelInboundHandlerAdapter())
			.get(10, TimeUnit.SECONDS);
		chunkStream.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 3, 1, 4, 1, 5 })).sync();
		chunkStream.shutdownOutput().sync();
		assertArrayEquals(new byte[] { 3, 1, 4, 1, 5 }, chunks.poll(10, TimeUnit.SECONDS));

		var auxiliary = game.parent()
			.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
				@Override
				protected void initChannel(QuicStreamChannel channel) {
					channel.pipeline().addLast(collectBytes());
				}
			})
			.get(10, TimeUnit.SECONDS);
		auxiliary.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 2, 7, 1, 8 })).sync();
		assertArrayEquals(new byte[] { 2, 7, 1, 8 }, chunks.poll(10, TimeUnit.SECONDS));

		game.parent().close().sync();
		assertTrue(server.closeFuture().await(10, TimeUnit.SECONDS));
		assertFalse(server.isActive());
	}

	@Test
	public void rejectsBackendWithoutProofBeforeRelayingSetup() throws Exception {
		var game = start(true);
		assertTrue(received.poll(10, TimeUnit.SECONDS) instanceof ServerAuthToken);
		var rejection = received.poll(10, TimeUnit.SECONDS);
		assertTrue(rejection instanceof ServerDisconnect);
		assertTrue(game.parent().closeFuture().await(10, TimeUnit.SECONDS));
		assertTrue(received.isEmpty());
	}

	@Test
	public void backendDisconnectWithoutReasonClosesClient() throws Exception {
		var game = start(false);
		assertTrue(received.poll(10, TimeUnit.SECONDS) instanceof ServerAuthToken);
		assertTrue(received.poll(10, TimeUnit.SECONDS) instanceof UnknownPacket);
		upstreamGame.get(10, TimeUnit.SECONDS).writeAndFlush(new ServerDisconnect(null)).sync();
		assertTrue(received.poll(10, TimeUnit.SECONDS) instanceof ServerDisconnect);
		assertTrue(game.parent().closeFuture().await(10, TimeUnit.SECONDS));
	}

	private QuicStreamChannel start(boolean invalidProof) throws Exception {
		return start(invalidProof, false);
	}

	@Test
	public void triesNextBackendBeforeSetupAndClosesFailedAttempt() throws Exception {
		var game = start(false, true);
		assertTrue(received.poll(10, TimeUnit.SECONDS) instanceof ServerAuthToken);
		assertTrue(received.poll(10, TimeUnit.SECONDS) instanceof UnknownPacket);
		verified.get(10, TimeUnit.SECONDS);
		assertTrue(rejectedUpstream.get(10, TimeUnit.SECONDS).closeFuture().await(10, TimeUnit.SECONDS));
		game.writeAndFlush(new UnknownPacket(frame(1001, new byte[] { 4, 2 }))).sync();
		assertArrayEquals(frame(1001, new byte[] { 4, 2 }),
				((UnknownPacket) received.poll(10, TimeUnit.SECONDS)).bytes());
	}

	private QuicStreamChannel start(boolean invalidProof, boolean fallback) throws Exception {
		var certificate = SelfSignedCertificate.generate("Backend Test");
		var serverSsl = QuicSslContextBuilder.forServer(certificate.privateKey(), null, certificate.certificate())
			.applicationProtocols("hytale/3")
			.clientAuth(ClientAuth.REQUIRE)
			.trustManager(InsecureTrustManagerFactory.INSTANCE)
			.build();
		var serverCodec = new QuicServerCodecBuilder().sslContext(serverSsl)
			.tokenHandler(InsecureQuicTokenHandler.INSTANCE)
			.maxIdleTimeout(30, TimeUnit.SECONDS)
			.initialMaxData(1048576)
			.initialMaxStreamDataBidirectionalLocal(262144)
			.initialMaxStreamDataBidirectionalRemote(262144)
			.initialMaxStreamsBidirectional(8)
			.initialMaxStreamsUnidirectional(8)
			.handler(new ChannelInboundHandlerAdapter())
			.streamHandler(new ChannelInitializer<QuicStreamChannel>() {
				@Override
				protected void initChannel(QuicStreamChannel stream) {
					if (stream.streamId() != 0) {
						stream.pipeline().addLast(new ChannelInboundHandlerAdapter() {
							@Override
							public void channelRead(ChannelHandlerContext context, Object message) {
								context.writeAndFlush(message);
							}
						});
						return;
					}
					stream.pipeline()
						.addLast(new PacketDecoder(), new PacketEncoder(), new SimpleChannelInboundHandler<Packet>() {
							@Override
							protected void channelRead0(ChannelHandlerContext context, Packet packet) throws Exception {
								if (packet instanceof Connect connect) {
									try {
										var payload = Unpooled.buffer();
										byte[] unsigned;
										try {
											new Connect(connect.protocolCrc(), connect.protocolBuildNumber(),
													connect.clientVersion(), connect.clientType(), connect.language(),
													null, connect.referralData(), connect.referralSource())
												.serialize(payload);
											unsigned = new byte[payload.readableBytes()];
											payload.readBytes(unsigned);
										}
										finally {
											payload.release();
										}
										var peer = (X509Certificate) stream.parent()
											.sslEngine()
											.getSession()
											.getPeerCertificates()[0];
										var claims = tokens.verify(connect.identityToken(),
												ForwardingToken.fingerprint(peer),
												ForwardingToken.fingerprint(certificate.certificate()), unsigned,
												Instant.now());
										assertEquals(profile.uniqueId(), claims.identity().uuid());
										assertEquals(profile.username(), claims.identity().username());
										assertEquals(clientSocket.localAddress(), claims.identity().address());
										verified.complete(null);
										context.pipeline().get(PacketDecoder.class).authenticated();
										upstream.complete(stream.parent());
										upstreamGame.complete(stream);
										context.write(new AuthGrant(invalidProof ? "wrong-proof"
												: tokens.acknowledgement(connect.identityToken()), null));
										context.writeAndFlush(new UnknownPacket(frame(1000, new byte[] { 9, 8, 7 })));
									}
									catch (Throwable failure) {
										verified.completeExceptionally(failure);
										throw failure;
									}
								}
								else {
									context.writeAndFlush(packet);
								}
							}
						});
				}
			})
			.build();
		backendSocket = new Bootstrap().group(group)
			.channel(NioDatagramChannel.class)
			.handler(serverCodec)
			.bind(new InetSocketAddress("127.0.0.1", 0))
			.sync()
			.channel();
		var backends = new ArrayList<BackendConfiguration>();
		if (fallback) {
			var rejectingCodec = new QuicServerCodecBuilder().sslContext(serverSsl)
				.tokenHandler(InsecureQuicTokenHandler.INSTANCE)
				.maxIdleTimeout(30, TimeUnit.SECONDS)
				.initialMaxData(1048576)
				.initialMaxStreamDataBidirectionalRemote(262144)
				.initialMaxStreamsBidirectional(8)
				.handler(new ChannelInboundHandlerAdapter() {
					@Override
					public void channelActive(ChannelHandlerContext context) {
						rejectedUpstream.complete((QuicChannel) context.channel());
						context.close();
					}
				})
				.build();
			rejectingBackendSocket = new Bootstrap().group(group)
				.channel(NioDatagramChannel.class)
				.handler(rejectingCodec)
				.bind(new InetSocketAddress("127.0.0.1", 0))
				.sync()
				.channel();
			backends.add(new BackendConfiguration((InetSocketAddress) rejectingBackendSocket.localAddress(), tokens));
		}
		backends.add(new BackendConfiguration((InetSocketAddress) backendSocket.localAddress(), tokens));
		var connector = new BackendConnector(backends);
		proxy = new NettyNetworkManager(new InetSocketAddress("127.0.0.1", 0), new AuthenticationService() {
			@Override
			public CompletionStage<Grant> requestGrant(Connect connect) {
				return CompletableFuture.completedFuture(new Grant(profile, "client-grant", "proxy-identity"));
			}

			@Override
			public CompletionStage<String> authenticate(AuthToken token, AuthenticatedProfile identity, String client,
					String server) {
				return CompletableFuture.completedFuture("proxy-authenticated");
			}
		}, null, connector::connect);
		proxy.start();
		var clientCertificate = SelfSignedCertificate.generate("Client Test");
		var clientSsl = QuicSslContextBuilder.forClient()
			.applicationProtocols("hytale/3")
			.keyManager(clientCertificate.privateKey(), null, clientCertificate.certificate())
			.trustManager(InsecureTrustManagerFactory.INSTANCE)
			.build();
		var clientCodec = new QuicClientCodecBuilder().sslContext(clientSsl)
			.maxIdleTimeout(30, TimeUnit.SECONDS)
			.initialMaxData(1048576)
			.initialMaxStreamDataBidirectionalLocal(262144)
			.initialMaxStreamDataBidirectionalRemote(262144)
			.initialMaxStreamDataUnidirectional(262144)
			.initialMaxStreamsBidirectional(8)
			.initialMaxStreamsUnidirectional(8)
			.build();
		clientSocket = new Bootstrap().group(group)
			.channel(NioDatagramChannel.class)
			.handler(clientCodec)
			.bind(new InetSocketAddress("127.0.0.1", 0))
			.sync()
			.channel();
		var connection = QuicChannel.newBootstrap(clientSocket)
			.remoteAddress(proxy.boundAddress())
			.streamHandler(new ChannelInitializer<QuicStreamChannel>() {
				@Override
				protected void initChannel(QuicStreamChannel channel) {
					channel.pipeline().addLast(collectBytes());
				}
			})
			.connect()
			.get(10, TimeUnit.SECONDS);
		var game = connection.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
			@Override
			protected void initChannel(QuicStreamChannel channel) {
				channel.pipeline()
					.addLast(new PacketDecoder(), new PacketEncoder(), new SimpleChannelInboundHandler<Packet>() {
						@Override
						protected void channelRead0(ChannelHandlerContext context, Packet packet) {
							if (packet instanceof AuthGrant) {
								context.writeAndFlush(new AuthToken("client-access", "server-grant"));
							}
							else {
								if (packet instanceof ServerAuthToken)
									context.pipeline().get(PacketDecoder.class).authenticated();
								received.add(packet);
							}
						}
					});
			}
		}).get(10, TimeUnit.SECONDS);
		game.writeAndFlush(new Connect(ProtocolVersion.CURRENT.crc(), ProtocolVersion.CURRENT.buildNumber(),
				"integration-test", ClientType.GAME, "en-US", "identity", null, null))
			.sync();
		return game;
	}

	private SimpleChannelInboundHandler<ByteBuf> collectBytes() {
		return new SimpleChannelInboundHandler<>() {
			@Override
			protected void channelRead0(ChannelHandlerContext context, ByteBuf buffer) {
				var bytes = new byte[buffer.readableBytes()];
				buffer.readBytes(bytes);
				chunks.add(bytes);
			}
		};
	}

	private static byte[] frame(int id, byte[] payload) {
		return ByteBuffer.allocate(8 + payload.length)
			.order(ByteOrder.LITTLE_ENDIAN)
			.putInt(payload.length)
			.putInt(id)
			.put(payload)
			.array();
	}

}
