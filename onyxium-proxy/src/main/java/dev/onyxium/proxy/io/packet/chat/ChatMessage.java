package dev.onyxium.proxy.io.packet.chat;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.util.NettyUtil;

/// Chat text sent by the client, limited to 255 UTF-8 bytes.
public record ChatMessage(String message) implements Packet {

	private static final int MAX_MESSAGE_BYTES = 255;

	public static ChatMessage deserialize(ByteBuf buf) {
		if ((buf.readUnsignedByte() & 1) == 0)
			return new ChatMessage(null);
		var bytes = NettyUtil.readVarBytes(buf, MAX_MESSAGE_BYTES);
		try {
			return new ChatMessage(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
		}
		catch (CharacterCodingException exception) {
			throw new CorruptedFrameException("Invalid chat UTF-8", exception);
		}
	}

	@Override
	public int id() {
		return 211;
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeByte(message == null ? 0 : 1);
		if (message != null)
			NettyUtil.writeVarString(buf, message, MAX_MESSAGE_BYTES);
	}

}
