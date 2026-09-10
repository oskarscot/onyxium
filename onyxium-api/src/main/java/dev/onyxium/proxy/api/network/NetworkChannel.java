package dev.onyxium.proxy.api.network;

import org.jetbrains.annotations.Nullable;

public enum NetworkChannel {

	DEFAULT, CHUNKS, WORLD_MAP, VOICE;

	/// Only stream zero has an implicit channel. Auxiliary streams identify their type
	/// with
	/// StreamOpen; their QUIC stream IDs do not identify the channel.
	public static @Nullable NetworkChannel fromStreamId(long streamId) {
		return streamId == 0 ? DEFAULT : null;
	}

}
