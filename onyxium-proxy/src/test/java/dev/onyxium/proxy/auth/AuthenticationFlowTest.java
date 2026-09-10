package dev.onyxium.proxy.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.sun.net.httpserver.HttpServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dev.onyxium.proxy.io.NettyNetworkManager;
import dev.onyxium.proxy.io.packet.ClientType;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.PacketDecoder;
import dev.onyxium.proxy.io.packet.PacketEncoder;
import dev.onyxium.proxy.io.packet.ProtocolVersion;
import dev.onyxium.proxy.io.packet.auth.AuthGrant;
import dev.onyxium.proxy.io.packet.auth.AuthToken;
import dev.onyxium.proxy.io.packet.auth.Connect;
import dev.onyxium.proxy.io.packet.auth.ServerAuthToken;
import dev.onyxium.proxy.io.packet.connection.ServerDisconnect;
import dev.onyxium.proxy.player.ProxyPlayer;
import dev.onyxium.proxy.util.SelfSignedCertificate;

public class AuthenticationFlowTest {

	private HttpServer sessions;

	private TestTokens tokens;

	private String issuer;

	private NettyNetworkManager proxy;

	private EventLoopGroup clientGroup;

	private Channel udp;

	private QuicChannel client;

	private QuicStreamChannel stream;

	private String fingerprint;

	private final LinkedBlockingQueue<Packet> received = new LinkedBlockingQueue<>();

	private final CompletableFuture<ProxyPlayer> joined = new CompletableFuture<>();

	private final AtomicReference<String> exchangedFingerprint = new AtomicReference<>();

	private final AtomicReference<Throwable> serviceFailure = new AtomicReference<>();

	@Before
	public void setup() throws Exception {
		tokens = new TestTokens("integration-key");
		sessions = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		issuer = "http://127.0.0.1:" + sessions.getAddress().getPort();
		sessions.createContext("/", exchange -> {
			try {
				var path = exchange.getRequestURI().getPath();
				String response;
				if (path.equals("/.well-known/jwks.json")) {
					response = tokens.jwks();
				}
				else {
					assertEquals("Bearer test-session", exchange.getRequestHeaders().getFirst("Authorization"));
					var body = JSONObjectUtils
						.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
					response = switch (path) {
						case "/server-join/auth-grant" -> {
							assertEquals("test-proxy", body.get("aud"));
							assertNotNull(body.get("identityToken"));
							yield JSONObjectUtils.toJSONString(Map.of("authorizationGrant", "client-grant"));
						}
						case "/server-join/auth-token" -> {
							assertEquals("server-grant", body.get("authorizationGrant"));
							exchangedFingerprint.set((String) body.get("x509Fingerprint"));
							yield JSONObjectUtils.toJSONString(Map.of("accessToken", "server-access-token"));
						}
						default -> throw new AssertionError("Unexpected path " + path);
					};
				}
				var bytes = response.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, bytes.length);
				exchange.getResponseBody().write(bytes);
			}
			catch (Throwable failure) {
				serviceFailure.set(failure);
				exchange.sendResponseHeaders(500, -1);
			}
			finally {
				exchange.close();
			}
		});
		sessions.start();
		var authentication = new HytaleAuthenticationService(
				new AuthConfiguration(URI.create(issuer), "test-proxy", "test-session", tokens.serverIdentity(issuer)));
		proxy = new NettyNetworkManager(new InetSocketAddress("127.0.0.1", 0), authentication, null, joined::complete);
		proxy.start();
		clientGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
		var certificate = SelfSignedCertificate.generate("Onyxium login test");
		fingerprint = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate.certificate().getEncoded()));
		var ssl = QuicSslContextBuilder.forClient()
			.keyManager(certificate.privateKey(), null, certificate.certificate())
			.applicationProtocols("hytale/3")
			.trustManager(InsecureTrustManagerFactory.INSTANCE)
			.build();
		var codec = new QuicClientCodecBuilder().sslContext(ssl)
			.maxIdleTimeout(10, TimeUnit.SECONDS)
			.initialMaxData(1024 * 1024)
			.initialMaxStreamDataBidirectionalLocal(128 * 1024)
			.initialMaxStreamDataBidirectionalRemote(128 * 1024)
			.initialMaxStreamsBidirectional(4)
			.build();
		udp = new Bootstrap().group(clientGroup)
			.channel(NioDatagramChannel.class)
			.handler(codec)
			.bind(0)
			.sync()
			.channel();
		client = QuicChannel.newBootstrap(udp)
			.handler(new ChannelInboundHandlerAdapter())
			.remoteAddress(proxy.boundAddress())
			.connect()
			.get(10, TimeUnit.SECONDS);
		stream = client.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
			@Override
			protected void initChannel(QuicStreamChannel channel) {
				channel.pipeline()
					.addLast(new PacketDecoder(), new PacketEncoder(), new SimpleChannelInboundHandler<Packet>() {
						@Override
						protected void channelRead0(ChannelHandlerContext context, Packet packet) {
							received.add(packet);
						}
					});
			}
		}).get(10, TimeUnit.SECONDS);
	}

	@After
	public void teardown() {
		if (udp != null) {
			udp.close().syncUninterruptibly();
		}
		if (clientGroup != null) {
			clientGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
		}
		if (proxy != null) {
			proxy.stop();
		}
		if (sessions != null) {
			sessions.stop(0);
		}
		assertNull(serviceFailure.get());
	}

	@Test
	public void completesMutualAuthenticationAndCleansUpOnStreamClose() throws Exception {
		begin();
		stream.writeAndFlush(new AuthToken(tokens.sign(tokens.access(issuer, fingerprint).build()), "server-grant"))
			.sync();
		var response = received.poll(10, TimeUnit.SECONDS);
		assertTrue(String.valueOf(response), response instanceof ServerAuthToken);
		assertEquals("server-access-token", ((ServerAuthToken) response).serverAccessToken());
		var player = joined.get(10, TimeUnit.SECONDS);
		assertEquals(TestTokens.PLAYER_ID, player.uuid());
		assertEquals("Oskar", player.username());
		assertTrue(proxy.players().player(player.uuid()).isPresent());
		var serverCertificate = client.sslEngine().getSession().getPeerCertificates()[0];
		var serverFingerprint = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(MessageDigest.getInstance("SHA-256").digest(serverCertificate.getEncoded()));
		assertEquals(serverFingerprint, exchangedFingerprint.get());
		var closed = new CompletableFuture<Void>();
		player.connection().onClose(() -> closed.complete(null));
		stream.close().sync();
		closed.get(10, TimeUnit.SECONDS);
		assertTrue(proxy.players().players().isEmpty());
	}

	@Test
	public void rejectsAccessTokenBoundToADifferentCertificate() throws Exception {
		begin();
		stream
			.writeAndFlush(
					new AuthToken(tokens.sign(tokens.access(issuer, "wrong-certificate").build()), "server-grant"))
			.sync();
		var response = received.poll(10, TimeUnit.SECONDS);
		assertTrue(String.valueOf(response), response instanceof ServerDisconnect);
		assertTrue(client.closeFuture().await(10, TimeUnit.SECONDS));
		assertTrue(proxy.players().players().isEmpty());
		assertNull(exchangedFingerprint.get());
		assertFalse(joined.isDone());
	}

	private void begin() throws Exception {
		var version = ProtocolVersion.CURRENT;
		stream
			.writeAndFlush(new Connect(version.crc(), version.buildNumber(), "test", ClientType.GAME, "en-US",
					tokens.identity(issuer), null, null))
			.sync();
		var response = received.poll(10, TimeUnit.SECONDS);
		assertTrue(String.valueOf(response), response instanceof AuthGrant);
		assertEquals("client-grant", ((AuthGrant) response).authorizationGrant());
	}

}
