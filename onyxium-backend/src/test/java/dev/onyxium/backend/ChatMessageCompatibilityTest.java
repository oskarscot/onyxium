package dev.onyxium.backend;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

import com.hypixel.hytale.protocol.io.ProtocolException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import org.junit.Test;

import dev.onyxium.proxy.io.packet.PacketDecoder;
import dev.onyxium.proxy.io.packet.PacketEncoder;
import dev.onyxium.proxy.io.packet.VarInts;
import dev.onyxium.proxy.io.packet.chat.ChatMessage;

public class ChatMessageCompatibilityTest {

	@Test
	public void encodesAndDecodesHytaleChatMessages() {
		for (var message : new String[] { null, "", "Hello, 世界 👋", "a".repeat(255), "界".repeat(85),
				"👋".repeat(63) + "abc" }) {
			var hytale = new com.hypixel.hytale.protocol.packets.interface_.ChatMessage(message);
			var expected = new byte[hytale.computeSize()];
			hytale.serialize(MemorySegment.ofArray(expected), 0);
			var channel = new EmbeddedChannel(new PacketDecoder(), new PacketEncoder());
			try {
				var packet = new ChatMessage(message);
				assertTrue(channel.writeOutbound(packet));
				var frame = (ByteBuf) channel.readOutbound();
				try {
					assertEquals(expected.length, frame.readIntLE());
					assertEquals(hytale.getId(), frame.readIntLE());
					var actual = new byte[frame.readableBytes()];
					frame.readBytes(actual);
					assertArrayEquals(expected, actual);
				}
				finally {
					frame.release();
				}
				assertFalse(channel.writeInbound(Unpooled.buffer().writeIntLE(expected.length)));
				assertTrue(channel.writeInbound(Unpooled.buffer().writeIntLE(hytale.getId()).writeBytes(expected)));
				assertEquals(packet, channel.readInbound());
			}
			finally {
				channel.finishAndReleaseAll();
			}
		}
	}

	@Test
	public void enforcesByteLimitWhenEncodingAndDecoding() {
		for (var message : new String[] { "a".repeat(256), "é".repeat(128) }) {
			var hytale = new com.hypixel.hytale.protocol.packets.interface_.ChatMessage(message);
			assertThrows(ProtocolException.class,
					() -> hytale.serialize(MemorySegment.ofArray(new byte[hytale.computeSize()]), 0));
			var buffer = Unpooled.buffer();
			try {
				assertThrows(IllegalStateException.class, () -> new ChatMessage(message).serialize(buffer));
				buffer.clear().writeByte(1);
				var bytes = message.getBytes(StandardCharsets.UTF_8);
				VarInts.write(buffer, bytes.length);
				buffer.writeBytes(bytes);
				assertThrows(CorruptedFrameException.class, () -> ChatMessage.deserialize(buffer));
			}
			finally {
				buffer.release();
			}
		}
	}

	@Test
	public void rejectsTruncatedStringsInvalidUtf8AndTrailingData() {
		for (var payload : new byte[][] { { 1 }, { 1, 3, 'h', 'i' }, { 1, 1, (byte) 0xff },
				{ 1, 2, (byte) 0xc0, (byte) 0x80 }, { 1, 3, (byte) 0xed, (byte) 0xa0, (byte) 0x80 }, { 0, 42 } }) {
			var channel = new EmbeddedChannel(new PacketDecoder());
			try {
				assertThrows(CorruptedFrameException.class, () -> channel
					.writeInbound(Unpooled.buffer().writeIntLE(payload.length).writeIntLE(211).writeBytes(payload)));
			}
			finally {
				channel.finishAndReleaseAll();
			}
		}
	}

}
