package dev.onyxium.proxy.io.packet;

import java.util.function.Function;

import dev.onyxium.proxy.io.packet.auth.Connect;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class PacketRegistry {

    private static final Int2ObjectMap<PacketInfo> packetMap = new Int2ObjectOpenHashMap<>();

    private PacketRegistry() { }

    static {
        registerPacket(0, Connect.class, Connect::deserialize);
    }

    public static PacketInfo findById(int id) {
        return packetMap.get(id);
    }

    public static <T extends Packet> void registerPacket(int id, Class<T> clazz, Function<ByteBuf, Packet> factory) {
        packetMap.put(id, new PacketInfo(id, clazz, factory));
    }

    static record PacketInfo(int id, Class<? extends Packet> packetClass, Function<ByteBuf, Packet> factory) { }
}
