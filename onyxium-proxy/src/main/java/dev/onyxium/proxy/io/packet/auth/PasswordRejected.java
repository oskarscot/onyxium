package dev.onyxium.proxy.io.packet.auth;

import io.netty.buffer.ByteBuf;

import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.util.NettyUtil;

public record PasswordRejected(byte[] newChallenge, int attemptsRemaining) implements Packet {
	public static PasswordRejected deserialize(ByteBuf buf) {
		var present = (buf.readUnsignedByte() & 1) != 0;
		var attempts = buf.readIntLE();
		return new PasswordRejected(present ? NettyUtil.readVarBytes(buf, 64) : null, attempts);
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeByte(newChallenge != null ? 1 : 0);
		buf.writeIntLE(attemptsRemaining);
		if (newChallenge != null) {
			NettyUtil.writeVarBytes(buf, newChallenge, 64);
		}
	}

	@Override
	public int id() {
		return 17;
	}
}
