package dev.onyxium.proxy.auth;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

	public static AuthConfiguration fromEnvironment() {
		return fromEnvironment(System.getenv());
	}

	static AuthConfiguration fromEnvironment(Map<String, String> environment) {
		return new AuthConfiguration(SESSION_SERVICE,
				environment.getOrDefault("HYTALE_SERVER_AUDIENCE", UUID.randomUUID().toString()),
				environment.get("HYTALE_SERVER_SESSION_TOKEN"), environment.get("HYTALE_SERVER_IDENTITY_TOKEN"));
	}

	public boolean configured() {
		return sessionToken != null && !sessionToken.isBlank() && identityToken != null && !identityToken.isBlank();
	}

	@Override
	public String toString() {
		return "AuthConfiguration[audience=" + audience + ", configured=" + configured() + "]";
	}
}
