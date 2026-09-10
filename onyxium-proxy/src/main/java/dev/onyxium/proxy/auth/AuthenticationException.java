package dev.onyxium.proxy.auth;

/// Contains a safe diagnostic, never token contents or an HTTP response body.
public final class AuthenticationException extends RuntimeException {

	public AuthenticationException(String message) {
		super(message);
	}

}
