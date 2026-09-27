package dev.onyxium.proxy.io.packet.handler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.onyxium.proxy.api.event.PostLoginEvent;
import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.auth.AuthenticatedProfile;
import dev.onyxium.proxy.io.connection.DisconnectErrorCode;
import dev.onyxium.proxy.io.connection.ProtocolConnection;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.auth.AuthGrant;
import dev.onyxium.proxy.io.packet.auth.AuthToken;
import dev.onyxium.proxy.io.packet.auth.Connect;
import dev.onyxium.proxy.io.packet.auth.PasswordAccepted;
import dev.onyxium.proxy.io.packet.auth.PasswordRejected;
import dev.onyxium.proxy.io.packet.auth.PasswordResponse;
import dev.onyxium.proxy.io.packet.auth.ServerAuthToken;
import dev.onyxium.proxy.player.ProxyPlayer;

public final class AuthenticationPacketHandler extends GenericPacketHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationPacketHandler.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private enum State {

		REQUESTING_GRANT, AWAITING_TOKEN, VALIDATING_TOKEN, AWAITING_PASSWORD, COMPLETE, CLOSED

	}

	private final LoginContext login;

	private final Connect connect;

	private State state = State.REQUESTING_GRANT;

	private AuthenticatedProfile identity;

	private byte[] challenge;

	private int attempts = 3;

	public AuthenticationPacketHandler(ProtocolConnection connection, LoginContext login, Connect connect) {
		super(connection);
		this.login = login;
		this.connect = connect;
	}

	@Override
	public void activated() {
		timeout("authorization grant", 45);
		await(login.authentication().requestGrant(connect), State.REQUESTING_GRANT, grant -> {
			identity = grant.profile();
			state = State.AWAITING_TOKEN;
			timeout("access token", 30);
			connection.write(new AuthGrant(grant.authorizationGrant(), grant.serverIdentityToken()));
		});
	}

	@Override
	protected void handle(Packet packet) {
		switch (packet) {
			case AuthToken token when state == State.AWAITING_TOKEN -> {
				state = State.VALIDATING_TOKEN;
				timeout("mutual authentication", 45);
				await(login.authentication()
					.authenticate(token, identity, connection.certificateFingerprint(),
							connection.serverCertificateFingerprint()),
						State.VALIDATING_TOKEN, this::serverAuthenticated);
			}
			case PasswordResponse response when state == State.AWAITING_PASSWORD -> password(response);
			default -> unexpected(packet);
		}
	}

	private void serverAuthenticated(String token) {
		if (token == null || token.isBlank()) {
			authenticationFailed();
			return;
		}
		if (login.password() != null && !login.password().isEmpty()) {
			challenge = newChallenge();
			state = State.AWAITING_PASSWORD;
			timeout("password", 30);
			connection.write(new ServerAuthToken(token, challenge.clone()));
		}
		else {
			connection.write(new ServerAuthToken(token, null));
			complete();
		}
	}

	private void password(PasswordResponse response) {
		if (response.hash() == null || response.hash().length != 32) {
			authenticationFailed();
			return;
		}
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			digest.update(challenge);
			var expected = digest.digest(login.password().getBytes(StandardCharsets.UTF_8));
			if (!MessageDigest.isEqual(expected, response.hash())) {
				if (--attempts == 0) {
					connection.disconnect(
							FormattedMessage.translation("client.general.disconnect.tooManyPasswordAttempts"),
							DisconnectErrorCode.AUTH_FAILED);
					return;
				}
				challenge = newChallenge();
				connection.write(new PasswordRejected(challenge.clone(), attempts));
				timeout("password", 30);
				return;
			}
			connection.write(new PasswordAccepted());
			complete();
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private void complete() {
		if (!connection.active()) {
			return;
		}
		var player = new ProxyPlayer(connection, identity, connect);

		var loginEvent = new PostLoginEvent(player);
		login.proxy().eventBus().postEvent(loginEvent);
		if (loginEvent.isCancelled()) {
			connection.disconnect(loginEvent.getCancelledMessage(), DisconnectErrorCode.AUTH_FAILED);
			return;
		}
		if (!connection.active()) {
			return;
		}

		if (!login.players().register(player)) {
			connection.disconnect(FormattedMessage.text("You are already connected to this proxy."),
					DisconnectErrorCode.AUTH_FAILED);
			return;
		}
		state = State.COMPLETE;
		connection.authenticated();
		connection.setPacketHandler(new AuthenticatedPacketHandler(connection));
		LOGGER.info("Authenticated {} from {}", player, connection.remoteAddress());
		login.onAuthenticated().accept(player);
	}

	private <T> void await(CompletionStage<T> result, State expected, Consumer<T> success) {
		result.whenComplete((value, failure) -> {
			try {
				connection.eventLoop().execute(() -> {
					if (!connection.active() || state != expected) {
						return;
					}
					if (failure != null || value == null) {
						authenticationFailed();
						return;
					}
					try {
						success.accept(value);
					}
					catch (RuntimeException exception) {
						authenticationFailed();
					}
				});
			}
			catch (RejectedExecutionException ignored) {
			}
		});
	}

	private void authenticationFailed() {
		LOGGER.debug("Authentication failed for {} during {}", connection.remoteAddress(), state);
		connection.disconnect(
				FormattedMessage.builder().translation("client.general.disconnect.authError").color("#ff5555").build(),
				DisconnectErrorCode.AUTH_FAILED);
	}

	private static byte[] newChallenge() {
		var bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	@Override
	public void deactivated() {
		state = State.CLOSED;
		challenge = null;
		super.deactivated();
	}

}
