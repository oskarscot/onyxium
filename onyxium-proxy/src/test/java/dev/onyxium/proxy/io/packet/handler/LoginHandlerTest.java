package dev.onyxium.proxy.io.packet.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.EventExecutor;
import org.junit.Test;

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

public class LoginHandlerTest {

	private static final AuthenticatedProfile PROFILE = new AuthenticatedProfile(UUID.randomUUID(), "Oskar", null,
			List.of());

	@Test
	public void rejectsTokenBeforeConnectAndVersionMismatch() {
		try (var connection = new TestConnection()) {
			var authentication = new TestAuthentication();
			connection.setPacketHandler(new HandshakePacketHandler(connection, context(authentication, null)));
			connection.receive(new AuthToken("access", "grant"));
			assertEquals(DisconnectErrorCode.AUTH_FAILED, connection.error);
			assertEquals(0, authentication.requests);
		}
		try (var connection = new TestConnection()) {
			var authentication = new TestAuthentication();
			connection.setPacketHandler(new HandshakePacketHandler(connection, context(authentication, null)));
			connection.receive(new Connect(1, 1, "old", ClientType.GAME, "en-US", "identity", null, null));
			assertEquals(DisconnectErrorCode.CLIENT_OUTDATED, connection.error);
			assertEquals(0, authentication.requests);
		}
	}

	@Test
	public void duplicateConnectAndDuplicateTokensFailClosed() {
		try (var connection = new TestConnection()) {
			var authentication = new TestAuthentication();
			connection.setPacketHandler(new HandshakePacketHandler(connection, context(authentication, null)));
			connection.receive(connect());
			connection.receive(connect());
			authentication.grant.complete(grant());
			connection.drain();
			assertFalse(connection.active());
			assertTrue(connection.writes.isEmpty());
		}
		try (var connection = new TestConnection()) {
			var authentication = new TestAuthentication();
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
			var authentication = new TestAuthentication();
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
				.setPacketHandler(new HandshakePacketHandler(connection, context(new TestAuthentication(), null)));
			connection.advance(11);
			assertEquals(DisconnectErrorCode.TIMEOUT, connection.error);
		}
		try (var connection = new TestConnection()) {
			connection
				.setPacketHandler(new HandshakePacketHandler(connection, context(new TestAuthentication(), null)));
			connection.receive(connect());
			connection.advance(46);
			assertEquals(DisconnectErrorCode.TIMEOUT, connection.error);
		}
	}

	@Test
	public void passwordRetriesRotateChallengesAndGatePlayerRegistration() throws Exception {
		try (var connection = new TestConnection()) {
			var authentication = new TestAuthentication();
			var login = context(authentication, "secret");
			start(connection, login, authentication);
			connection.receive(new AuthToken("access", "server-grant"));
			authentication.access.complete("server-access");
			connection.drain();
			var challenge = ((ServerAuthToken) connection.writes.getLast()).passwordChallenge();
			assertTrue(login.players().players().isEmpty());
			connection.receive(new PasswordResponse(new byte[32]));
			var retry = (PasswordRejected) connection.writes.getLast();
			assertEquals(2, retry.attemptsRemaining());
			assertFalse(MessageDigest.isEqual(challenge, retry.newChallenge()));
			var digest = MessageDigest.getInstance("SHA-256");
			digest.update(retry.newChallenge());
			connection.receive(new PasswordResponse(digest.digest("secret".getBytes(StandardCharsets.UTF_8))));
			assertTrue(connection.writes.getLast() instanceof PasswordAccepted);
			assertEquals(1, login.players().players().size());
			connection.close();
			assertTrue(login.players().players().isEmpty());
		}
	}

	@Test
	public void passwordFailureClosesAfterThreeAttempts() {
		try (var connection = new TestConnection()) {
			var authentication = new TestAuthentication();
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
		var registry = new PlayerRegistry();
		try (var first = new TestConnection(); var second = new TestConnection()) {
			for (var connection : List.of(first, second)) {
				var authentication = new TestAuthentication();
				var login = new LoginContext(authentication, registry, ProtocolVersion.CURRENT, null, player -> {
				});
				start(connection, login, authentication);
				connection.receive(new AuthToken("access", "server-grant"));
				authentication.access.complete("server-access");
				connection.drain();
			}
			assertTrue(first.active());
			assertFalse(second.active());
			assertSame(first, registry.player(PROFILE.uniqueId()).orElseThrow().connection());
			first.close();
			assertTrue(registry.players().isEmpty());
		}
	}

	@Test
	public void replacingAuthenticatedHandlerCancelsBackendDeadline() {
		try (var connection = new TestConnection()) {
			var authentication = new TestAuthentication();
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

	private static void start(TestConnection connection, LoginContext login, TestAuthentication authentication) {
		connection.setPacketHandler(new HandshakePacketHandler(connection, login));
		connection.receive(connect());
		authentication.grant.complete(grant());
		connection.drain();
		assertTrue(connection.writes.getLast() instanceof AuthGrant);
	}

	private static LoginContext context(TestAuthentication authentication, String password) {
		return new LoginContext(authentication, new PlayerRegistry(), ProtocolVersion.CURRENT, password, player -> {
		});
	}

	private static Connect connect() {
		return new Connect(ProtocolVersion.CURRENT.crc(), ProtocolVersion.CURRENT.buildNumber(), "test",
				ClientType.GAME, "en-US", "identity", null, null);
	}

	private static AuthenticationService.Grant grant() {
		return new AuthenticationService.Grant(PROFILE, "grant", "server-identity");
	}

	private static final class TestAuthentication implements AuthenticationService {

		private final CompletableFuture<Grant> grant = new CompletableFuture<>();

		private final CompletableFuture<String> access = new CompletableFuture<>();

		private int requests;

		@Override
		public CompletionStage<Grant> requestGrant(Connect connect) {
			requests++;
			return grant;
		}

		@Override
		public CompletionStage<String> authenticate(AuthToken token, AuthenticatedProfile identity,
				String clientFingerprint, String serverFingerprint) {
			return access;
		}

	}

	private static final class TestConnection implements ProtocolConnection, AutoCloseable {

		private final EmbeddedChannel channel = new EmbeddedChannel();

		private final List<Packet> writes = new ArrayList<>();

		private final List<Runnable> closeListeners = new ArrayList<>();

		private GenericPacketHandler handler;

		private DisconnectErrorCode error;

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
