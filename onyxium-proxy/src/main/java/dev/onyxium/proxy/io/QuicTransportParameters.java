package dev.onyxium.proxy.io;

import java.time.Duration;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class QuicTransportParameters {

	public static final Duration IDLE_TIMEOUT = Duration.ofSeconds(30);

	public static final int MAX_CONCURRENT_BIDIRECTIONAL_STREAMS = 8;

	public static final int MAX_CONCURRENT_UNIDIRECTIONAL_STREAMS = 0;

	public static final long CONNECTION_BUFFER_SIZE = 512 * 1024L;

	public static final long STREAM_BUFFER_SIZE = 128 * 1024L;

	private QuicTransportParameters() {
	}

}
