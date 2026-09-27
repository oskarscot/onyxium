package dev.onyxium.proxy.api.event;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import dev.onyxium.eventbus.CancellableEvent;
import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.api.player.Player;

/// Fired after authentication and any password challenge, before registration
/// and backend connection. Cancelling this event rejects the login.
public final class PostLoginEvent implements CancellableEvent {

	private final Player player;

	private boolean cancelled;

	private FormattedMessage cancelledMessage = FormattedMessage.text("Connection blocked");

	public PostLoginEvent(@NotNull Player player) {
		this.player = Objects.requireNonNull(player, "player");
	}

	/// Returns the authenticated player attempting to log in.
	@NotNull
	public Player player() {
		return this.player;
	}

	/// Returns the reason sent to the client if the login is cancelled.
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
