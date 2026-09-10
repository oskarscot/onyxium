package dev.onyxium.proxy.io.packet.auth;

import io.netty.buffer.ByteBuf;

import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.util.NettyUtil;

public record ServerAuthToken(String serverAccessToken, byte[] passwordChallenge) implements Packet {
	public static ServerAuthToken deserialize(ByteBuf buf) {
		var bits = buf.readUnsignedByte();
		var firstOffset = buf.readIntLE();
		var secondOffset = buf.readIntLE();
		var heap = buf.readerIndex();
		var end = heap;
		String first = null;
		byte[] second = null;
		if ((bits & 1) != 0) {
			NettyUtil.seekToField(buf, heap, firstOffset);
			first = NettyUtil.readVarString(buf, 8192);
			end = Math.max(end, buf.readerIndex());
		}
		if ((bits & 2) != 0) {
			NettyUtil.seekToField(buf, heap, secondOffset);
			second = NettyUtil.readVarBytes(buf, 64);
			end = Math.max(end, buf.readerIndex());
		}
		buf.readerIndex(end);
		return new ServerAuthToken(first, second);
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeByte((serverAccessToken != null ? 1 : 0) | (passwordChallenge != null ? 2 : 0));
		var firstSlot = buf.writerIndex();
		buf.writeIntLE(-1);
		var secondSlot = buf.writerIndex();
		buf.writeIntLE(-1);
		var heap = buf.writerIndex();
		if (serverAccessToken != null) {
			buf.setIntLE(firstSlot, buf.writerIndex() - heap);
			NettyUtil.writeVarString(buf, serverAccessToken, 8192);
		}
		if (passwordChallenge != null) {
			buf.setIntLE(secondSlot, buf.writerIndex() - heap);
			NettyUtil.writeVarBytes(buf, passwordChallenge, 64);
		}
	}

	@Override
	public int id() {
		return 13;
	}

	@Override
	public String toString() {
		return "ServerAuthToken[redacted]";
	}
}
