package dev.onyxium.proxy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.nimbusds.jose.util.JSONObjectUtils;
import module java.base;
import module jdk.httpserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HytaleDeviceLoginTest {

	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	Path credentialsFile;

	AtomicInteger deviceRequests = new AtomicInteger();

	AtomicInteger refreshes = new AtomicInteger();

	String refreshToken = "refresh-secret";

	int refreshStatus = 200;

	String refreshError = "invalid_grant";

	int sessionStatus = 201;

	private HttpServer server;

	private URI base;

	private String identity;

	private TestTokens signing;

	private int expires = 300;

	private List<Map<String, Object>> profiles = List
		.of(Map.of("uuid", TestTokens.PLAYER_ID.toString(), "username", "Oskar"));

	private List<Map<String, Object>> polls = List.of(Map.of("access_token", "oauth-secret", "refresh_token", "refresh-secret"));

	private final AtomicInteger pollCount = new AtomicInteger();

	private final AtomicInteger sessions = new AtomicInteger();

	private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();

	private final List<Duration> waits = new ArrayList<>();

	private final MutableClock clock = new MutableClock();

	@Before
	public void start() throws Exception {
		credentialsFile = temporary.getRoot().toPath().resolve("onyxium-auth.json");
		signing = new TestTokens("device-login");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
		identity = signing.serverIdentity(base.toString());
		server.createContext("/", this::handle);
		server.start();
	}

	void handle(HttpExchange exchange) throws IOException {
		try {
			var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			var status = 200;
			Map<String, Object> response;
			switch (exchange.getRequestURI().getPath()) {
				case "/oauth2/device/auth" -> {
					deviceRequests.incrementAndGet();
					assertThat(form(body)).isEqualTo(Map.of("client_id", "hytale-server", "scope", "openid offline auth:server"));
					response = Map.of("device_code", "private-device-code", "user_code", "ABCD-EFGH",
							"verification_uri", "https://accounts.hytale.com/device", "expires_in", expires, "interval", 5);
				}
				case "/oauth2/token" -> {
					var values = form(body);
					if (values.get("grant_type").equals("refresh_token")) {
						assertThat(values).isEqualTo(Map.of("client_id", "hytale-server", "grant_type", "refresh_token",
								"refresh_token", refreshToken));
						refreshes.incrementAndGet();
						status = refreshStatus;
						if (status == 200) {
							refreshToken = "rotated-" + refreshes.get();
							response = Map.of("access_token", "oauth-secret", "refresh_token", refreshToken);
						}
						else {
							response = Map.of("error", refreshError, "error_description", "secret-must-not-be-logged");
						}
					}
					else {
						assertThat(values).isEqualTo(Map.of("client_id", "hytale-server", "grant_type",
								"urn:ietf:params:oauth:grant-type:device_code", "device_code", "private-device-code"));
						response = polls.get(Math.min(pollCount.getAndIncrement(), polls.size() - 1));
						if (response.containsKey("error")) {
							status = 400;
						}
					}
				}
				case "/my-account/get-profiles" -> {
					assertThat(exchange.getRequestMethod()).isEqualTo("GET");
					assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer oauth-secret");
					response = Map.of("profiles", profiles);
				}
				case "/game-session/new" -> {
					assertThat(exchange.getRequestMethod()).isEqualTo("POST");
					assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer oauth-secret");
					assertThat(JSONObjectUtils.parse(body)).isEqualTo(Map.of("uuid", TestTokens.PLAYER_ID.toString()));
					sessions.incrementAndGet();
					status = sessionStatus;
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
	}

	@After
	public void stop() {
		server.stop(0);
		assertNull(serverFailure.get());
	}

	@Test
	public void pollsWithBackoffCreatesValidatedSessionAndGeneratesAudience() {
		polls = List.of(Map.of("error", "authorization_pending"), Map.of("error", "slow_down"),
				Map.of("access_token", "oauth-secret", "refresh_token", "refresh-secret"));
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
	public void remembersProfileAndRotatesRefreshTokenAcrossRestarts() throws Exception {
		profiles = List.of(Map.of("uuid", UUID.randomUUID().toString(), "username", "Other"), profiles.getFirst());
		try (var login = login()) {
			login.login(_ -> {}, this::selectSecondProfile);
		}

		profiles = profiles.reversed();
		var first = restore();
		var second = restore();

		assertThat(first.sessionToken()).isEqualTo("game-session-secret");
		assertThat(second.identityToken()).isEqualTo(identity);
		assertThat(second.audience()).isNotEqualTo(first.audience());
		assertThat(deviceRequests.get()).isEqualTo(1);
		assertThat(refreshes.get()).isEqualTo(2);
		assertThat(sessions.get()).isEqualTo(3);
		assertThat(JSONObjectUtils.parse(Files.readString(credentialsFile)))
			.isEqualTo(Map.of("refreshToken", "rotated-2", "profile", TestTokens.PLAYER_ID.toString()));
		if (Files.getFileStore(credentialsFile).supportsFileAttributeView("posix")) {
			assertThat(Files.getPosixFilePermissions(credentialsFile)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
		}
	}

	UUID selectSecondProfile(List<HytaleDeviceLogin.GameProfile> choices) {
		assertThat(choices).hasSize(2);
		return choices.get(1).uuid();
	}

	@Test
	public void revokedRefreshTokenFallsBackToDeviceLogin() {
		new StoredLogin(refreshToken, TestTokens.PLAYER_ID).save(credentialsFile);
		refreshStatus = 400;

		try (var login = login()) {
			var result = login.login(_ -> {}, HytaleDeviceLoginTest::unexpectedSelection);
			assertThat(result.sessionToken()).isEqualTo("game-session-secret");
		}

		assertThat(refreshes.get()).isEqualTo(1);
		assertThat(deviceRequests.get()).isEqualTo(1);
		assertThat(StoredLogin.load(credentialsFile)).contains(new StoredLogin("refresh-secret", TestTokens.PLAYER_ID));
	}

	@Test
	public void serviceOutagePreservesSavedLoginWithoutDeviceAuthorization() throws Exception {
		new StoredLogin(refreshToken, TestTokens.PLAYER_ID).save(credentialsFile);
		var original = Files.readString(credentialsFile);
		refreshStatus = 503;

		assertThatThrownBy(this::restore).isInstanceOf(AuthenticationException.class)
			.hasMessage("Hytale login request failed (HTTP 503)");

		assertThat(Files.readString(credentialsFile)).isEqualTo(original);
		assertThat(deviceRequests.get()).isZero();
		assertThat(sessions.get()).isZero();
	}

	@Test
	public void persistsRotationEvenWhenSessionCreationFails() {
		new StoredLogin(refreshToken, TestTokens.PLAYER_ID).save(credentialsFile);
		sessionStatus = 503;

		assertThatThrownBy(this::restore).isInstanceOf(AuthenticationException.class)
			.hasMessage("Session service rejected request (HTTP 503)");
		assertThat(StoredLogin.load(credentialsFile)).contains(new StoredLogin("rotated-1", TestTokens.PLAYER_ID));

		sessionStatus = 201;
		assertThat(restore().sessionToken()).isEqualTo("game-session-secret");
		assertThat(StoredLogin.load(credentialsFile)).contains(new StoredLogin("rotated-2", TestTokens.PLAYER_ID));
		assertThat(deviceRequests.get()).isZero();
	}

	@Test
	public void replacesMalformedSavedLoginAfterDeviceAuthorization() throws Exception {
		Files.writeString(credentialsFile, "{malformed-secret");
		try (var login = login()) {
			login.login(_ -> {}, HytaleDeviceLoginTest::unexpectedSelection);
		}

		assertThat(deviceRequests.get()).isEqualTo(1);
		assertThat(StoredLogin.load(credentialsFile)).contains(new StoredLogin("refresh-secret", TestTokens.PLAYER_ID));
	}

	AuthConfiguration restore() {
		try (var login = login()) {
			return login.login(HytaleDeviceLoginTest::unexpectedPrompt, HytaleDeviceLoginTest::unexpectedSelection);
		}
	}

	static void unexpectedPrompt(HytaleDeviceLogin.DevicePrompt prompt) {
		throw new AssertionError("Saved login should not need device authorization");
	}

	static UUID unexpectedSelection(List<HytaleDeviceLogin.GameProfile> choices) {
		throw new AssertionError("Saved or single profile should not need selection");
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

	HytaleDeviceLogin login() {
		return new HytaleDeviceLogin(base, base, base, clock, this::sleep, credentialsFile);
	}

	void sleep(Duration duration) {
		waits.add(duration);
		clock.now = clock.now.plus(duration);
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
