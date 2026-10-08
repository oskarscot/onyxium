package dev.onyxium.eventbus;

public class SampleCancellableHandler {

	@Subscribe(5)
	void higherEvent(SampleCancellableEvent event) {
		event.setNumber(10);
		IO.println("Setting event number to 10");
	}

	@Subscribe
	void event(SampleCancellableEvent event) {
		IO.println("Cancelling with number " + event.getNumber());
		event.setCancelled(true);
	}

}
