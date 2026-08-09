package dev.onyxium.proxy;

import java.util.Objects;

import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.network.NetworkManager;
import dev.onyxium.proxy.io.KwikNetworkManager;
import dev.onyxium.proxy.lifecycle.Lifecycle;
import dev.onyxium.proxy.lifecycle.LifecycleException;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class OnyxiumProxy implements ProxyServer, Lifecycle {

    private final KwikNetworkManager networkManager;

    public OnyxiumProxy(@NotNull KwikNetworkManager networkManager) {
        this.networkManager = Objects.requireNonNull(networkManager, "networkManager");
    }

    @Override
    @NotNull
    public NetworkManager networkManager() {
        return this.networkManager;
    }

    @Override
    public void start() throws LifecycleException {
        this.networkManager.start();
    }

    @Override
    public void stop() {
        this.networkManager.stop();
    }
}
