package dev.onyxium.proxy.util;

import java.nio.charset.StandardCharsets;

import dev.onyxium.proxy.io.packet.VarInts;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicTransportError;

public final class NettyUtil {

    private NettyUtil() {
    }

    public static void closeConnection(Channel channel) {
        int errorCode = (int) QuicTransportError.PROTOCOL_VIOLATION.code();
        if (channel instanceof QuicChannel quicChannel) {
            quicChannel.close(false, errorCode, Unpooled.EMPTY_BUFFER);
        } else if (channel.parent() instanceof QuicChannel quicChannel) {
            quicChannel.close(false, errorCode, Unpooled.EMPTY_BUFFER);
        } else {
            channel.close();
        }
    }

    public static void writeVarString(ByteBuf buf, String value, int maxLength) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxLength) {
            throw new IllegalStateException(
                    "String %s exceeds max length %i > %i".formatted(value, bytes.length, maxLength));
        }
        VarInts.write(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    public static String readVarString(ByteBuf buf, int maxLength) {
        int length = VarInts.read(buf);
        if(length > maxLength) {
            throw new IllegalStateException("varint length too large (length=" + length + ", maxLength=" + maxLength + ")");
        }

        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
