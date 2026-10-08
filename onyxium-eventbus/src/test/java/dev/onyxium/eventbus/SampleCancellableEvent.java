package dev.onyxium.eventbus;

public class SampleCancellableEvent implements CancellableEvent {

	private boolean cancelled;

	private int number;

	public SampleCancellableEvent(int number) {
		this.number = number;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

}
