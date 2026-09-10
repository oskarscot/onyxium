package dev.onyxium.proxy.api.player;

import java.net.SocketAddress;
import java.util.UUID;

import dev.onyxium.proxy.api.message.FormattedMessage;

public interface Player {

	UUID uuid();

	String username();

	SocketAddress remoteAddress();

	boolean active();

	/// Safe to call from any thread.
	void disconnect(FormattedMessage reason);

	default void disconnect(String reason) {
		disconnect(FormattedMessage.text(reason));
	}

}
