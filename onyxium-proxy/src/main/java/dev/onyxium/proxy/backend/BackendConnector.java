package dev.onyxium.proxy.backend;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.onyxium.command.CommandDispatcher;
import dev.onyxium.forwarding.ForwardedIdentity;
import dev.onyxium.forwarding.ForwardingToken;
import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.io.QuicTransportParameters;
import dev.onyxium.proxy.io.connection.DisconnectErrorCode;
import dev.onyxium.proxy.io.connection.HytaleProtocolConnection;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.PacketDecoder;
import dev.onyxium.proxy.io.packet.PacketEncoder;
import dev.onyxium.proxy.io.packet.auth.AuthGrant;
import dev.onyxium.proxy.io.packet.auth.Connect;
import dev.onyxium.proxy.io.packet.connection.ServerDisconnect;
import dev.onyxium.proxy.io.packet.handler.AuthenticatedPacketHandler;
import dev.onyxium.proxy.io.packet.handler.ForwardingPacketHandler;
import dev.onyxium.proxy.player.ProxyPlayer;
import dev.onyxium.proxy.util.SelfSignedCertificate;

/// One upstream QUIC connection per authenticated player. Its UDP socket shares the downstream event loop.
public final class BackendConnector {

	private static final Logger LOGGER = LoggerFactory.getLogger(BackendConnector.class);

	private final List<BackendConfiguration> configurations;

	private final QuicSslContext ssl;

	private final String clientFingerprint;

	public BackendConnector(BackendConfiguration configuration) throws GeneralSecurityException {
		this(List.of(configuration));
	}

	public BackendConnector(List<BackendConfiguration> configurations) throws GeneralSecurityException {
		if (configurations.isEmpty())
			throw new IllegalArgumentException("At least one initial backend is required");
		this.configurations = List.copyOf(configurations);
		var certificate = SelfSignedCertificate.generate("Onyxium Forwarding");
		clientFingerprint = ForwardingToken.fingerprint(certificate.certificate());
		ssl = QuicSslContextBuilder.forClient()
			.keyManager(certificate.privateKey(), null, certificate.certificate())
			.trustManager(InsecureTrustManagerFactory.INSTANCE)
			.applicationProtocols("hytale/3")
			.earlyData(false)
			.build();
	}

	public void connect(ProxyPlayer player, CommandDispatcher commands) {
		if (!(player.connection() instanceof HytaleProtocolConnection connection)) {
			throw new IllegalArgumentException("Backend forwarding requires a QUIC connection");
		}
		if (!connection.eventLoop().inEventLoop())
			throw new IllegalStateException("Not on connection event loop");
		new Session(player, connection, commands, 0).connect();
	}

	private final class Session {

		private final ProxyPlayer player;

		private final HytaleProtocolConnection client;

		CommandDispatcher commands;

		private final int index;

		private final BackendConfiguration configuration;

		private Future<?> timeout;

		private final List<QuicStreamChannel> pendingServerStreams = new ArrayList<>();

		private final List<QuicStreamChannel> pendingClientStreams = new ArrayList<>();

		private Channel datagram;

		private QuicChannel backend;

		private QuicStreamChannel game;

		private String token;

		private boolean ready;

		private boolean closed;

		Session(ProxyPlayer player, HytaleProtocolConnection client, CommandDispatcher commands, int index) {
			this.player = player;
			this.client = client;
			this.commands = commands;
			this.index = index;
			configuration = configurations.get(index);
		}

		void connect() {
			client.onClose(this::close);
			client.auxiliaryStreams(this::clientStream);
			timeout = client.eventLoop().schedule(() -> fail("Backend connection timed out."), 15, TimeUnit.SECONDS);
			var codec = new QuicClientCodecBuilder().sslContext(ssl)
				.maxIdleTimeout(30, TimeUnit.SECONDS)
				.initialMaxData(QuicTransportParameters.CONNECTION_BUFFER_SIZE)
				.initialMaxStreamDataBidirectionalLocal(QuicTransportParameters.STREAM_BUFFER_SIZE)
				.initialMaxStreamDataBidirectionalRemote(QuicTransportParameters.STREAM_BUFFER_SIZE)
				.initialMaxStreamDataUnidirectional(QuicTransportParameters.STREAM_BUFFER_SIZE)
				.initialMaxStreamsBidirectional(8)
				.initialMaxStreamsUnidirectional(8)
				.build();
			new Bootstrap().group(client.channel().eventLoop())
				.channel(NioDatagramChannel.class)
				.handler(codec)
				.bind(0)
				.addListener(bound -> {
					if (!bound.isSuccess()) {
						fail("Could not open backend transport.");
						return;
					}
					datagram = ((ChannelFuture) bound).channel();
					if (closed || !client.active()) {
						close();
						return;
					}
					QuicChannel.newBootstrap(datagram).handler(new ChannelInboundHandlerAdapter() {
						@Override
						public void channelInactive(ChannelHandlerContext context) {
							fail("Backend disconnected.");
							context.fireChannelInactive();
						}

						@Override
						public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
							fail("Backend transport failed.");
						}
					}).streamHandler(new ChannelInitializer<QuicStreamChannel>() {
						@Override
						protected void initChannel(QuicStreamChannel stream) {
							serverStream(stream);
						}
					}).remoteAddress(configuration.address()).connect().addListener(connected -> {
						if (!connected.isSuccess()) {
							fail("Could not connect to backend.");
							return;
						}
						backend = (QuicChannel) connected.getNow();
						if (closed || !client.active()) {
							close();
							return;
						}
						backend.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
							@Override
							protected void initChannel(QuicStreamChannel stream) {
								var decoder = new PacketDecoder();
								stream.pipeline().addLast(decoder, new PacketEncoder(), new BackendPackets(timeout));
							}
						}).addListener(created -> {
							if (!created.isSuccess()) {
								fail("Could not open backend game stream.");
								return;
							}
							game = (QuicStreamChannel) created.getNow();
							if (closed || !client.active()) {
								close();
								return;
							}
							try {
								sendConnect();
							}
							catch (Exception exception) {
								fail("Could not forward player identity.");
							}
						});
					});
				});
		}

		void sendConnect() throws Exception {
			var unsigned = new Connect(player.protocolVersion().crc(), player.protocolVersion().buildNumber(),
					player.clientVersion(), player.clientType(), player.language(), null, player.referralData(),
					player.referralSource());
			var payload = Unpooled.buffer();
			try {
				unsigned.serialize(payload);
				var bytes = new byte[payload.readableBytes()];
				payload.readBytes(bytes);
				var serverCertificate = (X509Certificate) backend.sslEngine().getSession().getPeerCertificates()[0];
				token = configuration.tokens()
					.sign(new ForwardedIdentity(player.uuid(), player.username(), player.profile().skin(),
							(InetSocketAddress) player.remoteAddress()), clientFingerprint,
							ForwardingToken.fingerprint(serverCertificate), bytes, Instant.now());
			}
			finally {
				payload.release();
			}
			game.writeAndFlush(new Connect(unsigned.protocolCrc(), unsigned.protocolBuildNumber(),
					unsigned.clientVersion(), unsigned.clientType(), unsigned.language(), token,
					unsigned.referralData(), unsigned.referralSource()))
				.addListener(result -> {
					if (!result.isSuccess())
						fail("Failed to send backend handshake.");
				});
		}

		void clientStream(QuicStreamChannel stream) {
			stream.config().setAutoRead(false);
			if (closed || stream.type() != QuicStreamType.BIDIRECTIONAL || pendingClientStreams.size() >= 4) {
				stream.close();
			}
			else if (ready) {
				bridge(stream, backend);
			}
			else {
				pendingClientStreams.add(stream);
			}
		}

		void serverStream(QuicStreamChannel stream) {
			stream.config().setAutoRead(false);
			if (closed || pendingServerStreams.size() >= 8) {
				stream.close();
			}
			else if (ready) {
				bridge(stream, client.channel());
			}
			else {
				pendingServerStreams.add(stream);
			}
		}

		void bridge(QuicStreamChannel source, QuicChannel destination) {
			destination.createStream(source.type(), new ChannelInitializer<QuicStreamChannel>() {
				@Override
				protected void initChannel(QuicStreamChannel target) {
					target.config().setAutoRead(false);
				}
			}).addListener(result -> {
				if (!result.isSuccess()) {
					fail("Could not forward backend stream.");
					return;
				}
				var target = (QuicStreamChannel) result.getNow();
				if (closed || !source.isActive()) {
					target.close();
					return;
				}
				source.pipeline().addLast(new StreamRelay(target, () -> fail("Forwarded stream failed.")));
				target.pipeline().addLast(new StreamRelay(source, () -> fail("Forwarded stream failed.")));
				source.config().setAutoRead(target.isWritable());
				if (source.type() == QuicStreamType.BIDIRECTIONAL)
					target.config().setAutoRead(source.isWritable());
			});
		}

		void fail(String message) {
			if (closed)
				return;
			LOGGER.debug("Backend connection for {}: {}", player, message);
			if (!ready && client.active() && index + 1 < configurations.size()) {
				var next = new Session(player, client, commands, index + 1);
				next.pendingClientStreams.addAll(pendingClientStreams);
				pendingClientStreams.clear();
				close();
				client.setPacketHandler(new AuthenticatedPacketHandler(client));
				next.connect();
				return;
			}
			close();
			client.disconnect(FormattedMessage.text(message), DisconnectErrorCode.NO_ERROR);
		}

		/// Also runs for late bind/connect completions so closing a client cannot leak a
		/// newly opened upstream socket.
		void close() {
			closed = true;
			if (timeout != null)
				timeout.cancel(false);
			if (backend != null)
				backend.close();
			if (datagram != null)
				datagram.close();
			pendingClientStreams.forEach(Channel::close);
			pendingServerStreams.forEach(Channel::close);
			pendingClientStreams.clear();
			pendingServerStreams.clear();
		}

		private final class BackendPackets extends SimpleChannelInboundHandler<Packet> {

			private final Future<?> timeout;

			BackendPackets(Future<?> timeout) {
				this.timeout = timeout;
			}

			@Override
			protected void channelRead0(ChannelHandlerContext context, Packet packet) {
				if (closed)
					return;
				if (packet instanceof ServerDisconnect(FormattedMessage reason, boolean crash)) {
					close();
					client.disconnect(
							Objects.requireNonNullElse(reason, FormattedMessage.text("Backend disconnected.")),
							crash ? DisconnectErrorCode.CRASH : DisconnectErrorCode.NO_ERROR);
					return;
				}
				if (!ready) {
					if (!(packet instanceof AuthGrant grant) || token == null
							|| !configuration.tokens().verifyAcknowledgement(token, grant.authorizationGrant())) {
						fail("Backend did not accept Onyxium forwarding.");
						return;
					}
					ready = true;
					context.pipeline().get(PacketDecoder.class).authenticated();
					timeout.cancel(false);
					client.setPacketHandler(new ForwardingPacketHandler(player, commands, game, Session.this::fail));
					client.onWritabilityChanged(() -> game.config().setAutoRead(client.gameStreamWritable()));
					pendingServerStreams.forEach(stream -> bridge(stream, client.channel()));
					pendingClientStreams.forEach(stream -> bridge(stream, backend));
					pendingServerStreams.clear();
					pendingClientStreams.clear();
					LOGGER.info("Connected {} to backend {}", player, configuration.address());
					return;
				}
				if (packet.id() == 0 || packet.id() >= 11 && packet.id() <= 17) {
					fail("Unexpected backend authentication packet.");
					return;
				}
				client.write(packet);
				context.channel().config().setAutoRead(client.gameStreamWritable());
			}

			@Override
			public void channelWritabilityChanged(ChannelHandlerContext context) {
				if (ready)
					client.readEnabled(context.channel().isWritable());
				context.fireChannelWritabilityChanged();
			}

			@Override
			public void userEventTriggered(ChannelHandlerContext context, Object event) {
				if (event instanceof ChannelInputShutdownEvent || event instanceof ChannelOutputShutdownEvent) {
					fail("Backend game stream closed.");
				}
				context.fireUserEventTriggered(event);
			}

			@Override
			public void channelInactive(ChannelHandlerContext context) {
				fail("Backend game stream closed.");
			}

			@Override
			public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
				fail("Invalid backend packet.");
			}

		}

	}

}
