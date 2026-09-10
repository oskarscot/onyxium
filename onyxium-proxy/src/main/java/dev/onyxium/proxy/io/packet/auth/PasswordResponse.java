package dev.onyxium.proxy.io.packet.auth;

import io.netty.buffer.ByteBuf;

import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.util.NettyUtil;

public record PasswordResponse(byte[] hash) implements Packet {
	public static PasswordResponse deserialize(ByteBuf buf) {
		var present = (buf.readUnsignedByte() & 1) != 0;

		return new PasswordResponse(present ? NettyUtil.readVarBytes(buf, 64) : null);
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeByte(hash != null ? 1 : 0);

		if (hash != null) {
			NettyUtil.writeVarBytes(buf, hash, 64);
		}
	}

	@Override
	public int id() {
		return 15;
	}
}
