package dev.onyxium.proxy.lifecycle;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface Lifecycle {

    void start() throws LifecycleException;

    void stop();
}
