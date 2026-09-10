package dev.onyxium.proxy.io.packet.auth;

import io.netty.buffer.ByteBuf;

import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.util.NettyUtil;

public record AuthToken(String accessToken, String serverAuthorizationGrant) implements Packet {
	public static AuthToken deserialize(ByteBuf buf) {
		var bits = buf.readUnsignedByte();
		var firstOffset = buf.readIntLE();
		var secondOffset = buf.readIntLE();
		var heap = buf.readerIndex();
		var end = heap;
		String first = null;
		String second = null;
		if ((bits & 1) != 0) {
			NettyUtil.seekToField(buf, heap, firstOffset);
			first = NettyUtil.readVarString(buf, 8192);
			end = Math.max(end, buf.readerIndex());
		}
		if ((bits & 2) != 0) {
			NettyUtil.seekToField(buf, heap, secondOffset);
			second = NettyUtil.readVarString(buf, 4096);
			end = Math.max(end, buf.readerIndex());
		}
		buf.readerIndex(end);
		return new AuthToken(first, second);
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeByte((accessToken != null ? 1 : 0) | (serverAuthorizationGrant != null ? 2 : 0));
		var firstSlot = buf.writerIndex();
		buf.writeIntLE(-1);
		var secondSlot = buf.writerIndex();
		buf.writeIntLE(-1);
		var heap = buf.writerIndex();
		if (accessToken != null) {
			buf.setIntLE(firstSlot, buf.writerIndex() - heap);
			NettyUtil.writeVarString(buf, accessToken, 8192);
		}
		if (serverAuthorizationGrant != null) {
			buf.setIntLE(secondSlot, buf.writerIndex() - heap);
			NettyUtil.writeVarString(buf, serverAuthorizationGrant, 4096);
		}
	}

	@Override
	public int id() {
		return 12;
	}

	@Override
	public String toString() {
		return "AuthToken[redacted]";
	}
}
