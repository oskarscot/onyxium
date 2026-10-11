package dev.onyxium.command;

/// The caller executing a command, such as a player or the console.
public interface CommandSource {

	/// {@return whether this source has the requested permission}
	///
	/// @param permission the permission to check
	boolean hasPermission(String permission);

	/// Sends a command response to this source.
	///
	/// @param message the response text
	void sendMessage(String message);

}
