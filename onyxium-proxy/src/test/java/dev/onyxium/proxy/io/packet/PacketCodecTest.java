package dev.onyxium.proxy.io.packet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HexFormat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import org.junit.Test;

import dev.onyxium.proxy.io.packet.auth.AuthGrant;
import dev.onyxium.proxy.io.packet.auth.AuthToken;

public class PacketCodecTest {

	@Test
	public void decodesFragmentedAuthTokenAndCoalescedFrames() {
		var channel = new EmbeddedChannel(new PacketDecoder());
		var frame = HexFormat.of().parseHex("100000000c00000003000000000400000003616263026465");
		try {
			assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(frame, 0, 11)));
			assertNull(channel.readInbound());
			var rest = Unpooled.buffer().writeBytes(frame, 11, frame.length - 11).writeBytes(frame);
			assertTrue(channel.writeInbound(rest));
			assertEquals(new AuthToken("abc", "de"), channel.readInbound());
			assertEquals(new AuthToken("abc", "de"), channel.readInbound());
		}
		finally {
			channel.finishAndReleaseAll();
		}
	}

	@Test
	public void encodesHeapRelativeAuthOffsets() {
		var channel = new EmbeddedChannel(new PacketEncoder());
		try {
			channel.writeOutbound(new AuthGrant("abc", "de"));
			var bytes = (ByteBuf) channel.readOutbound();
			try {
				var expected = HexFormat.of().parseHex("100000000b00000003000000000400000003616263026465");
				var actual = new byte[bytes.readableBytes()];
				bytes.readBytes(actual);
				assertArrayEquals(expected, actual);
			}
			finally {
				bytes.release();
			}
		}
		finally {
			channel.finishAndReleaseAll();
		}
	}

	@Test
	public void opaquePacketsKeepTheirOriginalFrameAndId() {
		var channel = new EmbeddedChannel(new PacketDecoder(), new PacketEncoder());
		var original = HexFormat.of().parseHex("05000000840300000102030405");
		try {
			channel.writeInbound(Unpooled.wrappedBuffer(original.clone()));
			var packet = (UnknownPacket) channel.readInbound();
			assertEquals(900, packet.id());
			channel.writeOutbound(packet);
			var bytes = (ByteBuf) channel.readOutbound();
			try {
				var actual = new byte[bytes.readableBytes()];
				bytes.readBytes(actual);
				assertArrayEquals(original, actual);
			}
			finally {
				bytes.release();
			}
		}
		finally {
			channel.finishAndReleaseAll();
		}
	}

	@Test
	public void rejectsOversizedHeadersWithoutWaitingForPayload() {
		for (var size : new int[] { -1, Integer.MAX_VALUE, 65537 }) {
			var channel = new EmbeddedChannel(new PacketDecoder());
			try {
				assertThrows(CorruptedFrameException.class,
						() -> channel.writeInbound(Unpooled.buffer().writeIntLE(size).writeIntLE(12)));
			}
			finally {
				channel.finishAndReleaseAll();
			}
		}
	}

	@Test
	public void rejectsOverflowedVariableLengths() {
		var bytes = Unpooled.wrappedBuffer(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x10 });
		try {
			assertThrows(CorruptedFrameException.class, () -> VarInts.read(bytes));
		}
		finally {
			bytes.release();
		}
	}

}
