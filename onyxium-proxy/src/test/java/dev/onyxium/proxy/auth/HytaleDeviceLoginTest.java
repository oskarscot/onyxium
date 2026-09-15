package dev.onyxium.proxy.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HytaleDeviceLoginTest {

	private HttpServer server;

	private URI base;

	private String identity;

	private TestTokens signing;

	private int expires = 300;

	private List<Map<String, Object>> profiles = List
		.of(Map.of("uuid", TestTokens.PLAYER_ID.toString(), "username", "Oskar"));

	private List<Map<String, Object>> polls = List.of(Map.of("access_token", "oauth-secret"));

	private final AtomicInteger pollCount = new AtomicInteger();

	private final AtomicInteger sessions = new AtomicInteger();

	private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();

	private final List<Duration> waits = new ArrayList<>();

	private final MutableClock clock = new MutableClock();

	@Before
	public void start() throws Exception {
		signing = new TestTokens("device-login");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
		identity = signing.serverIdentity(base.toString());
		server.createContext("/", exchange -> {
			try {
				var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				var status = 200;
				Map<String, Object> response;
				switch (exchange.getRequestURI().getPath()) {
					case "/oauth2/device/auth" -> {
						assertEquals(Map.of("client_id", "hytale-server", "scope", "openid offline auth:server"),
								form(body));
						response = Map.of("device_code", "private-device-code", "user_code", "ABCD-EFGH",
								"verification_uri", "https://accounts.hytale.com/device", "expires_in", expires,
								"interval", 5);
					}
					case "/oauth2/token" -> {
						assertEquals(Map.of("client_id", "hytale-server", "grant_type",
								"urn:ietf:params:oauth:grant-type:device_code", "device_code", "private-device-code"),
								form(body));
						response = polls.get(Math.min(pollCount.getAndIncrement(), polls.size() - 1));
						if (response.containsKey("error"))
							status = 400;
					}
					case "/my-account/get-profiles" -> {
						assertEquals("GET", exchange.getRequestMethod());
						assertEquals("Bearer oauth-secret", exchange.getRequestHeaders().getFirst("Authorization"));
						response = Map.of("profiles", profiles);
					}
					case "/game-session/new" -> {
						assertEquals("POST", exchange.getRequestMethod());
						assertEquals("Bearer oauth-secret", exchange.getRequestHeaders().getFirst("Authorization"));
						assertEquals(Map.of("uuid", TestTokens.PLAYER_ID.toString()), JSONObjectUtils.parse(body));
						sessions.incrementAndGet();
						status = 201;
						response = Map.of("sessionToken", "game-session-secret", "identityToken", identity);
					}
					case "/.well-known/jwks.json" -> response = JSONObjectUtils.parse(signing.jwks());
					default -> throw new AssertionError("Unexpected request");
				}
				var bytes = JSONObjectUtils.toJSONString(response).getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(status, bytes.length);
				exchange.getResponseBody().write(bytes);
			}
			catch (Throwable failure) {
				serverFailure.set(failure);
				exchange.sendResponseHeaders(500, -1);
			}
			finally {
				exchange.close();
			}
		});
		server.start();
	}

	@After
	public void stop() {
		server.stop(0);
		assertNull(serverFailure.get());
	}

	@Test
	public void pollsWithBackoffCreatesValidatedSessionAndGeneratesAudience() {
		polls = List.of(Map.of("error", "authorization_pending"), Map.of("error", "slow_down"),
				Map.of("access_token", "oauth-secret"));
		try (var login = login()) {
			var result = login.login(prompt -> {
				assertEquals("ABCD-EFGH", prompt.userCode());
				assertEquals("https://accounts.hytale.com/device", prompt.verificationUri());
				assertFalse(prompt.toString().contains("private-device-code"));
			}, profiles -> {
				throw new AssertionError("Single profile should be selected automatically");
			});
			assertEquals(List.of(Duration.ofSeconds(15), Duration.ofSeconds(15), Duration.ofSeconds(20)), waits);
			assertEquals("game-session-secret", result.sessionToken());
			assertEquals(identity, result.identityToken());
			UUID.fromString(result.audience());
			assertFalse(result.toString().contains("game-session-secret"));
			var second = login.login(prompt -> {
			}, profiles -> null);
			assertNotEquals(result.audience(), second.audience());
			assertEquals(2, sessions.get());
		}
	}

	@Test
	public void letsOperatorSelectFromMultipleProfiles() {
		profiles = List.of(Map.of("uuid", UUID.randomUUID().toString(), "username", "Other"), profiles.getFirst());
		try (var login = login()) {
			login.login(prompt -> {
			}, choices -> {
				assertEquals(2, choices.size());
				return choices.get(1).uuid();
			});
			assertEquals(1, sessions.get());
		}
	}

	@Test
	public void rejectsProfilesOutsideTheAccountsList() {
		profiles = List.of(profiles.getFirst(), Map.of("uuid", UUID.randomUUID().toString(), "username", "Other"));
		try (var login = login()) {
			assertThrows(AuthenticationException.class, () -> login.login(prompt -> {
			}, choices -> UUID.randomUUID()));
			assertEquals(0, sessions.get());
		}
	}

	@Test
	public void deniedLoginNeverCreatesSession() {
		polls = List.of(Map.of("error", "access_denied", "error_description", "secret-must-not-be-logged"));
		try (var login = login()) {
			var exception = assertThrows(AuthenticationException.class, () -> login.login(prompt -> {
			}, choices -> null));
			assertTrue(exception.getMessage().contains("denied"));
			assertFalse(exception.getMessage().contains("secret-must-not-be-logged"));
			assertEquals(0, sessions.get());
		}
	}

	@Test
	public void stopsPollingAtDeviceExpiry() {
		expires = 20;
		polls = List.of(Map.of("error", "authorization_pending"));
		try (var login = login()) {
			var exception = assertThrows(AuthenticationException.class, () -> login.login(prompt -> {
			}, choices -> null));
			assertTrue(exception.getMessage().contains("expired"));
			assertEquals(1, pollCount.get());
			assertEquals(0, sessions.get());
		}
	}

	@Test
	public void refusesClientIdentityAsServerCredentials() throws Exception {
		identity = signing.identity(base.toString());
		try (var login = login()) {
			assertThrows(AuthenticationException.class, () -> login.login(prompt -> {
			}, choices -> null));
		}
	}

	private HytaleDeviceLogin login() {
		return new HytaleDeviceLogin(base, base, base, clock, duration -> {
			waits.add(duration);
			clock.now = clock.now.plus(duration);
		});
	}

	private static Map<String, String> form(String body) {
		return Arrays.stream(body.split("&"))
			.map(entry -> entry.split("=", 2))
			.collect(Collectors.toMap(entry -> URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
					entry -> URLDecoder.decode(entry[1], StandardCharsets.UTF_8)));
	}

	private static final class MutableClock extends Clock {

		private Instant now = Instant.now();

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
