package dev.onyxium.proxy.io.packet.connection;

import java.time.Instant;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

import dev.onyxium.proxy.io.packet.Packet;

public record Ping(int pingId, Instant time, int lastRaw, int lastDirect, int lastTick) implements Packet {
	public static Ping deserialize(ByteBuf buf) {
		var id = buf.readIntLE();
		var seconds = buf.readLongLE();
		var nanos = buf.readIntLE();
		if (nanos < 0 || nanos > 999_999_999) {
			throw new CorruptedFrameException("Invalid instant");
		}
		var packet = new Ping(id, Instant.ofEpochSecond(seconds, nanos), buf.readIntLE(), buf.readIntLE(),
				buf.readIntLE());

		return packet;
	}

	@Override
	public int id() {
		return 3;
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeIntLE(pingId);
		buf.writeLongLE(time.getEpochSecond());
		buf.writeIntLE(time.getNano());
		buf.writeIntLE(lastRaw);
		buf.writeIntLE(lastDirect);
		buf.writeIntLE(lastTick);
	}
}
