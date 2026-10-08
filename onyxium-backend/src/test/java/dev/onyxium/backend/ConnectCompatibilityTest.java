package dev.onyxium.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.hypixel.hytale.protocol.packets.connection.Connect;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import dev.onyxium.proxy.io.packet.ClientType;
import dev.onyxium.proxy.io.packet.HostAddress;
import dev.onyxium.proxy.io.packet.PacketDecoder;
import dev.onyxium.proxy.io.packet.PacketEncoder;
import dev.onyxium.proxy.io.packet.ProtocolVersion;

@RunWith(Parameterized.class)
public class ConnectCompatibilityTest {

	long crc;

	int optionalFields;

	String language;

	public ConnectCompatibilityTest(long crc, int optionalFields, String language) {
		this.crc = crc;
		this.optionalFields = optionalFields;
		this.language = language;
	}

	@Parameterized.Parameters(name = "crc={0}, optionalFields={1}, case={index}")
	public static List<Object[]> cases() {
		return LongStream.of(0, Integer.MAX_VALUE, 0x80000000L, ProtocolVersion.CURRENT.crc(), 0xFFFFFFFFL)
			.boxed()
			.flatMap(ConnectCompatibilityTest::optionalFieldCases)
			.toList();
	}

	static Stream<Object[]> optionalFieldCases(long crc) {
		return IntStream.range(0, 8)
			.boxed()
			.flatMap(mask -> Stream.of("", "en-US", "a".repeat(100))
				.map(language -> new Object[] { crc, mask, language }));
	}

	@Test
	public void matchesHytaleConnectEncodingAndDecoding() {
		var identityToken = (optionalFields & 1) != 0 ? "a".repeat(8192) : null;
		var referralData = (optionalFields & 2) != 0 ? new byte[4096] : null;
		var referralSource = (optionalFields & 4) != 0 ? new HostAddress("source.example", (short) 5520) : null;
		var packet = new dev.onyxium.proxy.io.packet.auth.Connect(crc, ProtocolVersion.CURRENT.buildNumber(),
				"0.7.0-pre.5.1", ClientType.GAME, language, identityToken, referralData, referralSource);
		var hytale = new Connect((int) crc, packet.protocolBuildNumber(), packet.clientVersion(),
				com.hypixel.hytale.protocol.packets.connection.ClientType.Game, identityToken, language, referralData,
				referralSource == null ? null
						: new com.hypixel.hytale.protocol.HostAddress(referralSource.host(), referralSource.port()));
		var expected = new byte[hytale.computeSize()];
		hytale.serialize(MemorySegment.ofArray(expected), 0);
		var channel = new EmbeddedChannel(new PacketDecoder(), new PacketEncoder());
		try {
			assertThat(channel.writeOutbound(packet)).isTrue();
			var frame = (ByteBuf) channel.readOutbound();
			try {
				assertThat(frame.readIntLE()).isEqualTo(expected.length);
				assertThat(frame.readIntLE()).isEqualTo(hytale.getId());
				var actual = new byte[frame.readableBytes()];
				frame.readBytes(actual);
				assertThat(actual).containsExactly(expected);
				assertThat(Connect.toObject(MemorySegment.ofArray(actual))).isEqualTo(hytale);
			}
			finally {
				frame.release();
			}
			assertThat(channel.writeInbound(Unpooled.buffer().writeIntLE(expected.length))).isFalse();
			assertThat(channel.writeInbound(Unpooled.buffer().writeIntLE(hytale.getId()).writeBytes(expected)))
				.isTrue();
			var decoded = (dev.onyxium.proxy.io.packet.auth.Connect) channel.readInbound();
			assertThat(decoded).usingRecursiveComparison().isEqualTo(packet);
		}
		finally {
			channel.finishAndReleaseAll();
		}
	}

}
