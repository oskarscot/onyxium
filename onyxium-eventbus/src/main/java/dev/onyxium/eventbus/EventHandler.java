package dev.onyxium.eventbus;

import java.util.Arrays;
import java.util.Comparator;

public class EventHandler<T extends Event> {

	private volatile EventRegistration[] registrations = new EventRegistration[0];

	public void register(EventRegistration eventRegistration) {
		var current = registrations;

		var updated = Arrays.copyOf(current, current.length + 1);
		updated[current.length] = eventRegistration;

		Arrays.sort(updated, Comparator.comparingInt(EventRegistration::priotity).reversed());

		registrations = updated;
	}

	public void dispatch(T event) {
		var snapshot = registrations;

		for (var registration : snapshot) {
			try {
				registration.targetMethod().invoke(registration.instance(), event);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
