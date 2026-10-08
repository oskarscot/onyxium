package dev.onyxium.proxy.io.packet.chat;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.io.packet.FormattedMessageCodec;
import dev.onyxium.proxy.io.packet.Packet;

/// Sends a formatted message to the player's chat.
public record ServerMessage(FormattedMessage message) implements Packet {

	// Chat is the only message type in this protocol version.
	private static final int CHAT_TYPE = 0;

	public static ServerMessage deserialize(ByteBuf buf) {
		var present = (buf.readUnsignedByte() & 1) != 0;
		if (buf.readUnsignedByte() != CHAT_TYPE)
			throw new CorruptedFrameException("Invalid chat type");
		return new ServerMessage(present ? FormattedMessageCodec.deserialize(buf) : null);
	}

	@Override
	public int id() {
		return 210;
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeByte(message == null ? 0 : 1);
		buf.writeByte(CHAT_TYPE);
		if (message != null)
			FormattedMessageCodec.serialize(buf, message);
	}

}
