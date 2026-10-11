package dev.onyxium.proxy.api.command;

import dev.onyxium.proxy.api.message.FormattedMessage;

/// The caller executing a command, such as a player or the console.
public interface CommandSource {

	/// {@return whether this source has the requested permission}
	///
	/// @param permission the permission to check
	boolean hasPermission(String permission);

	/// Sends raw response text through the same delivery path as formatted replies.
	default void sendMessage(String message) {
		sendMessage(FormattedMessage.text(message));
	}

	/// Delivers the complete message to this source. Implementations decide how to render
	/// it: players retain Hytale formatting, while the proxy console extracts plain text.
	void sendMessage(FormattedMessage message);

}
