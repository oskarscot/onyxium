package dev.onyxium.proxy.io.packet;

import io.netty.buffer.ByteBuf;

public interface Packet {
    
    int id();

    void serialize(ByteBuf bytes);
}
