package dev.onyxium.proxy.io.packet;

import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import dev.onyxium.proxy.io.packet.auth.AuthGrant;
import dev.onyxium.proxy.io.packet.auth.AuthToken;
import dev.onyxium.proxy.io.packet.auth.Connect;
import dev.onyxium.proxy.io.packet.auth.PasswordAccepted;
import dev.onyxium.proxy.io.packet.auth.PasswordRejected;
import dev.onyxium.proxy.io.packet.auth.PasswordResponse;
import dev.onyxium.proxy.io.packet.auth.ServerAuthToken;
import dev.onyxium.proxy.io.packet.chat.ChatMessage;
import dev.onyxium.proxy.io.packet.chat.ServerMessage;
import dev.onyxium.proxy.io.packet.connection.ClientDisconnect;
import dev.onyxium.proxy.io.packet.connection.Ping;
import dev.onyxium.proxy.io.packet.connection.Pong;
import dev.onyxium.proxy.io.packet.connection.ServerDisconnect;

public final class PacketRegistry {

	private static final Int2ObjectMap<PacketInfo> PACKETS = new Int2ObjectOpenHashMap<>();

	static {
		register(0, 46, 38056, Connect::deserialize);
		register(1, 2, 2, ClientDisconnect::deserialize);
		register(2, 2, PacketDecoder.FORWARDING_MAX_FRAME_SIZE, ServerDisconnect::deserialize);
		register(3, 28, 28, Ping::deserialize);
		register(4, 19, 19, Pong::deserialize);
		register(11, 9, 49171, AuthGrant::deserialize);
		register(12, 9, 49171, AuthToken::deserialize);
		register(13, 9, 32851, ServerAuthToken::deserialize);
		register(15, 1, 70, PasswordResponse::deserialize);
		register(16, 0, 0, ignored -> new PasswordAccepted());
		register(17, 5, 74, PasswordRejected::deserialize);
		register(210, 2, PacketDecoder.FORWARDING_MAX_FRAME_SIZE, ServerMessage::deserialize);
		register(211, 1, 1026, ChatMessage::deserialize);
	}

	private PacketRegistry() {
	}

	public static PacketInfo findById(int id) {
		return PACKETS.get(id);
	}

	private static void register(int id, int minSize, int maxSize, Function<ByteBuf, Packet> factory) {
		PACKETS.put(id, new PacketInfo(minSize, maxSize, factory));
	}

	public record PacketInfo(int minSize, int maxSize, Function<ByteBuf, Packet> factory) {
	}

}
