package dev.onyxium.proxy.auth;

import java.net.URI;
import java.util.Objects;

public record AuthConfiguration(URI sessionService, String audience, String sessionToken, String identityToken) {

	public static final URI SESSION_SERVICE = URI.create("https://sessions.hytale.com");

	public AuthConfiguration {
		Objects.requireNonNull(sessionService, "sessionService");
		Objects.requireNonNull(audience, "audience");
		if (audience.isBlank())
			throw new IllegalArgumentException("Empty server audience");
		if (!"https".equals(sessionService.getScheme()) && !("http".equals(sessionService.getScheme())
				&& ("127.0.0.1".equals(sessionService.getHost()) || "localhost".equals(sessionService.getHost())))) {
			throw new IllegalArgumentException("Session service must use HTTPS");
		}
	}

	public boolean configured() {
		return sessionToken != null && !sessionToken.isBlank() && identityToken != null && !identityToken.isBlank();
	}

	@Override
	public String toString() {
		return "AuthConfiguration[audience=" + audience + ", configured=" + configured() + "]";
	}
}
