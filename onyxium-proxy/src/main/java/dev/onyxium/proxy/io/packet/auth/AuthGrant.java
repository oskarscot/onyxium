package dev.onyxium.proxy.io.packet.auth;

import io.netty.buffer.ByteBuf;

import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.util.NettyUtil;

public record AuthGrant(String authorizationGrant, String serverIdentityToken) implements Packet {
	public static AuthGrant deserialize(ByteBuf buf) {
		var bits = buf.readUnsignedByte();
		var firstOffset = buf.readIntLE();
		var secondOffset = buf.readIntLE();
		var heap = buf.readerIndex();
		var end = heap;
		String first = null;
		String second = null;
		if ((bits & 1) != 0) {
			NettyUtil.seekToField(buf, heap, firstOffset);
			first = NettyUtil.readVarString(buf, 4096);
			end = Math.max(end, buf.readerIndex());
		}
		if ((bits & 2) != 0) {
			NettyUtil.seekToField(buf, heap, secondOffset);
			second = NettyUtil.readVarString(buf, 8192);
			end = Math.max(end, buf.readerIndex());
		}
		buf.readerIndex(end);
		return new AuthGrant(first, second);
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeByte((authorizationGrant != null ? 1 : 0) | (serverIdentityToken != null ? 2 : 0));
		var firstSlot = buf.writerIndex();
		buf.writeIntLE(-1);
		var secondSlot = buf.writerIndex();
		buf.writeIntLE(-1);
		var heap = buf.writerIndex();
		if (authorizationGrant != null) {
			buf.setIntLE(firstSlot, buf.writerIndex() - heap);
			NettyUtil.writeVarString(buf, authorizationGrant, 4096);
		}
		if (serverIdentityToken != null) {
			buf.setIntLE(secondSlot, buf.writerIndex() - heap);
			NettyUtil.writeVarString(buf, serverIdentityToken, 8192);
		}
	}

	@Override
	public int id() {
		return 11;
	}

	@Override
	public String toString() {
		return "AuthGrant[redacted]";
	}
}
