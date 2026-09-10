package dev.onyxium.proxy.lifecycle;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class LifecycleException extends RuntimeException {

	public LifecycleException(@NotNull String message) {
		super(message);
	}

	public LifecycleException(@NotNull String message, @NotNull Throwable cause) {
		super(message, cause);
	}

}
