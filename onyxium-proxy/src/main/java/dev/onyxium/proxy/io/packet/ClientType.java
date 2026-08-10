package dev.onyxium.proxy.io.packet;

public enum ClientType {
    GAME,
    EDITOR;

    public byte getId() {
        return (byte) this.ordinal();
    }

    public static ClientType getById(byte id) {
        return ClientType.values()[id];
    }
}
