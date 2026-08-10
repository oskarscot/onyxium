package dev.onyxium.proxy.io.packet;

import io.netty.buffer.ByteBuf;

public record UnknownPacket(int id, byte[] bytes) implements Packet {

    @Override
    public void serialize(ByteBuf bytes) {
        throw new UnsupportedOperationException("Cannot serialize an unknown packet!");
    }

}
