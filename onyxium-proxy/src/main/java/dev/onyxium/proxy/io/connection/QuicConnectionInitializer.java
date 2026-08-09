package dev.onyxium.proxy.io.connection;

import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicChannel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class QuicConnectionInitializer extends ChannelInitializer<QuicChannel> {

    @Override
    protected void initChannel(@NotNull QuicChannel channel) {
        channel.pipeline().addLast(new QuicConnectionHandler());
    }
}
