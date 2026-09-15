package dev.onyxium.proxy.io.connection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.handler.codec.quic.QuicStreamChannel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.io.packet.Packet;

public final class QuicStreamHandler extends SimpleChannelInboundHandler<Packet> {

	private static final Logger LOGGER = LoggerFactory.getLogger(QuicStreamHandler.class);

	private HytaleProtocolConnection connection;

	private boolean gameStream;

	@Override
	public void channelActive(@NotNull ChannelHandlerContext context) {
		var stream = (QuicStreamChannel) context.channel();
		connection = HytaleProtocolConnection.of(stream);
		gameStream = connection != null && connection.openGameStream(stream);
		if (!gameStream) {
			context.close();
			return;
		}
		context.fireChannelActive();
	}

	@Override
	protected void channelRead0(@NotNull ChannelHandlerContext context, @NotNull Packet packet) {
		if (gameStream) {
			connection.receive(packet);
		}
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext context) {
		if (gameStream)
			connection.writabilityChanged();
		context.fireChannelWritabilityChanged();
	}

	@Override
	public void channelInactive(@NotNull ChannelHandlerContext context) {
		if (gameStream && connection.active()) {
			connection.close();
		}
		context.fireChannelInactive();
	}

	@Override
	public void userEventTriggered(@NotNull ChannelHandlerContext context, @NotNull Object event) {
		if (gameStream && (event instanceof ChannelInputShutdownEvent || event instanceof ChannelOutputShutdownEvent)
				&& connection.active()) {
			connection.close();
		}
		context.fireUserEventTriggered(event);
	}

	@Override
	public void exceptionCaught(@NotNull ChannelHandlerContext context, @NotNull Throwable cause) {
		LOGGER.debug("Packet error from {} ({})", connection, cause.getClass().getSimpleName());
		if (gameStream) {
			connection.disconnect(FormattedMessage.text("Malformed or unexpected packet."),
					DisconnectErrorCode.AUTH_FAILED);
		}
		else {
			context.close();
		}
	}

}
