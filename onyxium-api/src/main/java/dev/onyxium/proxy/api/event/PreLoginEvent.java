package dev.onyxium.proxy.api.event;

import java.net.SocketAddress;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.onyxium.eventbus.CancellableEvent;
import dev.onyxium.proxy.api.message.FormattedMessage;

/// Fired after basic handshake checks, before any authentication-service calls.
/// Cancelling this event rejects the attempt.
///
/// **Warning:** [#uuid()] and [#username()] have not been verified and may be forged.
/// Use [PostLoginEvent] for verified identity. Unreadable or missing claims are `null`.
public final class PreLoginEvent implements CancellableEvent {

	private final SocketAddress remoteAddress;

	private final UUID uuid;

	private final String username;

	private boolean cancelled;

	private FormattedMessage cancelledMessage = FormattedMessage.text("Connection blocked");

	public PreLoginEvent(@NotNull SocketAddress remoteAddress, @Nullable UUID uuid, @Nullable String username) {
		this.remoteAddress = Objects.requireNonNull(remoteAddress, "remoteAddress");
		this.uuid = uuid;
		this.username = username;
	}

	/// Returns the remote address of the client attempting to log in.
	@NotNull
	public SocketAddress remoteAddress() {
		return this.remoteAddress;
	}

	/// Returns the unverified UUID, or `null` if missing or unreadable.
	@Nullable
	public UUID uuid() {
		return this.uuid;
	}

	/// Returns the unverified username, or `null` if missing or unreadable.
	@Nullable
	public String username() {
		return this.username;
	}

	/// Returns the reason sent to the client if the attempt is cancelled.
	@NotNull
	public FormattedMessage getCancelledMessage() {
		return this.cancelledMessage;
	}

	public void setFormattedMessage(@NotNull FormattedMessage cancelledMessage) {
		this.cancelledMessage = Objects.requireNonNull(cancelledMessage, "cancelledMessage");
	}

	@Override
	public boolean isCancelled() {
		return this.cancelled;
	}

	@Override
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

}
