package dev.onyxium.proxy.io.packet.connection;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.io.packet.FormattedMessageCodec;
import dev.onyxium.proxy.io.packet.Packet;

public record ServerDisconnect(FormattedMessage reason, boolean crash) implements Packet {
	public ServerDisconnect(FormattedMessage reason) {
		this(reason, false);
	}

	public static ServerDisconnect deserialize(ByteBuf buf) {
		var present = (buf.readUnsignedByte() & 1) != 0;
		var type = buf.readUnsignedByte();
		if (type > 1) {
			throw new CorruptedFrameException("Invalid disconnect type");
		}
		return new ServerDisconnect(present ? FormattedMessageCodec.deserialize(buf) : null, type == 1);
	}

	@Override
	public int id() {
		return 2;
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeByte(reason == null ? 0 : 1);
		buf.writeByte(crash ? 1 : 0);
		if (reason != null) {
			FormattedMessageCodec.serialize(buf, reason);
		}
	}
}
