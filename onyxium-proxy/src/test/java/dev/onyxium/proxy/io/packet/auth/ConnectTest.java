package dev.onyxium.proxy.io.packet.auth;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.CorruptedFrameException;
import org.junit.Test;

import dev.onyxium.proxy.io.packet.ClientType;
import dev.onyxium.proxy.io.packet.HostAddress;

public class ConnectTest {

	private static final int FIXED_PART_SIZE = 46;

	private static final String IDENTITY_TOKEN = "a".repeat(512);

	private static final byte[] REFERRAL_DATA = { 0x00, 0x7F, -0x80, -0x01, 0x2A };

	private static final HostAddress REFERRAL_SOURCE = new HostAddress("hub.onyxium.dev", (short) 5520);

	@Test
	public void roundTripsEveryOptionalFieldCombination() {
		for (var mask = 0; mask < 8; mask++) {
			var packet = new Connect(0x1234ABCD, 987654, "0.6.0-pre.11", ClientType.GAME, "en-US",
					(mask & 0x1) != 0 ? IDENTITY_TOKEN : null, (mask & 0x2) != 0 ? REFERRAL_DATA : null,
					(mask & 0x4) != 0 ? REFERRAL_SOURCE : null);

			var decoded = roundTrip(packet);

			assertEquals("mask " + mask, packet.protocolCrc(), decoded.protocolCrc());
			assertEquals("mask " + mask, packet.protocolBuildNumber(), decoded.protocolBuildNumber());
			assertEquals("mask " + mask, packet.clientVersion(), decoded.clientVersion());
			assertEquals("mask " + mask, packet.clientType(), decoded.clientType());
			assertEquals("mask " + mask, packet.language(), decoded.language());
			assertEquals("mask " + mask, packet.identityToken(), decoded.identityToken());
			assertArrayEquals("mask " + mask, packet.referralData(), decoded.referralData());
			assertEquals("mask " + mask, packet.referralSource(), decoded.referralSource());
		}
	}

	@Test
	public void roundTripsEmptyStrings() {
		var decoded = roundTrip(minimal("", ""));

		assertEquals("", decoded.clientVersion());
		assertEquals("", decoded.language());
	}

	@Test
	public void keepsTheFixedPartAtAConstantSize() {
		var buf = Unpooled.buffer();
		minimal("v".repeat(20), "en-US").serialize(buf);
		var fullWidth = buf.readableBytes();

		buf.clear();
		minimal("v", "en-US").serialize(buf);

		assertEquals(fullWidth, buf.readableBytes());
		assertEquals("v", Connect.deserialize(buf).clientVersion());
	}

	@Test
	public void writesHeapRelativeOffsets() {
		var buf = Unpooled.buffer();
		minimal("1.0.0", "en-US").serialize(buf);

		var identityTokenOffset = buf.getIntLE(FIXED_PART_SIZE - 16);
		var languageOffset = buf.getIntLE(FIXED_PART_SIZE - 12);

		assertEquals(-1, identityTokenOffset);
		assertEquals(0, languageOffset);
	}

	@Test
	public void leavesTheReaderIndexAtTheEndOfThePacket() {
		var buf = Unpooled.buffer();
		var packet = new Connect(1, 2, "1.0.0", ClientType.EDITOR, "en-US", IDENTITY_TOKEN, REFERRAL_DATA,
				REFERRAL_SOURCE);
		packet.serialize(buf);

		var packetLength = buf.readableBytes();
		buf.writeInt(0xDEADBEEF);

		Connect.deserialize(buf);

		assertEquals(packetLength, buf.readerIndex());
		assertEquals(0xDEADBEEF, buf.readInt());
	}

	@Test
	public void rejectsOffsetsPointingOutsideThePacket() {
		var buf = Unpooled.buffer();
		minimal("1.0.0", "en-US").serialize(buf);
		buf.setIntLE(FIXED_PART_SIZE - 12, Integer.MAX_VALUE);

		try {
			Connect.deserialize(buf);
			assertTrue("expected a corrupt frame", false);
		}
		catch (CorruptedFrameException expected) {
			assertTrue(expected.getMessage().contains("outside the packet"));
		}
	}

	@Test
	public void roundTripsWhenNotAtTheStartOfTheBuffer() {
		var buf = Unpooled.buffer();
		buf.writeBytes(new byte[37]);
		buf.readerIndex(37);

		var packet = minimal("1.0.0", "en-US");
		packet.serialize(buf);

		assertEquals(packet.language(), Connect.deserialize(buf).language());
	}

	@Test
	public void treatsAbsentOptionalFieldsAsNull() {
		var decoded = roundTrip(minimal("1.0.0", "en-US"));

		assertNull(decoded.identityToken());
		assertNull(decoded.referralData());
		assertNull(decoded.referralSource());
	}

	private static Connect minimal(String clientVersion, String language) {
		return new Connect(0, 0, clientVersion, ClientType.GAME, language, null, null, null);
	}

	private static Connect roundTrip(Connect packet) {
		ByteBuf buf = Unpooled.buffer();
		packet.serialize(buf);

		var decoded = Connect.deserialize(buf);
		assertEquals("packet did not consume its own bytes", 0, buf.readableBytes());

		return decoded;
	}

}
