package dev.onyxium.backend;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hypixel.hytale.protocol.ProtocolSettings;
import com.hypixel.hytale.protocol.io.ChannelConnection;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.server.core.io.handlers.InitialPacketHandler;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

import dev.onyxium.forwarding.ForwardedIdentity;
import dev.onyxium.forwarding.ForwardingToken;
import dev.onyxium.proxy.io.packet.ClientType;
import dev.onyxium.proxy.io.packet.HostAddress;
import dev.onyxium.proxy.io.packet.ProtocolVersion;

public class ForwardingFilterTest {

	@Test
	public void proxyProtocolMatchesTargetServer() {
		assertTrue(ProtocolSettings.validateCrc(ProtocolVersion.CURRENT.crc()));
		assertEquals(ProtocolSettings.PROTOCOL_BUILD_NUMBER, ProtocolVersion.CURRENT.buildNumber());
	}

	@Test
	public void proxyAndHytaleAgreeOnSignedConnectPayload() {
		var tokens = new ForwardingToken(Base64.getEncoder().encodeToString(new byte[32]));
		var identity = new ForwardedIdentity(UUID.randomUUID(), "Oskar", null,
				new InetSocketAddress("192.0.2.1", 43210));
		for (var referral : new byte[][] { null, new byte[4096] }) {
			var original = new dev.onyxium.proxy.io.packet.auth.Connect(ProtocolVersion.CURRENT.crc(),
					ProtocolVersion.CURRENT.buildNumber(), "test-client", ClientType.GAME, "en-US", null, referral,
					referral == null ? null : new HostAddress("source.example", (short) 5520));
			var buffer = Unpooled.buffer();
			try {
				original.serialize(buffer);
				var payload = new byte[buffer.readableBytes()];
				buffer.getBytes(0, payload);
				var hytale = Connect.toObject(MemorySegment.ofArray(payload));
				assertArrayEquals(payload, ForwardingFilter.unsignedPayload(hytale));
				var now = Instant.now();
				hytale.identityToken = tokens.sign(identity, "client-cert", "server-cert", payload, now);
				assertEquals(identity,
						tokens
							.verify(hytale.identityToken, "client-cert", "server-cert",
									ForwardingFilter.unsignedPayload(hytale), now)
							.identity());
			}
			finally {
				buffer.release();
			}
		}
	}

	@Test
	public void missingConfigurationClosesInitialConnectionWithoutFallingThrough() {
		var closed = new AtomicBoolean();
		var channel = channel(new InetSocketAddress("127.0.0.1", 1234), closed);
		assertTrue(new ForwardingFilter(null).test(new InitialPacketHandler(channel), new Connect()));
		assertTrue(closed.get());
	}

	@Test
	public void forwardedOriginUsesPlayerAddressRatherThanProxyAddress() {
		var transport = channel(new InetSocketAddress("127.0.0.1", 1234), new AtomicBoolean());
		var first = new ForwardedChannelConnection(transport, new InetSocketAddress("192.0.2.1", 1234));
		var second = new ForwardedChannelConnection(transport, new InetSocketAddress("192.0.2.1", 9876));
		assertEquals(new InetSocketAddress("192.0.2.1", 1234), first.remoteAddress());
		assertTrue(first.isFromSameOrigin(second));
		Assert.assertFalse(first.isFromSameOrigin(transport));
	}

	private static ChannelConnection channel(InetSocketAddress address, AtomicBoolean closed) {
		return (ChannelConnection) Proxy.newProxyInstance(ChannelConnection.class.getClassLoader(),
				new Class<?>[] { ChannelConnection.class }, (proxy, method, args) -> switch (method.getName()) {
					case "remoteAddress" -> address;
					case "closeConnection" -> {
						closed.set(true);
						yield null;
					}
					default -> throw new AssertionError("Unexpected call: " + method.getName());
				});
	}

}
