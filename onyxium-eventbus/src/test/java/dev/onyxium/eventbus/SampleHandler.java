package dev.onyxium.eventbus;

public class SampleHandler {

	@Subscribe(10)
	public void handleSample(SampleEvent event) {
		IO.println("Hello SampleEvent: " + event.getText());
		event.setText("bar");
		IO.println("New text: " + event.getText());
	}

	@Subscribe(5)
	public void handleFirst(SampleEvent event) {
		IO.println("This should call second");
	}

}
