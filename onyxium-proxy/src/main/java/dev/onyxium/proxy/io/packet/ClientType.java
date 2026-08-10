package dev.onyxium.proxy.io.packet;

public enum ClientType {
    GAME,
    EDITOR;

    public byte getId() {
        return (byte) this.ordinal();
    }

    public static ClientType getById(byte id) {
        if(id < 0 || id > 1) {
            throw new IllegalArgumentException("ClientType can only be 0 or 1");
        }
        return ClientType.values()[id];
    }
}
