package dev.onyxium.eventbus;

public class SampleEvent implements Event {

	private String text;

	public SampleEvent(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

}
