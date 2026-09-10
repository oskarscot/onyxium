package dev.onyxium.proxy.io.packet;

import io.netty.buffer.ByteBuf;

import dev.onyxium.proxy.util.NettyUtil;

public record HostAddress(String host, short port) {
	public void serialize(ByteBuf buf) {
		buf.writeShortLE(this.port);
		NettyUtil.writeVarString(buf, this.host, 256);
	}

	public static HostAddress deserialize(ByteBuf buf) {
		var port = buf.readShortLE();
		var host = NettyUtil.readVarString(buf, 256);
		return new HostAddress(host, port);
	}
}
