package dev.onyxium.proxy.io.connection;

import java.util.Objects;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.api.network.NetworkInfo;
import dev.onyxium.proxy.io.packet.handler.HandshakePacketHandler;
import dev.onyxium.proxy.io.packet.handler.LoginContext;

@ApiStatus.Internal
public final class QuicConnectionHandler extends ChannelInboundHandlerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(QuicConnectionHandler.class);

	private final LoginContext login;

	public QuicConnectionHandler(LoginContext login) {
		this.login = login;
	}

	@Override
	public void userEventTriggered(@NotNull ChannelHandlerContext context, @NotNull Object event) {
		if (event instanceof SslHandshakeCompletionEvent handshake) {
			if (handshake.isSuccess()) {
				onHandshakeCompleted(context);
			}
			else {
				context.close();
			}
		}

		context.fireUserEventTriggered(event);
	}

	@Override
	public void channelInactive(@NotNull ChannelHandlerContext context) {
		var connection = HytaleProtocolConnection.of(context.channel());
		if (connection != null) {
			LOGGER.debug("Disconnected {}", connection);
		}

		context.fireChannelInactive();
	}

	@Override
	public void exceptionCaught(@NotNull ChannelHandlerContext context, @NotNull Throwable cause) {
		LOGGER.debug("Closing connection to {} after an unhandled error", describe(context), cause);
		var connection = HytaleProtocolConnection.of(context.channel());
		if (connection != null) {
			connection.disconnect(FormattedMessage.text("Connection error."), DisconnectErrorCode.CRASH);
		}
		else {
			context.close();
		}
	}

	@NotNull
	private static Object describe(@NotNull ChannelHandlerContext context) {
		var connection = HytaleProtocolConnection.of(context.channel());
		if (connection != null) {
			return connection;
		}

		var quicChannel = (QuicChannel) context.channel();
		return Objects.requireNonNullElse(quicChannel.remoteSocketAddress(), quicChannel);
	}

	private void onHandshakeCompleted(@NotNull ChannelHandlerContext context) {
		var connection = HytaleProtocolConnection.attach((QuicChannel) context.channel());
		if (connection == null) {
			LOGGER.warn("Rejecting {}: handshake completed without a usable client certificate",
					context.channel().remoteAddress());
			((QuicChannel) context.channel()).close(true, DisconnectErrorCode.AUTH_FAILED.code(),
					context.alloc().buffer(0));

			return;
		}

		if (!connection.applicationProtocol().equals("hytale/" + NetworkInfo.PROTOCOL_VERSION)) {
			connection.disconnect(FormattedMessage.text("Unsupported Hytale protocol version."),
					DisconnectErrorCode.INVALID_VERSION);
			return;
		}
		connection.setPacketHandler(new HandshakePacketHandler(connection, login));
		LOGGER.debug("Handshake complete for connection: {}", connection);
	}

}
