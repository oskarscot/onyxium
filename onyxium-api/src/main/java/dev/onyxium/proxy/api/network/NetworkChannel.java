package dev.onyxium.proxy.api.network;

import org.jetbrains.annotations.Nullable;

// @author: https://github.com/xyzeva/hyproxy/blob/main/proxy/src/main/java/ac/eva/hyproxy/io/HytaleConnection.java
public enum NetworkChannel {
    DEFAULT,
    CHUNKS,
    WORLD_MAP,
    VOICE;

    public static @Nullable NetworkChannel fromStreamId(long streamId) {
        if (streamId == 0) {
            return NetworkChannel.DEFAULT;
        }

        NetworkChannel[] values = NetworkChannel.values();
        long index = (streamId >> 2) + 1;

        if (index < 0 || index >= values.length) {
            return null;
        }

        return values[(int) index];
    }
}
