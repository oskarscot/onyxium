package dev.onyxium.proxy.io.packet.auth;

import java.util.UUID;

import dev.onyxium.proxy.io.packet.ClientType;
import dev.onyxium.proxy.io.packet.HostAddress;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.CorruptedFrameException;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConnectTest {

    private static final int FIXED_PART_SIZE = 66;

    private static final UUID UUID_VALUE = UUID.fromString("6ad0f0d4-6c9e-4a9e-bd0f-5c4a4d9e1b2c");
    private static final String IDENTITY_TOKEN = "a".repeat(512);
    private static final byte[] REFERRAL_DATA = { 0x00, 0x7F, -0x80, -0x01, 0x2A };
    private static final HostAddress REFERRAL_SOURCE = new HostAddress("hub.onyxium.dev", (short) 5520);

    @Test
    public void roundTripsEveryOptionalFieldCombination() {
        for (var mask = 0; mask < 8; mask++) {
            var packet = new Connect(
                    0x1234ABCD,
                    987654,
                    "0.6.0-pre.11",
                    ClientType.GAME,
                    UUID_VALUE,
                    "en-US",
                    (mask & 0x1) != 0 ? IDENTITY_TOKEN : null,
                    "oskarscot",
                    (mask & 0x2) != 0 ? REFERRAL_DATA : null,
                    (mask & 0x4) != 0 ? REFERRAL_SOURCE : null);

            var decoded = roundTrip(packet);

            assertEquals("mask " + mask, packet.protocolCrc(), decoded.protocolCrc());
            assertEquals("mask " + mask, packet.protocolBuildNumber(), decoded.protocolBuildNumber());
            assertEquals("mask " + mask, packet.clientVersion(), decoded.clientVersion());
            assertEquals("mask " + mask, packet.clientType(), decoded.clientType());
            assertEquals("mask " + mask, packet.uuid(), decoded.uuid());
            assertEquals("mask " + mask, packet.language(), decoded.language());
            assertEquals("mask " + mask, packet.username(), decoded.username());
            assertEquals("mask " + mask, packet.identityToken(), decoded.identityToken());
            assertArrayEquals("mask " + mask, packet.referralData(), decoded.referralData());
            assertEquals("mask " + mask, packet.referralSource(), decoded.referralSource());
        }
    }

    @Test
    public void roundTripsEmptyStrings() {
        var decoded = roundTrip(minimal("", "", ""));

        assertEquals("", decoded.clientVersion());
        assertEquals("", decoded.username());
        assertEquals("", decoded.language());
    }

    @Test
    public void keepsTheFixedPartAtAConstantSize() {
        var buf = Unpooled.buffer();
        minimal("v".repeat(20), "oskarscot", "en-US").serialize(buf);
        var fullWidth = buf.readableBytes();

        buf.clear();
        minimal("v", "oskarscot", "en-US").serialize(buf);

        assertEquals(fullWidth, buf.readableBytes());
        assertEquals("v", Connect.deserialize(buf).clientVersion());
    }

    @Test
    public void writesHeapRelativeOffsets() {
        var buf = Unpooled.buffer();
        minimal("1.0.0", "oskarscot", "en-US").serialize(buf);

        var usernameOffset = buf.getIntLE(FIXED_PART_SIZE - 20);
        var identityTokenOffset = buf.getIntLE(FIXED_PART_SIZE - 16);
        var languageOffset = buf.getIntLE(FIXED_PART_SIZE - 12);

        assertEquals(0, usernameOffset);
        assertEquals(-1, identityTokenOffset);
        // "oskarscot" costs a one-byte length prefix plus five bytes of UTF-8.
        assertEquals(10, languageOffset);
    }

    @Test
    public void leavesTheReaderIndexAtTheEndOfThePacket() {
        var buf = Unpooled.buffer();
        var packet = new Connect(1, 2, "1.0.0", ClientType.EDITOR, UUID_VALUE, "en-US",
                IDENTITY_TOKEN, "oskarscot", REFERRAL_DATA, REFERRAL_SOURCE);
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
        minimal("1.0.0", "oskarscot", "en-US").serialize(buf);
        buf.setIntLE(FIXED_PART_SIZE - 20, Integer.MAX_VALUE);

        try {
            Connect.deserialize(buf);
            assertTrue("expected a corrupt frame", false);
        } catch (CorruptedFrameException expected) {
            assertTrue(expected.getMessage().contains("outside the packet"));
        }
    }
    @Test
    public void roundTripsWhenNotAtTheStartOfTheBuffer() {
        var buf = Unpooled.buffer();
        buf.writeBytes(new byte[37]);
        buf.readerIndex(37);

        var packet = minimal("1.0.0", "oskarscot", "en-US");
        packet.serialize(buf);

        assertEquals(packet.username(), Connect.deserialize(buf).username());
    }

    @Test
    public void treatsAbsentOptionalFieldsAsNull() {
        var decoded = roundTrip(minimal("1.0.0", "oskarscot", "en-US"));

        assertNull(decoded.identityToken());
        assertNull(decoded.referralData());
        assertNull(decoded.referralSource());
    }

    private static Connect minimal(String clientVersion, String username, String language) {
        return new Connect(0, 0, clientVersion, ClientType.GAME, UUID_VALUE, language,
                null, username, null, null);
    }

    private static Connect roundTrip(Connect packet) {
        ByteBuf buf = Unpooled.buffer();
        packet.serialize(buf);

        var decoded = Connect.deserialize(buf);
        assertEquals("packet did not consume its own bytes", 0, buf.readableBytes());

        return decoded;
    }
}
