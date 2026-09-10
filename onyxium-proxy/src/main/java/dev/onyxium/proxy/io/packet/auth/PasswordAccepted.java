package dev.onyxium.proxy.io.packet.auth;

import io.netty.buffer.ByteBuf;

import dev.onyxium.proxy.io.packet.Packet;

public record PasswordAccepted() implements Packet {
	@Override
	public int id() {
		return 16;
	}

	@Override
	public void serialize(ByteBuf buf) {
	}
}
