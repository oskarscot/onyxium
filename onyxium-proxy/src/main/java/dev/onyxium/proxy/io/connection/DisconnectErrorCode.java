package dev.onyxium.proxy.io.connection;

public enum DisconnectErrorCode {

	NO_ERROR(0), RATE_LIMITED(1), AUTH_FAILED(2), INVALID_VERSION(3), TIMEOUT(4), CLIENT_OUTDATED(5),
	SERVER_OUTDATED(6), CRASH(7);

	private final int code;

	DisconnectErrorCode(int code) {
		this.code = code;
	}

	public int code() {
		return code;
	}

}
