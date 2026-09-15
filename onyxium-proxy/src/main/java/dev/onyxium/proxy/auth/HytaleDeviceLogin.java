package dev.onyxium.proxy.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nimbusds.jose.util.JSONObjectUtils;

public final class HytaleDeviceLogin implements AutoCloseable {

	private final URI oauth;

	private final URI accounts;

	private final URI sessions;

	private final Clock clock;

	private final Sleeper sleeper;

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	public HytaleDeviceLogin() {
		this(URI.create("https://oauth.accounts.hytale.com"), URI.create("https://account-data.hytale.com"),
				AuthConfiguration.SESSION_SERVICE, Clock.systemUTC(), Thread::sleep);
	}

	HytaleDeviceLogin(URI oauth, URI accounts, URI sessions, Clock clock, Sleeper sleeper) {
		this.oauth = oauth;
		this.accounts = accounts;
		this.sessions = sessions;
		this.clock = clock;
		this.sleeper = sleeper;
	}

	public AuthConfiguration login(Consumer<DevicePrompt> prompt, Function<List<GameProfile>, UUID> selectProfile) {
		var device = form("/oauth2/device/auth",
				Map.of("client_id", "hytale-server", "scope", "openid offline auth:server"), false);
		var code = string(device, "device_code");
		var expires = seconds(device, "expires_in", 1);
		var interval = Math.max(15, seconds(device, "interval", 5));
		var deadline = clock.instant().plusSeconds(expires);
		prompt.accept(new DevicePrompt(string(device, "user_code"), string(device, "verification_uri"), expires));
		while (clock.instant().isBefore(deadline)) {
			try {
				sleeper.sleep(Duration.ofSeconds(interval));
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AuthenticationException("Hytale device login interrupted");
			}
			if (!clock.instant().isBefore(deadline))
				break;
			var token = form("/oauth2/token", Map.of("client_id", "hytale-server", "grant_type",
					"urn:ietf:params:oauth:grant-type:device_code", "device_code", code), true);
			if (token.containsKey("error")) {
				switch (string(token, "error")) {
					case "authorization_pending" -> {
						continue;
					}
					case "slow_down" -> {
						interval = Math.min(60, interval + 5);
						continue;
					}
					case "access_denied" -> throw new AuthenticationException("Hytale device login was denied");
					case "expired_token" ->
						throw new AuthenticationException("Hytale device login expired, restart to try again");
					default -> throw new AuthenticationException("Hytale device login failed");
				}
			}
			return createSession(string(token, "access_token"), selectProfile);
		}
		throw new AuthenticationException("Hytale device login expired; restart to try again");
	}

	// TODO: store the credentials so we dont have to re-auth every time
	private AuthConfiguration createSession(String accessToken, Function<List<GameProfile>, UUID> selectProfile) {
		var response = request(HttpRequest.newBuilder(accounts.resolve("/my-account/get-profiles"))
			.header("Authorization", "Bearer " + accessToken), false);
		if (!(response.get("profiles") instanceof List<?> entries) || entries.isEmpty()) {
			throw new AuthenticationException("No Hytale game profiles are available for this account");
		}
		var profiles = new ArrayList<GameProfile>();
		for (var entry : entries) {
			if (!(entry instanceof Map<?, ?> profile))
				throw invalidResponse();
			try {
				var uuid = UUID.fromString(string(profile, "uuid"));
				var username = string(profile, "username");
				if (!username.matches("[A-Za-z0-9_]{1,16}"))
					throw invalidResponse();
				profiles.add(new GameProfile(uuid, username));
			}
			catch (IllegalArgumentException exception) {
				throw invalidResponse();
			}
		}
		var profile = profiles.size() == 1 ? profiles.getFirst().uuid() : selectProfile.apply(List.copyOf(profiles));
		if (profiles.stream().noneMatch(candidate -> candidate.uuid().equals(profile))) {
			throw new AuthenticationException("Select one of the Hytale profiles shown in the console");
		}
		try (var service = new SessionServiceClient(sessions)) {
			var session = service.createSession(accessToken, profile);
			var credentials = new AuthConfiguration(sessions, UUID.randomUUID().toString(),
					string(session, "sessionToken"), string(session, "identityToken"));
			var validator = new JwtValidator(sessions.toString(), credentials.audience(), service::jwks, clock);
			var expiry = validator.validateServerIdentity(credentials.identityToken());
			if (!clock.instant().isBefore(expiry))
				throw new AuthenticationException("Hytale returned an expired server session");
			return credentials;
		}
	}

	private Map<String, Object> form(String path, Map<String, String> values, boolean polling) {
		var body = values.entrySet()
			.stream()
			.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
			.collect(Collectors.joining("&"));
		return request(HttpRequest.newBuilder(oauth.resolve(path))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body)), polling);
	}

	private Map<String, Object> request(HttpRequest.Builder request, boolean polling) {
		request.timeout(Duration.ofSeconds(30))
			.header("Accept", "application/json")
			.header("User-Agent", "Onyxium/0.0.1");
		try {
			var response = client.send(request.build(),
					HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), 1024 * 1024));
			if (response.statusCode() != 200 && !(polling && response.statusCode() == 400)) {
				throw new AuthenticationException("Hytale login request failed (HTTP " + response.statusCode() + ")");
			}
			var object = JSONObjectUtils.parse(new String(response.body(), StandardCharsets.UTF_8));
			if (response.statusCode() == 400 && !object.containsKey("error"))
				throw invalidResponse();
			return object;
		}
		catch (IOException exception) {
			throw new AuthenticationException("Hytale login service unavailable");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AuthenticationException("Hytale device login interrupted");
		}
		catch (ParseException exception) {
			throw invalidResponse();
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String string(Map<?, ?> object, String key) {
		if (object.get(key) instanceof String value && !value.isBlank())
			return value;
		throw invalidResponse();
	}

	private static int seconds(Map<String, Object> object, String key, int fallback) {
		if (!object.containsKey(key))
			return fallback;
		if (object.get(key) instanceof Number value && value.intValue() >= 1 && value.intValue() <= 3600)
			return value.intValue();
		throw invalidResponse();
	}

	private static AuthenticationException invalidResponse() {
		return new AuthenticationException("Hytale login service returned an invalid response");
	}

	@Override
	public void close() {
		client.shutdownNow();
	}

	public record DevicePrompt(String userCode, String verificationUri, int expiresIn) {
	}

	public record GameProfile(UUID uuid, String username) {
	}

	@FunctionalInterface
	interface Sleeper {

		void sleep(Duration duration) throws InterruptedException;

	}

}
