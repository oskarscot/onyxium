package dev.onyxium.proxy.io.packet.auth;

import dev.onyxium.proxy.io.packet.KnownPacket;
import io.netty.buffer.ByteBuf;

public record Connect(

) implements KnownPacket {

    public static final int ID = 0;

    public static Connect deserialize(ByteBuf buf) {
        return new Connect();
    }

    @Override
    public void serialize(ByteBuf buffer) {
        throw new UnsupportedOperationException("Unimplemented method 'serialize'");
    }

    @Override
    public int id() {
        return Connect.ID;
    }

}
