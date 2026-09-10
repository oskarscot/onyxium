package dev.onyxium.proxy.io.packet.connection;

import java.time.Instant;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

import dev.onyxium.proxy.io.packet.Packet;

public record Pong(int pingId, Instant time, int type, short packetQueueSize) implements Packet {
	public static Pong deserialize(ByteBuf buf) {
		var id = buf.readIntLE();
		var seconds = buf.readLongLE();
		var nanos = buf.readIntLE();
		if (nanos < 0 || nanos > 999_999_999) {
			throw new CorruptedFrameException("Invalid instant");
		}
		var packet = new Pong(id, Instant.ofEpochSecond(seconds, nanos), buf.readUnsignedByte(), buf.readShortLE());
		if (packet.type < 0 || packet.type > 2)
			throw new CorruptedFrameException("Invalid pong type");
		return packet;
	}

	@Override
	public int id() {
		return 4;
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeIntLE(pingId);
		buf.writeLongLE(time.getEpochSecond());
		buf.writeIntLE(time.getNano());
		buf.writeByte(type);
		buf.writeShortLE(packetQueueSize);
	}
}
