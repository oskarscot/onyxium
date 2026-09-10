package dev.onyxium.proxy.io.packet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class PacketEncoder extends MessageToByteEncoder<Packet> {

	@Override
	protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf buf) throws Exception {
		switch (packet) {
			case UnknownPacket u -> buf.writeBytes(u.bytes());
			default -> {
				var lengthPosition = buf.writerIndex();
				buf.writeIntLE(-1);

				buf.writeIntLE(packet.id());
				packet.serialize(buf);

				buf.setIntLE(lengthPosition, buf.writerIndex() - lengthPosition - 8);
			}
		}
	}

}
