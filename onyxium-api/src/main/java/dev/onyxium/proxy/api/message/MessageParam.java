package dev.onyxium.proxy.api.message;

/// Typed translation parameters. Nested messages use messageParams instead.
public sealed interface MessageParam {

	record Text(String value) implements MessageParam {
	}

	record Bool(boolean value) implements MessageParam {
	}

	record Decimal(double value) implements MessageParam {
	}

	record Int(int value) implements MessageParam {
	}

	record Long(long value) implements MessageParam {
	}

}
