package dev.onyxium.proxy.io.packet.connection;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

import dev.onyxium.proxy.io.packet.Packet;

public record ClientDisconnect(int reason, int type) implements Packet {
	public static ClientDisconnect deserialize(ByteBuf buf) {
		var reason = buf.readUnsignedByte();
		var type = buf.readUnsignedByte();
		if (reason > 3 || type > 1) {
			throw new CorruptedFrameException("Invalid disconnect reason or type");
		}
		return new ClientDisconnect(reason, type);
	}

	@Override
	public int id() {
		return 1;
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeByte(reason);
		buf.writeByte(type);
	}
}
