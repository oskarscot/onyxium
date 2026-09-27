package dev.onyxium.proxy.io.packet.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.EventExecutor;
import org.junit.Test;

import dev.onyxium.eventbus.EventBus;
import dev.onyxium.eventbus.Subscribe;
import dev.onyxium.proxy.api.ProxyServer;
import dev.onyxium.proxy.api.event.PostLoginEvent;
import dev.onyxium.proxy.api.event.PreLoginEvent;
import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.auth.AuthenticatedProfile;
import dev.onyxium.proxy.auth.AuthenticationService;
import dev.onyxium.proxy.io.connection.DisconnectErrorCode;
import dev.onyxium.proxy.io.connection.ProtocolConnection;
import dev.onyxium.proxy.io.packet.ClientType;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.ProtocolVersion;
import dev.onyxium.proxy.io.packet.auth.AuthGrant;
import dev.onyxium.proxy.io.packet.auth.AuthToken;
import dev.onyxium.proxy.io.packet.auth.Connect;
import dev.onyxium.proxy.io.packet.auth.PasswordAccepted;
import dev.onyxium.proxy.io.packet.auth.PasswordRejected;
import dev.onyxium.proxy.io.packet.auth.PasswordResponse;
import dev.onyxium.proxy.io.packet.auth.ServerAuthToken;
import dev.onyxium.proxy.player.PlayerRegistry;
import dev.onyxium.proxy.player.ProxyPlayer;

public class LoginHandlerTest {

	private static final AuthenticatedProfile PROFILE = new AuthenticatedProfile(UUID.randomUUID(), "Oskar", null,
			List.of());

	@Test
	public void rejectsTokenBeforeConnectAndVersionMismatch() {
		try (var connection = new TestConnection()) {
			var authentication = new AuthenticationFixture();
			connection.setPacketHandler(new HandshakePacketHandler(connection, context(authentication, null)));
			connection.receive(new AuthToken("access", "grant"));
			assertEquals(DisconnectErrorCode.AUTH_FAILED, connection.error);
			verifyNoInteractions(authentication.service);
		}
		try (var connection = new TestConnection()) {
			var authentication = new AuthenticationFixture();
			connection.setPacketHandler(new HandshakePacketHandler(connection, context(authentication, null)));
			connection.receive(new Connect(1, 1, "old", ClientType.GAME, "en-US", "identity", null, null));
			assertEquals(DisconnectErrorCode.CLIENT_OUTDATED, connection.error);
			verifyNoInteractions(authentication.service);
		}
	}

	@Test
	public void duplicateConnectAndDuplicateTokensFailClosed() {
		try (var connection = new TestConnection()) {
			var authentication = new AuthenticationFixture();
			connection.setPacketHandler(new HandshakePacketHandler(connection, context(authentication, null)));
			connection.receive(connect());
			connection.receive(connect());
			authentication.grant.complete(grant());
			connection.drain();
			assertFalse(connection.active());
			assertTrue(connection.writes.isEmpty());
		}
		try (var connection = new TestConnection()) {
			var authentication = new AuthenticationFixture();
			var login = context(authentication, null);
			start(connection, login, authentication);
			connection.receive(new AuthToken("access", "server-grant"));
			connection.receive(new AuthToken("access", "server-grant"));
			authentication.access.complete("server-access");
			connection.drain();
			assertFalse(connection.active());
			assertTrue(login.players().players().isEmpty());
		}
	}

	@Test
	public void lateHttpCompletionCannotRegisterClosedPlayer() {
		try (var connection = new TestConnection()) {
			var authentication = new AuthenticationFixture();
			var login = context(authentication, null);
			start(connection, login, authentication);
			connection.receive(new AuthToken("access", "server-grant"));
			connection.close();
			authentication.access.complete("server-access");
			connection.drain();
			assertEquals(1, connection.writes.size());
			assertTrue(login.players().players().isEmpty());
		}
	}

	@Test
	public void initialAndAuthenticationTimeoutsCloseConnection() {
		try (var connection = new TestConnection()) {
			connection
				.setPacketHandler(new HandshakePacketHandler(connection, context(new AuthenticationFixture(), null)));
			connection.advance(11);
			assertEquals(DisconnectErrorCode.TIMEOUT, connection.error);
		}
		try (var connection = new TestConnection()) {
			connection
				.setPacketHandler(new HandshakePacketHandler(connection, context(new AuthenticationFixture(), null)));
			connection.receive(connect());
			connection.advance(46);
			assertEquals(DisconnectErrorCode.TIMEOUT, connection.error);
		}
	}

	@Test
	public void passwordRetriesRotateChallengesAndGatePlayerRegistration() throws Exception {
		try (var connection = new TestConnection()) {
			var authentication = new AuthenticationFixture();
			var login = context(authentication, "secret");
			var events = new ArrayList<PostLoginEvent>();
			login.proxy().eventBus().registerHandler(new LoginListener(events::add));
			start(connection, login, authentication);
			connection.receive(new AuthToken("access", "server-grant"));
			authentication.access.complete("server-access");
			connection.drain();
			var challenge = ((ServerAuthToken) connection.writes.getLast()).passwordChallenge();
			assertTrue(login.players().players().isEmpty());
			assertTrue(events.isEmpty());
			connection.receive(new PasswordResponse(new byte[32]));
			var retry = (PasswordRejected) connection.writes.getLast();
			assertEquals(2, retry.attemptsRemaining());
			assertTrue(events.isEmpty());
			assertFalse(MessageDigest.isEqual(challenge, retry.newChallenge()));
			var digest = MessageDigest.getInstance("SHA-256");
			digest.update(retry.newChallenge());
			connection.receive(new PasswordResponse(digest.digest("secret".getBytes(StandardCharsets.UTF_8))));
			assertTrue(connection.writes.getLast() instanceof PasswordAccepted);
			assertEquals(1, login.players().players().size());
			assertEquals(1, events.size());
			assertSame(login.players().player(PROFILE.uniqueId()).orElseThrow(), events.getFirst().player());
			connection.close();
			assertTrue(login.players().players().isEmpty());
		}
	}

	@Test
	public void cancelledPreLoginSkipsAuthenticationAndLaterEvents() {
		try (var connection = new TestConnection()) {
			var authentication = new AuthenticationFixture();
			var joined = new ArrayList<ProxyPlayer>();
			var login = context(authentication, null, joined::add);
			var preEvents = new ArrayList<PreLoginEvent>();
			var postEvents = new ArrayList<PostLoginEvent>();
			var reason = FormattedMessage.text("Too many login attempts");
			login.proxy().eventBus().registerHandler(new PreLoginListener(event -> {
				preEvents.add(event);
				event.setFormattedMessage(reason);
				event.setCancelled(true);
			}));
			login.proxy().eventBus().registerHandler(new LoginListener(postEvents::add));
			connection.setPacketHandler(new HandshakePacketHandler(connection, login));
			connection.receive(connect());
			assertEquals(1, preEvents.size());
			assertEquals(connection.remoteAddress(), preEvents.getFirst().remoteAddress());
			assertNull(preEvents.getFirst().uuid());
			assertNull(preEvents.getFirst().username());
			verifyNoInteractions(authentication.service);
			assertEquals(DisconnectErrorCode.AUTH_FAILED, connection.error);
			assertSame(reason, connection.disconnectReason);
			assertFalse(connection.active());
			assertTrue(connection.writes.isEmpty());
			assertTrue(login.players().players().isEmpty());
			assertTrue(postEvents.isEmpty());
			assertTrue(joined.isEmpty());
		}
	}

	@Test
	public void preLoginExposesUnverifiedClaimsWithoutBypassingAuthentication() {
		try (var connection = new TestConnection()) {
			var authentication = new AuthenticationFixture();
			var login = context(authentication, null);
			var preEvents = new ArrayList<PreLoginEvent>();
			var postEvents = new ArrayList<PostLoginEvent>();
			login.proxy().eventBus().registerHandler(new PreLoginListener(event -> {
				verifyNoInteractions(authentication.service);
				preEvents.add(event);
			}));
			login.proxy().eventBus().registerHandler(new LoginListener(postEvents::add));
			var claims = new JWTClaimsSet.Builder().subject(PROFILE.uniqueId().toString())
				.claim("profile", Map.of("username", PROFILE.username()))
				.build();
			var token = new JWSHeader(JWSAlgorithm.EdDSA).toBase64URL() + "." + Base64URL.encode(claims.toString())
					+ "." + Base64URL.encode(new byte[64]);
			connection.setPacketHandler(new HandshakePacketHandler(connection, login));
			connection.receive(connect(token));
			assertEquals(1, preEvents.size());
			assertEquals(PROFILE.uniqueId(), preEvents.getFirst().uuid());
			assertEquals(PROFILE.username(), preEvents.getFirst().username());
			verify(authentication.service).requestGrant(any(Connect.class));
			authentication.grant.completeExceptionally(new IllegalArgumentException("Invalid signature"));
			connection.drain();
			assertFalse(connection.active());
			assertTrue(login.players().players().isEmpty());
			assertTrue(postEvents.isEmpty());
		}
	}

	@Test
	public void cancelledPostLoginPreventsRegistrationAndAuthenticatedCallback() throws Exception {
		for (var passwordRequired : List.of(false, true)) {
			try (var connection = new TestConnection()) {
				var authentication = new AuthenticationFixture();
				var joined = new ArrayList<ProxyPlayer>();
				var login = context(authentication, passwordRequired ? "secret" : null, joined::add);
				var events = new ArrayList<PostLoginEvent>();
				var reason = FormattedMessage.text("This account is banned");
				login.proxy().eventBus().registerHandler(new LoginListener(event -> {
					events.add(event);
					event.setFormattedMessage(reason);
					event.setCancelled(true);
				}));
				start(connection, login, authentication);
				assertTrue(events.isEmpty());
				connection.receive(new AuthToken("access", "server-grant"));
				authentication.access.complete("server-access");
				connection.drain();
				if (passwordRequired) {
					assertTrue(events.isEmpty());
					var challenge = ((ServerAuthToken) connection.writes.getLast()).passwordChallenge();
					var digest = MessageDigest.getInstance("SHA-256");
					digest.update(challenge);
					connection.receive(new PasswordResponse(digest.digest("secret".getBytes(StandardCharsets.UTF_8))));
				}
				assertEquals(1, events.size());
				assertEquals(PROFILE.uniqueId(), events.getFirst().player().uuid());
				assertEquals(PROFILE.username(), events.getFirst().player().username());
				assertEquals(DisconnectErrorCode.AUTH_FAILED, connection.error);
				assertSame(reason, connection.disconnectReason);
				assertFalse(connection.active());
				assertTrue(login.players().players().isEmpty());
				assertTrue(joined.isEmpty());
			}
		}
	}

	@Test
	public void passwordFailureClosesAfterThreeAttempts() {
		try (var connection = new TestConnection()) {
			var authentication = new AuthenticationFixture();
			var login = context(authentication, "secret");
			start(connection, login, authentication);
			connection.receive(new AuthToken("access", "server-grant"));
			authentication.access.complete("server-access");
			connection.drain();
			for (var i = 0; i < 3; i++) {
				connection.receive(new PasswordResponse(new byte[32]));
			}
			assertEquals(DisconnectErrorCode.AUTH_FAILED, connection.error);
			assertTrue(login.players().players().isEmpty());
		}
	}

	@Test
	public void duplicateLoginCannotReplaceOrRemoveExistingPlayer() {
		var authentication = new AuthenticationFixture();
		var login = context(authentication, null);
		var registry = login.players();
		var postEvents = new ArrayList<PostLoginEvent>();
		login.proxy().eventBus().registerHandler(new LoginListener(postEvents::add));
		try (var first = new TestConnection(); var second = new TestConnection()) {
			for (var connection : List.of(first, second)) {
				start(connection, login, authentication);
				connection.receive(new AuthToken("access", "server-grant"));
				authentication.access.complete("server-access");
				connection.drain();
			}
			assertTrue(first.active());
			assertFalse(second.active());
			assertEquals(2, postEvents.size());
			assertSame(first, registry.player(PROFILE.uniqueId()).orElseThrow().connection());
			first.close();
			assertTrue(registry.players().isEmpty());
		}
	}

	@Test
	public void replacingAuthenticatedHandlerCancelsBackendDeadline() {
		try (var connection = new TestConnection()) {
			var authentication = new AuthenticationFixture();
			var login = context(authentication, null);
			start(connection, login, authentication);
			connection.receive(new AuthToken("access", "server-grant"));
			authentication.access.complete("server-access");
			connection.drain();
			connection.setPacketHandler(new GenericPacketHandler(connection) {
				@Override
				protected void handle(Packet packet) {
				}
			});
			connection.advance(60);
			assertTrue(connection.active());
			connection.close();
			assertTrue(login.players().players().isEmpty());
		}
	}

	private static void start(TestConnection connection, LoginContext login, AuthenticationFixture authentication) {
		connection.setPacketHandler(new HandshakePacketHandler(connection, login));
		connection.receive(connect());
		authentication.grant.complete(grant());
		connection.drain();
		assertTrue(connection.writes.getLast() instanceof AuthGrant);
	}

	private static LoginContext context(AuthenticationFixture authentication, String password) {
		return context(authentication, password, player -> {
		});
	}

	private static LoginContext context(AuthenticationFixture authentication, String password,
			Consumer<ProxyPlayer> onAuthenticated) {
		var proxy = mock(ProxyServer.class);
		when(proxy.eventBus()).thenReturn(new EventBus());
		return new LoginContext(proxy, authentication.service, new PlayerRegistry(), ProtocolVersion.CURRENT, password,
				onAuthenticated);
	}

	private static Connect connect() {
		return connect("identity");
	}

	private static Connect connect(String identityToken) {
		return new Connect(ProtocolVersion.CURRENT.crc(), ProtocolVersion.CURRENT.buildNumber(), "test",
				ClientType.GAME, "en-US", identityToken, null, null);
	}

	private static AuthenticationService.Grant grant() {
		return new AuthenticationService.Grant(PROFILE, "grant", "server-identity");
	}

	public static final class PreLoginListener {

		private final Consumer<PreLoginEvent> listener;

		private PreLoginListener(Consumer<PreLoginEvent> listener) {
			this.listener = listener;
		}

		@Subscribe
		public void onPreLogin(PreLoginEvent event) {
			listener.accept(event);
		}

	}

	public static final class LoginListener {

		private final Consumer<PostLoginEvent> listener;

		private LoginListener(Consumer<PostLoginEvent> listener) {
			this.listener = listener;
		}

		@Subscribe
		public void onPostLogin(PostLoginEvent event) {
			listener.accept(event);
		}

	}

	private static final class AuthenticationFixture {

		private final AuthenticationService service = mock(AuthenticationService.class);

		private final CompletableFuture<AuthenticationService.Grant> grant = new CompletableFuture<>();

		private final CompletableFuture<String> access = new CompletableFuture<>();

		private AuthenticationFixture() {
			when(service.requestGrant(any(Connect.class))).thenReturn(grant);
			when(service.authenticate(any(AuthToken.class), eq(PROFILE), eq("client-certificate"),
					eq("server-certificate")))
				.thenReturn(access);
		}

	}

	private static final class TestConnection implements ProtocolConnection, AutoCloseable {

		private final EmbeddedChannel channel = new EmbeddedChannel();

		private final List<Packet> writes = new ArrayList<>();

		private final List<Runnable> closeListeners = new ArrayList<>();

		private GenericPacketHandler handler;

		private DisconnectErrorCode error;

		private FormattedMessage disconnectReason;

		private boolean active = true;

		void receive(Packet packet) {
			if (active) {
				handler.accept(packet);
			}
		}

		void drain() {
			channel.runPendingTasks();
		}

		void advance(long seconds) {
			channel.advanceTimeBy(seconds, TimeUnit.SECONDS);
			channel.runScheduledPendingTasks();
			drain();
		}

		@Override
		public EventExecutor eventLoop() {
			return channel.eventLoop();
		}

		@Override
		public SocketAddress remoteAddress() {
			return new InetSocketAddress("127.0.0.1", 12345);
		}

		@Override
		public String certificateFingerprint() {
			return "client-certificate";
		}

		@Override
		public String serverCertificateFingerprint() {
			return "server-certificate";
		}

		@Override
		public boolean active() {
			return active;
		}

		@Override
		public void write(Packet packet) {
			if (active) {
				writes.add(packet);
			}
		}

		@Override
		public void setPacketHandler(GenericPacketHandler next) {
			if (handler != null) {
				handler.deactivated();
			}
			handler = next;
			handler.activated();
		}

		@Override
		public void disconnect(FormattedMessage reason, DisconnectErrorCode errorCode) {
			error = errorCode;
			disconnectReason = reason;
			close();
		}

		@Override
		public void close() {
			if (!active) {
				return;
			}
			active = false;
			if (handler != null) {
				handler.deactivated();
			}
			closeListeners.forEach(Runnable::run);
			channel.finishAndReleaseAll();
		}

		@Override
		public void onClose(Runnable listener) {
			closeListeners.add(listener);
		}

		@Override
		public void authenticated() {
		}

	}

}
