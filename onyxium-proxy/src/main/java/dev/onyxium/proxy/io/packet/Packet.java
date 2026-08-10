package dev.onyxium.proxy.io.packet;

import io.netty.buffer.ByteBuf;

public sealed interface Packet permits KnownPacket, UnknownPacket {
    
    void serialize(ByteBuf bytes);
}
