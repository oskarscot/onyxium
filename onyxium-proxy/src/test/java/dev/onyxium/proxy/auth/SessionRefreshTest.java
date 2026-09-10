package dev.onyxium.proxy.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

public class SessionRefreshTest {

	@Test
	public void refreshesExpiringCredentialsWithoutAPlayerConnection() throws Exception {
		var tokens = new TestTokens("refresh-key");
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var issuer = "http://127.0.0.1:" + server.getAddress().getPort();
		var expiredSoon = tokens.sign(tokens.claims(issuer)
			.claim("scope", "hytale:server")
			.expirationTime(Date.from(Instant.now().plusSeconds(200)))
			.build());
		var refreshedIdentity = tokens.serverIdentity(issuer);
		var requests = new AtomicInteger();
		var failure = new AtomicReference<Throwable>();
		server.createContext("/", exchange -> {
			try {
				String response;
				if (exchange.getRequestURI().getPath().equals("/.well-known/jwks.json")) {
					response = tokens.jwks();
				}
				else {
					assertEquals("/game-session/refresh", exchange.getRequestURI().getPath());
					assertEquals("POST", exchange.getRequestMethod());
					assertEquals("Bearer old-session", exchange.getRequestHeaders().getFirst("Authorization"));
					requests.incrementAndGet();
					response = JSONObjectUtils
						.toJSONString(Map.of("sessionToken", "new-session", "identityToken", refreshedIdentity));
				}
				var bytes = response.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, bytes.length);
				exchange.getResponseBody().write(bytes);
			}
			catch (Throwable exception) {
				failure.set(exception);
				exchange.sendResponseHeaders(500, -1);
			}
			finally {
				exchange.close();
			}
		});
		server.start();
		try (var service = new HytaleAuthenticationService(
				new AuthConfiguration(URI.create(issuer), "proxy", "old-session", expiredSoon))) {
			service.maintainSession().toCompletableFuture().get(10, TimeUnit.SECONDS);
			service.maintainSession().toCompletableFuture().get(10, TimeUnit.SECONDS);
			assertEquals(1, requests.get());
			assertNull(failure.get());
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	public void rejectsHttpFailuresWithoutExposingResponseBody() throws Exception {
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			var bytes = "sensitive-body-must-not-escape".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(403, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		try (var client = new SessionServiceClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()))) {
			var exception = assertThrows(AuthenticationException.class,
					() -> client.requestGrant("identity", "proxy", "session"));
			assertTrue(exception.getMessage().contains("403"));
			assertFalse(exception.getMessage().contains("sensitive-body"));
		}
		finally {
			server.stop(0);
		}
	}

}
