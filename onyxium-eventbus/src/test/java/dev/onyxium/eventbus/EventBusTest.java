package dev.onyxium.eventbus;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
