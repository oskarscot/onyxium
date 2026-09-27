package dev.onyxium.eventbus;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

/// Dispatches events synchronously to subscribers for their exact runtime class.
/// Higher priorities run first. Registration must not run concurrently with
/// registration or posting.
public final class EventBus {

	private final Map<Class<? extends Event>, EventHandler<? extends Event>> eventMap = new HashMap<>();

	/// Registers accessible [Subscribe] instance methods declared by the handler's
	/// class. Each method must accept exactly one [Event] parameter; inherited
	/// methods are ignored. Registering the same handler again adds duplicate subscriptions.
	///
	/// @param handler the object containing subscriber methods
	/// @throws IllegalArgumentException if a subscriber has an invalid signature
	/// @throws RuntimeException if a subscriber method cannot be accessed
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
				_ -> new EventHandler<>()
			);

			var lookup = MethodHandles.lookup();

			try {
				var handle = lookup.unreflect(method).bindTo(handler);

				var registration = new EventRegistration(
					handle,
					subscribe.value()
				);
				eventHandler.register(registration);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/// Returns the [EventHandler] of type [T] for the specified eventClass.
	///
	/// @param eventClass the event class being handled by the EventHandler
	@SuppressWarnings("unchecked")
	public <T extends Event> EventHandler<T> getHandler(Class<T> eventClass) {
		return (EventHandler<T>) this.eventMap.get(eventClass);
	}

	/// Delivers the event on the calling thread. Does nothing if its exact class has
	/// no subscribers. A subscriber failure stops delivery to remaining subscribers.
	///
	/// @param event the event to deliver
	/// @throws RuntimeException if a subscriber throws, wrapping the cause
	public <T extends Event> void postEvent(T event) {
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
