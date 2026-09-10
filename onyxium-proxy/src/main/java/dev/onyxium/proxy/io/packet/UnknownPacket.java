package dev.onyxium.proxy.io.packet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.netty.buffer.ByteBuf;

/// An owned copy of the complete frame, including its header. Compression stays untouched.
public record UnknownPacket(byte[] bytes) implements Packet {
	public UnknownPacket {
		if (bytes.length < 8 || ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt() != bytes.length - 8) {
			throw new IllegalArgumentException("Invalid opaque packet frame");
		}
		bytes = bytes.clone();
	}

	@Override
	public byte[] bytes() {
		return bytes.clone();
	}

	@Override
	public void serialize(ByteBuf buf) {
		throw new UnsupportedOperationException("Opaque frames are written by PacketEncoder");
	}

	@Override
	public int id() {
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
	}

	@Override
	public String toString() {
		return "UnknownPacket[id=" + id() + ", size=" + bytes.length + "]";
	}
}
