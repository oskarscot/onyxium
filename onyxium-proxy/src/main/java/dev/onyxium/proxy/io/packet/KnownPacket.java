package dev.onyxium.proxy.io.packet;

import org.jetbrains.annotations.Nullable;

import dev.onyxium.proxy.io.packet.auth.Connect;
import io.netty.buffer.ByteBuf;

public non-sealed interface KnownPacket extends Packet {

    int id();

    @Nullable
    static KnownPacket decode(int id, ByteBuf buf) {
        return switch (id) {
            case Connect.ID -> Connect.deserialize(buf);
            default -> null;
        };
    }
}
