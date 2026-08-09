package dev.onyxium.proxy.io;

import java.time.Duration;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
final class QuicTransportParameters {

    static final Duration IDLE_TIMEOUT = Duration.ofSeconds(30);

    static final int MAX_CONCURRENT_BIDIRECTIONAL_STREAMS = 8;

    static final int MAX_CONCURRENT_UNIDIRECTIONAL_STREAMS = 0;

    static final long CONNECTION_BUFFER_SIZE = 512 * 1024L;

    static final long STREAM_BUFFER_SIZE = 128 * 1024L;

    private QuicTransportParameters() {
    }
}
