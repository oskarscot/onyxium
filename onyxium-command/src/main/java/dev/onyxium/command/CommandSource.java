package dev.onyxium.command;

/// The host decides how messages are delivered and which permissions are granted.
public interface CommandSource {

	boolean hasPermission(String permission);

	void sendMessage(String message);

}
