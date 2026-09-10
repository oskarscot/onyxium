package dev.onyxium.proxy.io.packet;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// GENERATED!
/// LEB128-style variable-length 32-bit integers, used for the length prefixes of
/// strings and byte arrays.
///
/// Each byte carries seven bits of value plus a continuation bit in the high position; groups are
/// emitted least-significant first. Values below 128 therefore cost a single byte, at the cost of a
/// 32-bit value needing up to five.
///
/// Both absolute and cursor-relative accessors are provided. The absolute forms exist because
/// Hytale addresses variable-length fields through a table of offsets rather than reading them in
/// sequence, so the reader index cannot be relied on to be in the right place.
@ApiStatus.Internal
public final class VarInts {

	/// Seven value bits per byte means five bytes are enough for 32, and a sixth would be
	/// The cap is what stops a peer stalling the decoder with an endless run of
	/// continuation bytes.
	public static final int MAX_BYTES = 5;

	private static final int VALUE_MASK = 0x7F;

	private static final int CONTINUATION_BIT = 0x80;

	private static final int BITS_PER_BYTE = 7;

	private VarInts() {
	}

	/// Reads a VarInt at the reader index and advances past it.
	///
	/// @throws CorruptedFrameException when the buffer ends mid-value, or the value
	/// exceeds
	/// [#MAX_BYTES] bytes
	public static int read(@NotNull ByteBuf buf) {
		var value = 0;

		for (var i = 0; i < MAX_BYTES; i++) {
			if (!buf.isReadable()) {
				throw new CorruptedFrameException("Buffer ended after " + i + " byte(s) of a VarInt");
			}

			int current = buf.readByte();
			if (i == MAX_BYTES - 1 && (current & 0xF0) != 0) {
				throw new CorruptedFrameException("VarInt overflows 32 bits");
			}
			value |= (current & VALUE_MASK) << (i * BITS_PER_BYTE);

			if ((current & CONTINUATION_BIT) == 0) {
				return value;
			}
		}

		throw new CorruptedFrameException("VarInt exceeds " + MAX_BYTES + " bytes");
	}

	/// Reads the VarInt at an absolute index without moving the reader index.
	///
	/// @throws CorruptedFrameException when the readable region ends mid-value, or the
	/// value
	/// exceeds [#MAX_BYTES] bytes
	public static int peek(@NotNull ByteBuf buf, int index) {
		var value = 0;

		for (var i = 0; i < MAX_BYTES; i++) {
			int current = byteAt(buf, index + i, i);
			if (i == MAX_BYTES - 1 && (current & 0xF0) != 0) {
				throw new CorruptedFrameException("VarInt overflows 32 bits");
			}
			value |= (current & VALUE_MASK) << (i * BITS_PER_BYTE);

			if ((current & CONTINUATION_BIT) == 0) {
				return value;
			}
		}

		throw new CorruptedFrameException("VarInt exceeds " + MAX_BYTES + " bytes");
	}

	/// Returns how many bytes the VarInt at an absolute index occupies, so callers
	/// reading through
	/// an offset table know how far to skip. Does not move the reader index.
	///
	/// @throws CorruptedFrameException when the readable region ends mid-value, or the
	/// value
	/// exceeds [#MAX_BYTES] bytes
	public static int length(@NotNull ByteBuf buf, int index) {
		for (var i = 0; i < MAX_BYTES; i++) {
			if (i == MAX_BYTES - 1 && (byteAt(buf, index + i, i) & 0xF0) != 0) {
				throw new CorruptedFrameException("VarInt overflows 32 bits");
			}
			if ((byteAt(buf, index + i, i) & CONTINUATION_BIT) == 0) {
				return i + 1;
			}
		}

		throw new CorruptedFrameException("VarInt exceeds " + MAX_BYTES + " bytes");
	}

	/// Writes a VarInt at the writer index and advances past it.
	///
	/// Negative values always occupy [#MAX_BYTES] bytes, since two's complement sets the
	/// high bits.
	/// Hytale only uses VarInts for lengths and counts, so a negative here signals a bug
	/// upstream
	/// rather than something worth encoding compactly.
	public static void write(@NotNull ByteBuf buf, int value) {
		while ((value & ~VALUE_MASK) != 0) {
			buf.writeByte((value & VALUE_MASK) | CONTINUATION_BIT);
			value >>>= BITS_PER_BYTE;
		}

		buf.writeByte(value);
	}

	/// Returns how many bytes [#write(ByteBuf, int)] would use for this value, for sizing
	/// a buffer
	/// or reserving space ahead of time.
	public static int sizeOf(int value) {
		for (var i = 1; i < MAX_BYTES; i++) {
			if ((value & (0xFFFFFFFF << (i * BITS_PER_BYTE))) == 0) {
				return i;
			}
		}

		return MAX_BYTES;
	}

	private static int byteAt(@NotNull ByteBuf buf, int index, int consumed) {
		if (index >= buf.writerIndex()) {
			throw new CorruptedFrameException("Buffer ended after " + consumed + " byte(s) of a VarInt");
		}

		return buf.getByte(index);
	}

}
