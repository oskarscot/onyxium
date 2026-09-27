package dev.onyxium.eventbus;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class EventBusTest {

	@Test
	public void testEventBus() {
		var eventBus = new EventBus();
		eventBus.registerHandler(new SampleHandler());
		eventBus.postEvent(new SampleEvent("Hello World!"));

		eventBus.registerHandler(new SampleCancellableHandler());

		var sampleEvent = new SampleCancellableEvent(5);
		eventBus.postEvent(sampleEvent);

		assertTrue(sampleEvent.isCancelled());
	}
}
