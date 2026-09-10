package dev.onyxium.proxy.io.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import org.junit.Test;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.api.message.MessageParam;
import dev.onyxium.proxy.io.packet.connection.ServerDisconnect;

public class FormattedMessageCodecTest {

	@Test
	public void matchesRawTextWireLayoutAndDisconnectFraming() {
		var message = FormattedMessage.text("Hello");
		var channel = new EmbeddedChannel(new PacketEncoder(), new PacketDecoder());
		try {
			channel.writeOutbound(new ServerDisconnect(message));
			var bytes = (ByteBuf) channel.readOutbound();
			assertEquals(48, bytes.getIntLE(0));
			assertEquals(2, bytes.getIntLE(4));
			assertEquals(1, bytes.getUnsignedByte(8));
			assertEquals(0x20, bytes.getUnsignedShortLE(10));
			assertEquals(0, bytes.getIntLE(18));
			for (var i = 1; i < 8; i++) {
				assertEquals(-1, bytes.getIntLE(18 + i * 4));
			}
			assertEquals(5, bytes.getUnsignedByte(50));
			channel.writeInbound(bytes);
			assertEquals(new ServerDisconnect(message), channel.readInbound());
		}
		finally {
			channel.finishAndReleaseAll();
		}
	}

	@Test
	public void roundTripsAllFieldsAndTypedParameters() {
		var message = FormattedMessage.builder()
			.translation("test.welcome")
			.param("text", "Oskar")
			.param("bool", true)
			.param("double", 1.25)
			.param("int", 42)
			.param("long", Long.MAX_VALUE)
			.param("nullable", new MessageParam.Text(null))
			.param("message", FormattedMessage.builder().text("nested").bold(false).build())
			.append(child -> child.text("child").italic())
			.bold()
			.italic(false)
			.monospace()
			.underlined()
			.strikethrough(false)
			.color("#abcdef")
			.link("https://hytale.com")
			.markupEnabled(true)
			.image("UI/Icons/Test.png", 32, 16)
			.build();
		var bytes = Unpooled.buffer();
		try {
			bytes.writeZero(13);
			bytes.readerIndex(13);
			FormattedMessageCodec.serialize(bytes, message);
			assertEquals(message, FormattedMessageCodec.deserialize(bytes));
			assertFalse(bytes.isReadable());
		}
		finally {
			bytes.release();
		}
	}

	@Test
	public void buildersProduceIndependentImmutableMessages() {
		var builder = FormattedMessage.builder().text("root").append("first").param("n", 1);
		var first = builder.build();
		builder.append("second").param("n", 2);
		assertEquals(1, first.children().size());
		assertEquals(new MessageParam.Int(1), first.params().get("n"));
		assertThrows(UnsupportedOperationException.class, () -> first.children().clear());
		assertThrows(UnsupportedOperationException.class, () -> first.params().clear());
		var changed = first.toBuilder().bold(false).append("third").build();
		assertNull(first.bold());
		assertEquals(Boolean.FALSE, changed.bold());
		assertEquals(1, first.children().size());
	}

	@Test
	public void rejectsOverlappingOffsetsAndExcessiveNesting() {
		var bytes = Unpooled.buffer();
		try {
			FormattedMessageCodec.serialize(bytes, FormattedMessage.text("test"));
			bytes.setIntLE(8, -40);
			assertThrows(CorruptedFrameException.class, () -> FormattedMessageCodec.deserialize(bytes));
			var message = FormattedMessage.text("leaf");
			for (var i = 0; i < 34; i++) {
				message = FormattedMessage.builder().append(message).build();
			}
			var deep = message;
			bytes.clear();
			assertThrows(CorruptedFrameException.class, () -> FormattedMessageCodec.serialize(bytes, deep));
		}
		finally {
			bytes.release();
		}
	}

}
