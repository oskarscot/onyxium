package dev.onyxium.proxy.api.player;

import java.net.SocketAddress;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import dev.onyxium.proxy.api.message.FormattedMessage;

/// An authenticated player's identity and connection to the proxy.
public interface Player {

	/// Returns the player's verified UUID.
	///
	/// @return the authenticated account's UUID
	UUID uuid();

	/// Returns the player's verified username.
	///
	/// @return the username established during authentication
	String username();

	/// Returns the client's remote socket address.
	///
	/// @return the address of the client's connection to the proxy
	SocketAddress remoteAddress();

	/// Returns whether the client's connection is currently active.
	///
	/// @return `true` if the connection is active
	boolean active();

	/// Schedules disconnection with a formatted reason. Safe to call from any thread.
	///
	/// @param reason the message to display to the player
	void disconnect(@NotNull FormattedMessage reason);

	/// Schedules disconnection with a plain-text reason. Safe to call from any thread.
	///
	/// @param reason the message to display to the player
	default void disconnect(@NotNull String reason) {
		disconnect(FormattedMessage.text(reason));
	}

	/// Sends the [FormattedMessage] to the specified player
	///
	/// @param message the message to send to the player
	void sendMessage(@NotNull FormattedMessage message);

}
