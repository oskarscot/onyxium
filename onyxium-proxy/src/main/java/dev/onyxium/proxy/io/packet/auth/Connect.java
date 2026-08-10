package dev.onyxium.proxy.io.packet.auth;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dev.onyxium.proxy.io.packet.ClientType;
import dev.onyxium.proxy.io.packet.HostAddress;
import dev.onyxium.proxy.io.packet.Packet;
import io.netty.buffer.ByteBuf;

public record Connect(
    int protocolCrc,
    int protocolBuildNumber,
    String clientVersion,
    ClientType clientType,
    UUID uuid,
    @Nullable String language,
    @Nullable String identityToken,
    String username,
    @Nullable byte[] referralData,
    @Nullable HostAddress referralSource
) implements Packet {

    public static Connect deserialize(ByteBuf buf) {
        throw new UnsupportedOperationException("Unimplemented method 'serialize'");
    }

    @Override
    public void serialize(ByteBuf buffer) {
        throw new UnsupportedOperationException("Unimplemented method 'serialize'");
    }

    @Override
    public int id() {
        return 0;
    }

}
