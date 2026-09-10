package dev.onyxium.proxy.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.onyxium.proxy.io.packet.auth.AuthToken;
import dev.onyxium.proxy.io.packet.auth.Connect;

public final class HytaleAuthenticationService implements AuthenticationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(HytaleAuthenticationService.class);

	private final AuthConfiguration configuration;

	private final SessionServiceClient sessions;

	private final JwtValidator validator;

	private final Clock clock;

	private final ExecutorService workers = Executors
		.newThreadPerTaskExecutor(Thread.ofVirtual().name("onyxium-auth-", 0).factory());

	private final Semaphore capacity = new Semaphore(64);

	private final ScheduledExecutorService refreshTimer = Executors
		.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("onyxium-session-refresh").factory());

	private Credentials credentials;

	public HytaleAuthenticationService(AuthConfiguration configuration) {
		this(configuration, Clock.systemUTC());
	}

	HytaleAuthenticationService(AuthConfiguration configuration, Clock clock) {
		this.configuration = configuration;
		this.clock = clock;
		sessions = new SessionServiceClient(configuration.sessionService());
		validator = new JwtValidator(configuration.sessionService().toString(), configuration.audience(),
				sessions::jwks, clock);
		if (configuration.configured()) {
			refreshTimer.scheduleWithFixedDelay(() -> maintainSession().exceptionally(failure -> {
				LOGGER.warn("Unable to refresh proxy authentication" + " session");
				return null;
			}), 0, 1, TimeUnit.MINUTES);
		}
	}

	/// Validates and refreshes the proxy's credentials without requiring a player login.
	CompletionStage<Void> maintainSession() {
		return submit(() -> {
			credentials();
			return null;
		});
	}

	@Override
	public CompletionStage<Grant> requestGrant(Connect connect) {
		return submit(() -> {
			if (!configuration.configured())
				throw new AuthenticationException("Proxy authentication is not configured");
			var identity = validator.validateIdentity(connect.identityToken(), connect.clientType());
			var server = credentials();
			var grant = sessions.requestGrant(connect.identityToken(), configuration.audience(), server.sessionToken());
			return new Grant(identity, grant, server.identityToken());
		});
	}

	@Override
	public CompletionStage<String> authenticate(AuthToken token, AuthenticatedProfile identity,
			String clientFingerprint, String serverFingerprint) {
		return submit(() -> {
			validator.validateAccess(token.accessToken(), identity, clientFingerprint);
			if (token.serverAuthorizationGrant() == null || token.serverAuthorizationGrant().isBlank()) {
				throw new AuthenticationException("Mutual authentication is required");
			}
			if (serverFingerprint == null || serverFingerprint.isBlank()) {
				throw new AuthenticationException("Proxy certificate is unavailable");
			}
			return sessions.exchangeGrant(token.serverAuthorizationGrant(), serverFingerprint,
					credentials().sessionToken());
		});
	}

	private synchronized Credentials credentials() {
		if (credentials == null) {
			var expiry = validator.validateServerIdentity(configuration.identityToken());
			credentials = new Credentials(configuration.sessionToken(), configuration.identityToken(), expiry);
		}
		if (!clock.instant().isBefore(credentials.expiresAt().minus(Duration.ofMinutes(5)))) {
			var refreshed = sessions.refreshSession(credentials.sessionToken());
			var session = SessionServiceClient.requiredString(refreshed, "sessionToken");
			var identity = SessionServiceClient.requiredString(refreshed, "identityToken");
			var expiry = validator.validateServerIdentity(identity);
			if (!clock.instant().isBefore(expiry))
				throw new AuthenticationException("Proxy session expired");
			credentials = new Credentials(session, identity, expiry);
		}
		return credentials;
	}

	private <T> CompletionStage<T> submit(Supplier<T> operation) {
		if (!capacity.tryAcquire()) {
			return CompletableFuture.failedFuture(new AuthenticationException("Authentication service is busy"));
		}
		try {
			return CompletableFuture.supplyAsync(() -> {
				try {
					return operation.get();
				}
				finally {
					capacity.release();
				}
			}, workers);
		}
		catch (RejectedExecutionException exception) {
			capacity.release();
			return CompletableFuture.failedFuture(new AuthenticationException("Authentication service is stopped"));
		}
	}

	@Override
	public void close() {
		refreshTimer.shutdownNow();
		workers.shutdownNow();
		sessions.close();
	}

	private record Credentials(String sessionToken, String identityToken, Instant expiresAt) {
		@Override
		public String toString() {
			return "Credentials[redacted]";
		}
	}

}
