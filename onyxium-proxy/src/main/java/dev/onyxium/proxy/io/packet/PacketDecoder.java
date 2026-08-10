package dev.onyxium.proxy.io.packet;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class PacketDecoder extends ByteToMessageDecoder {

    /// make sure it matches Hytale's generated protocol!
    private static final int MAX_PACKET_SIZE = 1677721600;

    /// [length 4 bytes LE][packedId 4 bytes LE][payload]
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 8) {
            return;
        }

        var originalIndex = in.readerIndex();
        var payloadLength = in.readIntLE();

        if (payloadLength < 0 || payloadLength > MAX_PACKET_SIZE) {
            in.skipBytes(in.readableBytes());
            // TODO: close the stream
            return;
        }

        var packetId = in.readIntLE();

        if (in.readableBytes() < payloadLength) {
            in.readerIndex(originalIndex);
            return;
        }

        var payload = in.readRetainedSlice(payloadLength);
        try {
            var decoded = KnownPacket.decode(packetId, payload);
            if (decoded == null) {
                var frame = new byte[8 + payloadLength];
                in.getBytes(originalIndex, frame);
                out.add(new UnknownPacket(packetId, frame));
            } else {
                out.add(decoded);
            }
        } finally {
            payload.release();
        }
    }

}
