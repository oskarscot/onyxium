package dev.onyxium.eventbus;

import java.util.Arrays;
import java.util.Comparator;

/// Stores and invokes subscribers for one event type in descending priority order.
///
/// @param <T> the event type
public class EventHandler<T extends Event> {

	private volatile EventRegistration[] registrations = new EventRegistration[0];

	/// Adds a subscriber, preserving registration order for equal priorities.
	/// Calls to this method must not run concurrently.
	///
	/// @param eventRegistration the subscriber to add
	public void register(EventRegistration eventRegistration) {
		var current = registrations;

		var updated = Arrays.copyOf(current, current.length + 1);
		updated[current.length] = eventRegistration;

		Arrays.sort(updated, Comparator.comparingInt(EventRegistration::priority).reversed());

		registrations = updated;
	}

	/// Invokes a snapshot of the registered subscribers on the calling thread.
	/// Cancellation does not skip subscribers; a subscriber failure stops delivery.
	///
	/// @param event the event to deliver
	/// @throws RuntimeException if a subscriber invocation fails, wrapping the cause
	public void dispatch(T event) {
		var snapshot = registrations;

		for (var registration : snapshot) {
			try {
				registration.handle().invoke(event);
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		}
	}
}
