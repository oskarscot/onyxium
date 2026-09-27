package dev.onyxium.proxy.io.packet.handler;

import java.text.ParseException;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jwt.SignedJWT;

import dev.onyxium.proxy.api.event.PreLoginEvent;
import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.io.connection.DisconnectErrorCode;
import dev.onyxium.proxy.io.connection.ProtocolConnection;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.auth.Connect;

public final class HandshakePacketHandler extends GenericPacketHandler {

	private final LoginContext login;

	public HandshakePacketHandler(ProtocolConnection connection, LoginContext login) {
		super(connection);
		this.login = login;
	}

	@Override
	public void activated() {
		timeout("Connect", 10);
	}

	@Override
	protected void handle(Packet packet) {
		if (!(packet instanceof Connect connect)) {
			unexpected(packet);
			return;
		}
		if (!login.protocolVersion().matches(connect.protocolCrc())) {
			var code = connect.protocolBuildNumber() < login.protocolVersion().buildNumber()
					? DisconnectErrorCode.CLIENT_OUTDATED : DisconnectErrorCode.SERVER_OUTDATED;
			connection.disconnect(FormattedMessage.builder()
				.text("Incompatible Hytale protocol. ")
				.append(child -> child.text("Expected build " + login.protocolVersion().buildNumber()).color("#ffaa00"))
				.build(), code);
			return;
		}
		if (connect.identityToken() == null || connect.identityToken().isBlank()) {
			connection.disconnect(
					FormattedMessage.translation("client.general.disconnect.serverRequiresAuthentication"),
					DisconnectErrorCode.AUTH_FAILED);
			return;
		}
		if (connect.referralData() != null
				&& (connect.referralSource() == null || connect.referralSource().host().isBlank())) {
			connection.disconnect(FormattedMessage.translation("client.general.disconnect.referralMissingSource"),
					DisconnectErrorCode.AUTH_FAILED);
			return;
		}
		var event = preLoginEvent(connect);
		login.proxy().eventBus().postEvent(event);
		if (event.isCancelled()) {
			connection.disconnect(event.getCancelledMessage(), DisconnectErrorCode.AUTH_FAILED);
			return;
		}
		if (!connection.active()) {
			return;
		}
		connection.setPacketHandler(new AuthenticationPacketHandler(connection, login, connect));
	}

	private PreLoginEvent preLoginEvent(Connect connect) {
		UUID uuid = null;
		String username = null;
		try {
			var claims = SignedJWT.parse(connect.identityToken()).getJWTClaimsSet();
			if (claims.getClaim("profile") instanceof Map<?, ?> profile
					&& profile.get("username") instanceof String value) {
				username = value;
			}
			if (claims.getSubject() != null) {
				uuid = UUID.fromString(claims.getSubject());
			}
		}
		catch (ParseException | IllegalArgumentException ignored) {
			// Unreadable claims remain absent; authentication still validates the token.
		}
		return new PreLoginEvent(connection.remoteAddress(), uuid, username);
	}

}
