package dev.onyxium.eventbus;

import org.junit.Test;

public class EventBusTest {

	@Test
	public void testEventBus() {
		var eventBus = new EventBus();
		eventBus.registerHandler(new SampleHandler());
		eventBus.post(new SampleEvent("Hello World!"));
	}
}
