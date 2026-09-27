package dev.onyxium.eventbus;

/// An event with a mutable cancellation flag. Cancellation does not stop delivery
/// to subscribers, the event's producer decides how to handle it.
public interface CancellableEvent extends Event {

	/// Returns whether this event is cancelled.
	///
	/// @return `true` if cancelled
	boolean isCancelled();

	/// Updates this event's cancellation state.
	///
	/// @param cancelled `true` to cancel, `false` to clear cancellation
	void setCancelled(boolean cancelled);

}
