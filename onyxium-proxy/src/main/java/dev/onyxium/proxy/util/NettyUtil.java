package dev.onyxium.proxy.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import dev.onyxium.proxy.io.packet.VarInts;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.CorruptedFrameException;
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

    /// Moves the reader index to a field addressed through an offset table, where `varsOffset` is the
    /// absolute start of the variable-length region and `offset` is the table entry relative to it.
    ///
    /// Offsets are peer-controlled, so they are bounds-checked here rather than left to surface as an
    /// [IndexOutOfBoundsException] out of [ByteBuf#readerIndex(int)]. The addition is widened because
    /// an offset near [Integer#MAX_VALUE] would otherwise wrap into a plausible-looking index.
    ///
    /// @throws CorruptedFrameException when the offset does not name a readable byte
    public static void seekToField(ByteBuf buf, int varsOffset, int offset) {
        long absolute = (long) varsOffset + offset;

        if (offset < 0 || absolute >= buf.writerIndex()) {
            throw new CorruptedFrameException("Field offset " + offset + " points outside the packet");
        }

        buf.readerIndex((int) absolute);
    }

    public static void writeVarString(ByteBuf buf, String value, int maxLength) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxLength) {
            throw new IllegalStateException(
                    "String %s exceeds max length %d > %d".formatted(value, bytes.length, maxLength));
        }
        VarInts.write(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    public static String readVarString(ByteBuf buf, int maxLength) {
        var bytes = readVarBytes(buf, maxLength);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeVarBytes(ByteBuf buf, byte[] value, int maxLength) {
        if (value.length > maxLength) {
            throw new IllegalStateException(
                    "Byte array exceeds max length %d > %d".formatted(value.length, maxLength));
        }
        VarInts.write(buf, value.length);
        buf.writeBytes(value);
    }

    public static byte[] readVarBytes(ByteBuf buf, int maxLength) {
        int length = VarInts.read(buf);

        // A five-byte VarInt can carry the sign bit, so a peer is free to send a negative length.
        // Without this the allocation below fails with NegativeArraySizeException instead of being
        // reported as the protocol error it is.
        if (length < 0 || length > maxLength) {
            throw new CorruptedFrameException(
                    "VarInt length out of range (length=" + length + ", maxLength=" + maxLength + ")");
        }

        var bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }

    /// Writes a fixed-width ASCII field, padding the remainder with NUL bytes.
    ///
    /// Fixed-width fields carry no length prefix, so writing anything other than exactly `width`
    /// bytes shifts every field after it and corrupts the frame.
    public static void writeFixedAscii(ByteBuf buf, String value, int width) {
        var bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > width) {
            throw new IllegalStateException(
                    "String %s exceeds fixed width %d > %d".formatted(value, bytes.length, width));
        }

        buf.writeBytes(bytes);
        buf.writeZero(width - bytes.length);
    }

    /// Reads a fixed-width ASCII field and strips the NUL padding [#writeFixedAscii] adds.
    public static String readFixedAscii(ByteBuf buf, int width) {
        var bytes = new byte[width];
        buf.readBytes(bytes);

        var length = 0;
        while (length < width && bytes[length] != 0) {
            length++;
        }

        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
    }

    /// Reads a UUID as two big-endian 64-bit halves, most significant first.
    ///
    /// Big-endian is deliberate and matches Hytale: UUIDs travel in RFC 4122 byte order even though
    /// every other scalar in the protocol is little-endian.
    public static UUID readUuid(ByteBuf buf) {
        long mostSignificantBits = buf.readLong();
        long leastSignificantBits = buf.readLong();
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    /// @see #readUuid(ByteBuf)
    public static void writeUuid(ByteBuf buf, UUID value) {
        buf.writeLong(value.getMostSignificantBits());
        buf.writeLong(value.getLeastSignificantBits());
    }
}
