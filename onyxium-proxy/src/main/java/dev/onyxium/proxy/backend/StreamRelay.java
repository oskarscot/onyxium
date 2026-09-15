package dev.onyxium.proxy.backend;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;

/// Relays complete byte streams without decoding or recompressing Hytale packets.
/// Both channels share an event loop; a slow receiver pauses reads at the sender.
public final class StreamRelay extends ChannelInboundHandlerAdapter {

	private final Channel peer;

	private final Runnable failed;

	public StreamRelay(Channel peer, Runnable failed) {
		this.peer = peer;
		this.failed = failed;
	}

	@Override
	public void channelRead(ChannelHandlerContext context, Object message) {
		if (!peer.isActive()) {
			ReferenceCountUtil.release(message);
			failed.run();
			return;
		}
		peer.writeAndFlush(message).addListener(result -> {
			if (!result.isSuccess())
				failed.run();
		});
		context.channel().config().setAutoRead(peer.isWritable());
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext context) {
		peer.config().setAutoRead(context.channel().isWritable());
		context.fireChannelWritabilityChanged();
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext context, Object event) {
		if (event instanceof ChannelInputShutdownEvent && peer instanceof QuicStreamChannel stream) {
			stream.shutdownOutput();
		}
		context.fireUserEventTriggered(event);
	}

	@Override
	public void channelInactive(ChannelHandlerContext context) {
		peer.close();
		context.fireChannelInactive();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
		failed.run();
	}

}
