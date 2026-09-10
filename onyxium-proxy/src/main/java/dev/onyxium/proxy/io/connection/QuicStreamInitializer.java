package dev.onyxium.proxy.io.connection;

import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicStreamChannel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import dev.onyxium.proxy.io.packet.PacketDecoder;
import dev.onyxium.proxy.io.packet.PacketEncoder;

@ApiStatus.Internal
public final class QuicStreamInitializer extends ChannelInitializer<QuicStreamChannel> {

	@Override
	protected void initChannel(@NotNull QuicStreamChannel channel) {
		channel.pipeline().addLast("decoder", new PacketDecoder());
		channel.pipeline().addLast("encoder", new PacketEncoder());
		channel.pipeline().addLast(new QuicStreamHandler());
	}

}
