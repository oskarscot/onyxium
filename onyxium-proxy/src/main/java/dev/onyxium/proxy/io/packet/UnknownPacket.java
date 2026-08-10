package dev.onyxium.proxy.io.packet;

import io.netty.buffer.ByteBuf;

public record UnknownPacket(byte[] bytes) implements Packet {

    @Override
    public void serialize(ByteBuf bytes) {
        throw new UnsupportedOperationException("Cannot serialize an unknown packet!");
    }

    @Override
    public int id() {
        return -1;
    }

}
