package dev.onyxium.backend;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.foreign.MemorySegment;

import com.hypixel.hytale.protocol.packets.interface_.ChatType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import org.junit.Test;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.io.packet.PacketDecoder;
import dev.onyxium.proxy.io.packet.PacketEncoder;
import dev.onyxium.proxy.io.packet.chat.ServerMessage;

public class ServerMessageCompatibilityTest {

	@Test
	public void encodesAndDecodesHytaleServerMessages() {
		assertCompatible(null, null);
		var text = "Hello, 世界 👋";
		var hytale = new com.hypixel.hytale.protocol.FormattedMessage();
		hytale.rawText = text;
		hytale.bold = true;
		hytale.color = "#55ff55";
		var child = new com.hypixel.hytale.protocol.FormattedMessage();
		child.messageId = "server.general.welcome";
		hytale.children = new com.hypixel.hytale.protocol.FormattedMessage[] { child };
		var message = FormattedMessage.builder()
			.text(text)
			.bold()
			.color("#55ff55")
			.append(FormattedMessage.translation(child.messageId))
			.build();
		assertCompatible(message, hytale);
	}

	@Test
	public void rejectsInvalidChatTypesTruncatedMessagesAndTrailingData() {
		for (var payload : new byte[][] { { 0, 1 }, { 1, 0 }, { 0, 0, 42 } }) {
			var channel = new EmbeddedChannel(new PacketDecoder());
			try {
				assertThrows(CorruptedFrameException.class, () -> channel
					.writeInbound(Unpooled.buffer().writeIntLE(payload.length).writeIntLE(210).writeBytes(payload)));
			}
			finally {
				channel.finishAndReleaseAll();
			}
		}
	}

	private static void assertCompatible(FormattedMessage message,
			com.hypixel.hytale.protocol.FormattedMessage hytaleMessage) {
		var hytale = new com.hypixel.hytale.protocol.packets.interface_.ServerMessage(ChatType.Chat, hytaleMessage);
		var expected = new byte[hytale.computeSize()];
		hytale.serialize(MemorySegment.ofArray(expected), 0);
		var channel = new EmbeddedChannel(new PacketDecoder(), new PacketEncoder());
		try {
			var packet = new ServerMessage(message);
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
