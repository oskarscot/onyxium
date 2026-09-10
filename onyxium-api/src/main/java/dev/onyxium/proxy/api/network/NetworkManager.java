package dev.onyxium.proxy.api.network;

import org.jetbrains.annotations.NotNull;

public interface NetworkManager {

	@NotNull
	NetworkInfo networkInfo();

	boolean running();

}
