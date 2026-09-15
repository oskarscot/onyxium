package dev.onyxium.proxy.auth;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.util.JSONObjectUtils;

/// Blocking HTTP operations run exclusively on the authentication service's virtual threads.
public final class SessionServiceClient implements AutoCloseable {

	private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

	private final URI baseUri;

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	public SessionServiceClient(URI baseUri) {
		this.baseUri = baseUri;
	}

	public String jwks() {
		return request("/.well-known/jwks.json", null, null);
	}

	public String requestGrant(String identityToken, String audience, String sessionToken) {
		return stringResponse("/server-join/auth-grant", sessionToken,
				Map.of("identityToken", identityToken, "aud", audience), "authorizationGrant");
	}

	public String exchangeGrant(String grant, String fingerprint, String sessionToken) {
		return stringResponse("/server-join/auth-token", sessionToken,
				Map.of("authorizationGrant", grant, "x509Fingerprint", fingerprint), "accessToken");
	}

	public Map<String, Object> refreshSession(String sessionToken) {
		return json(request("/game-session/refresh", sessionToken, ""));
	}

	public Map<String, Object> createSession(String accessToken, UUID profile) {
		return json(request("/game-session/new", accessToken,
				JSONObjectUtils.toJSONString(Map.of("uuid", profile.toString()))));
	}

	private String stringResponse(String path, String bearer, Map<String, Object> body, String field) {
		var response = json(request(path, bearer, JSONObjectUtils.toJSONString(body)));
		return requiredString(response, field);
	}

	static String requiredString(Map<String, Object> object, String field) {
		if (object.get(field) instanceof String value && !value.isBlank())
			return value;
		throw new AuthenticationException("Session service returned an invalid " + field);
	}

	private static Map<String, Object> json(String body) {
		try {
			return JSONObjectUtils.parse(body);
		}
		catch (ParseException exception) {
			throw new AuthenticationException("Session service returned invalid JSON");
		}
	}

	private String request(String path, String bearer, String body) {
		var builder = HttpRequest.newBuilder(baseUri.resolve(path))
			.timeout(Duration.ofSeconds(15))
			.header("Accept", "application/json")
			.header("User-Agent", "Onyxium/0.0.1");
		if (bearer != null)
			builder.header("Authorization", "Bearer " + bearer);
		if (body != null)
			builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
		try {
			var response = client.send(builder.build(),
					HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_RESPONSE_BYTES));
			if (response.statusCode() != 200 && !(path.equals("/game-session/new") && response.statusCode() == 201)) {
				throw new AuthenticationException(
						"Session service rejected request (HTTP " + response.statusCode() + ")");
			}
			return new String(response.body(), StandardCharsets.UTF_8);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AuthenticationException("Authentication interrupted");
		}
		catch (IOException exception) {
			throw new AuthenticationException("Session service unavailable");
		}
	}

	@Override
	public void close() {
		client.shutdownNow();
	}

}
