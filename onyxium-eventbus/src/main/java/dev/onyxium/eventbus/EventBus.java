package dev.onyxium.eventbus;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EventBus {

	private final Map<Class<? extends Event>, EventHandler<? extends Event>> eventMap =
		new LinkedHashMap<>();

	public void registerHandler(Object handler) {
		for (var method : handler.getClass().getDeclaredMethods()) {
			var subscribe = method.getAnnotation(Subscribe.class);

			if (subscribe == null) {
				continue;
			}

			var parameters = method.getParameterTypes();

			if (parameters.length != 1) {
				throw new IllegalArgumentException(
					"Subscriber methods must take exactly one event"
				);
			}

			var eventType = parameters[0];

			if (!Event.class.isAssignableFrom(eventType)) {
				throw new IllegalArgumentException(
					"Subscriber parameter must implement Event"
				);
			}

			@SuppressWarnings("unchecked")
			var eventClass = (Class<? extends Event>) eventType;

			var eventHandler = eventMap.computeIfAbsent(
				eventClass,
				ignored -> new EventHandler<>()
			);

			var registration = new EventRegistration(
				handler,
				method,
				subscribe.value()
			);

			eventHandler.register(registration);
		}
	}

	public void post(Event event) {
		var handler = eventMap.get(event.getClass());

		if (handler == null) {
			return;
		}

		dispatch(handler, event);
	}

	@SuppressWarnings("unchecked")
	private <T extends Event> void dispatch(
		EventHandler<?> handler,
		T event
	) {
		((EventHandler<T>) handler).dispatch(event);
	}
}
