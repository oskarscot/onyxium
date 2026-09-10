package dev.onyxium.proxy.io.packet;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

public final class PacketDecoder extends ByteToMessageDecoder {

	public static final int LOGIN_MAX_FRAME_SIZE = 64 * 1024;

	public static final int FORWARDING_MAX_FRAME_SIZE = 16 * 1024 * 1024;

	private int maxFrameSize = LOGIN_MAX_FRAME_SIZE;

	/// Must be called on the stream's event loop after authentication.
	public void authenticated() {
		maxFrameSize = FORWARDING_MAX_FRAME_SIZE;
	}

	/// [payload length: 4 bytes LE][packet id: 4 bytes LE][payload]
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
		if (in.readableBytes() < 8)
			return;
		var start = in.readerIndex();
		var length = in.getIntLE(start);
		var id = in.getIntLE(start + 4);
		var info = PacketRegistry.findById(id);
		if (id < 0 || length < 0 || length > maxFrameSize
				|| (info != null && (length < info.minSize() || length > info.maxSize()))) {
			in.skipBytes(in.readableBytes());
			throw new CorruptedFrameException("Invalid packet header (id=" + id + ", length=" + length + ")");
		}
		if (in.readableBytes() - 8 < length)
			return;
		in.skipBytes(8);
		var payload = in.readSlice(length);
		if (info == null) {
			var frame = new byte[8 + length];
			in.getBytes(start, frame);
			out.add(new UnknownPacket(frame));
			return;
		}
		try {
			var packet = info.factory().apply(payload);
			if (payload.isReadable())
				throw new CorruptedFrameException("Trailing packet data");
			out.add(packet);
		}
		catch (IndexOutOfBoundsException | IllegalArgumentException exception) {
			throw new CorruptedFrameException("Malformed packet " + id);
		}
	}

}
