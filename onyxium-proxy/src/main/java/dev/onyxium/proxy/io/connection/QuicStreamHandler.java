package dev.onyxium.proxy.io.connection;

import dev.onyxium.proxy.api.network.NetworkChannel;
import dev.onyxium.proxy.io.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.quic.QuicStreamChannel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiStatus.Internal
public final class QuicStreamHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuicStreamHandler.class);

    @Override
    public void channelActive(@NotNull ChannelHandlerContext context) {
        var streamChannel = (QuicStreamChannel) context.channel();
        var connection = HytaleProtocolConnection.of(streamChannel);

        if (connection == null) {
            context.close();
            return;
        }

        var networkChannel = NetworkChannel.fromStreamId(streamChannel.streamId());
        if (networkChannel == null) {
            LOGGER.debug("Ignoring stream {} on {}: no known channel", streamChannel.streamId(), connection);
            context.close();

            return;
        }

        LOGGER.debug("Opened {} stream {} on {}", networkChannel, streamChannel.streamId(), connection);
        context.fireChannelActive();
    }

    @Override
    protected void channelRead0(@NotNull ChannelHandlerContext context, @NotNull Packet message) {
        // TODO: decode Hytale packets once the protocol layer exists
        LOGGER.trace("Discarding {} on stream {}",
                message.toString(), ((QuicStreamChannel) context.channel()).streamId());
    }

    @Override
    public void exceptionCaught(@NotNull ChannelHandlerContext context, @NotNull Throwable cause) {
        LOGGER.debug("Closing stream {} after an unhandled error",
                ((QuicStreamChannel) context.channel()).streamId(), cause);
        context.close();
    }
}
