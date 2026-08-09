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

@ApiStatus.Internal
public final class QuicConnectionHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuicConnectionHandler.class);

    private static final int UNAUTHENTICATED_ERROR_CODE = 0;

    @Override
    public void userEventTriggered(@NotNull ChannelHandlerContext context, @NotNull Object event) {
        if (event instanceof SslHandshakeCompletionEvent handshake) {
            if (handshake.isSuccess()) {
                onHandshakeCompleted(context);
            } else {
                // The same cause also travels down the pipeline as an exception, so leave the
                // logging to exceptionCaught rather than reporting it twice.
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
        //TODO: Fire ServerDisconnect
        context.close();
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

    private static void onHandshakeCompleted(@NotNull ChannelHandlerContext context) {
        var connection = HytaleProtocolConnection.attach((QuicChannel) context.channel());
        if (connection == null) {
            // Should be unreachable: ClientAuth.REQUIRE fails the handshake when no certificate is
            // presented. Fail closed anyway rather than serving an unidentified client.
            LOGGER.warn("Rejecting {}: handshake completed without a usable client certificate",
                    context.channel().remoteAddress());
            ((QuicChannel) context.channel()).close(true, UNAUTHENTICATED_ERROR_CODE, context.alloc().buffer(0));

            return;
        }

        LOGGER.debug("Connected {}", connection);
    }
}
