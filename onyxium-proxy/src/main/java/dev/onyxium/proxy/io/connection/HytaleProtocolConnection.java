package dev.onyxium.proxy.io.connection;

import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.io.packet.FormattedMessageCodec;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.PacketDecoder;
import dev.onyxium.proxy.io.packet.connection.ServerDisconnect;
import dev.onyxium.proxy.io.packet.handler.GenericPacketHandler;

@ApiStatus.Internal
public final class HytaleProtocolConnection implements ProtocolConnection {

	private static final AttributeKey<HytaleProtocolConnection> ATTRIBUTE = AttributeKey
		.valueOf(HytaleProtocolConnection.class, "connection");

	private final QuicChannel channel;

	private final SocketAddress remoteAddress;

	private final String applicationProtocol;

	private final X509Certificate clientCertificate;

	private final String certificateFingerprint;

	private final String serverCertificateFingerprint;

	private QuicStreamChannel gameStream;

	private GenericPacketHandler packetHandler;

	private volatile boolean closing;

	private Consumer<QuicStreamChannel> auxiliaryStreams;

	private Runnable writabilityChanged = () -> {
	};

	public void auxiliaryStreams(Consumer<QuicStreamChannel> handler) {
		requireEventLoop();
		auxiliaryStreams = handler;
	}

	public void openAuxiliaryStream(QuicStreamChannel stream) {
		requireEventLoop();
		if (active() && auxiliaryStreams != null)
			auxiliaryStreams.accept(stream);
		else
			stream.close();
	}

	public void readEnabled(boolean enabled) {
		requireEventLoop();
		if (gameStream != null)
			gameStream.config().setAutoRead(enabled);
	}

	public boolean gameStreamWritable() {
		return active() && gameStream != null && gameStream.isWritable();
	}

	public void onWritabilityChanged(Runnable listener) {
		requireEventLoop();
		writabilityChanged = Objects.requireNonNull(listener);
	}

	void writabilityChanged() {
		writabilityChanged.run();
	}

	private HytaleProtocolConnection(QuicChannel channel, String applicationProtocol, X509Certificate clientCertificate,
			String serverFingerprint) {
		this.channel = channel;
		remoteAddress = channel.remoteSocketAddress();
		this.applicationProtocol = applicationProtocol;
		this.clientCertificate = clientCertificate;
		certificateFingerprint = fingerprint(clientCertificate);
		serverCertificateFingerprint = serverFingerprint;
	}

	/// Captures both certificates from the actual TLS session, binding mutual
	/// authentication
	/// to the certificates used on this connection rather than independently generated
	/// keys.
	@Nullable
	static HytaleProtocolConnection attach(@NotNull QuicChannel channel) {
		var engine = channel.sslEngine();
		if (engine == null) {
			return null;
		}
		var session = engine.getSession();
		var peer = peerCertificate(session);
		var local = session.getLocalCertificates();
		if (peer == null || local == null || local.length == 0 || !(local[0] instanceof X509Certificate server)) {
			return null;
		}
		var connection = new HytaleProtocolConnection(channel,
				Objects.requireNonNullElse(engine.getApplicationProtocol(), ""), peer, fingerprint(server));
		channel.attr(ATTRIBUTE).set(connection);
		channel.closeFuture().addListener(ignored -> connection.closed());
		return connection;
	}

	@Nullable
	public static HytaleProtocolConnection of(@NotNull Channel channel) {
		var owner = channel instanceof QuicStreamChannel stream ? stream.parent() : channel;
		return owner == null ? null : owner.attr(ATTRIBUTE).get();
	}

	@Nullable
	private static X509Certificate peerCertificate(SSLSession session) {
		Certificate[] certificates;
		try {
			certificates = session.getPeerCertificates();
		}
		catch (SSLPeerUnverifiedException exception) {
			return null;
		}
		return certificates.length > 0 && certificates[0] instanceof X509Certificate certificate ? certificate : null;
	}

	private static String fingerprint(X509Certificate certificate) {
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException | CertificateEncodingException exception) {
			throw new IllegalStateException("Cannot fingerprint TLS certificate", exception);
		}
	}

	public boolean openGameStream(QuicStreamChannel stream) {
		requireEventLoop();
		if (!active() || stream.streamId() != 0 || gameStream != null) {
			return false;
		}
		gameStream = stream;
		return true;
	}

	public void receive(Packet packet) {
		requireEventLoop();
		if (active() && packetHandler != null) {
			packetHandler.accept(packet);
		}
	}

	@Override
	public void setPacketHandler(GenericPacketHandler handler) {
		requireEventLoop();
		Objects.requireNonNull(handler);
		if (!active()) {
			return;
		}
		if (packetHandler != null) {
			packetHandler.deactivated();
		}
		packetHandler = handler;
		handler.activated();
	}

	@Override
	public void write(Packet packet) {
		requireEventLoop();
		if (!active()) {
			return;
		}
		if (gameStream == null || !gameStream.isActive()) {
			close();
			return;
		}
		gameStream.writeAndFlush(packet).addListener(result -> {
			if (!result.isSuccess()) {
				close();
			}
		});
	}

	@Override
	public void disconnect(FormattedMessage reason, DisconnectErrorCode errorCode) {
		requireEventLoop();
		Objects.requireNonNull(reason);
		Objects.requireNonNull(errorCode);
		if (!active()) {
			return;
		}
		closing = true;
		deactivateHandler();
		if (gameStream == null || !gameStream.isActive()) {
			closeTransport(reason, errorCode);
			return;
		}
		var fallback = eventLoop().schedule(() -> closeTransport(reason, errorCode), 1, TimeUnit.SECONDS);
		gameStream.writeAndFlush(new ServerDisconnect(reason, errorCode == DisconnectErrorCode.CRASH))
			.addListener(ignored -> {
				fallback.cancel(false);
				closeTransport(reason, errorCode);
			});
	}

	private void closeTransport(FormattedMessage reason, DisconnectErrorCode errorCode) {
		if (!channel.isActive()) {
			return;
		}
		var bytes = channel.alloc().buffer();
		try {
			FormattedMessageCodec.serialize(bytes, reason);
			if (bytes.readableBytes() > 1024) {
				bytes.clear();
				FormattedMessageCodec.serialize(bytes, FormattedMessage.text("Disconnected from proxy."));
			}
		}
		catch (RuntimeException exception) {
			bytes.release();
			channel.close(true, errorCode.code(), Unpooled.EMPTY_BUFFER);
			return;
		}
		channel.close(true, errorCode.code(), bytes);
	}

	@Override
	public void close() {
		requireEventLoop();
		closing = true;
		deactivateHandler();
		channel.close(true, DisconnectErrorCode.NO_ERROR.code(), Unpooled.EMPTY_BUFFER);
	}

	private void closed() {
		closing = true;
		deactivateHandler();
	}

	private void deactivateHandler() {
		var previous = packetHandler;
		packetHandler = null;
		if (previous != null) {
			previous.deactivated();
		}
	}

	private void requireEventLoop() {
		if (!eventLoop().inEventLoop()) {
			throw new IllegalStateException("Connection mutation outside its event loop");
		}
	}

	@Override
	public void onClose(Runnable listener) {
		channel.closeFuture().addListener(ignored -> listener.run());
	}

	@Override
	public void authenticated() {
		requireEventLoop();
		if (gameStream != null) {
			gameStream.pipeline().get(PacketDecoder.class).authenticated();
		}
	}

	@Override
	public EventExecutor eventLoop() {
		return channel.eventLoop();
	}

	@Override
	public boolean active() {
		return !closing && channel.isActive();
	}

	public QuicChannel channel() {
		return channel;
	}

	@Override
	public SocketAddress remoteAddress() {
		return remoteAddress;
	}

	public String applicationProtocol() {
		return applicationProtocol;
	}

	public X509Certificate clientCertificate() {
		return clientCertificate;
	}

	@Override
	public String certificateFingerprint() {
		return certificateFingerprint;
	}

	@Override
	public String serverCertificateFingerprint() {
		return serverCertificateFingerprint;
	}

	@Override
	public String toString() {
		return "HytaleProtocolConnection[remoteAddress=" + remoteAddress + ", applicationProtocol="
				+ applicationProtocol + "]";
	}

}
