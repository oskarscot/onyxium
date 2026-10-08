package dev.onyxium.proxy.player;

import module java.base;
import org.jetbrains.annotations.NotNull;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.api.player.Player;
import dev.onyxium.proxy.auth.AuthenticatedProfile;
import dev.onyxium.proxy.io.connection.DisconnectErrorCode;
import dev.onyxium.proxy.io.connection.ProtocolConnection;
import dev.onyxium.proxy.io.packet.ClientType;
import dev.onyxium.proxy.io.packet.HostAddress;
import dev.onyxium.proxy.io.packet.ProtocolVersion;
import dev.onyxium.proxy.io.packet.auth.Connect;
import dev.onyxium.proxy.io.packet.chat.ServerMessage;

/// Created only after mutual authentication and the optional password challenge succeed.
public final class ProxyPlayer implements Player {

	private final ProtocolConnection connection;

	private final AuthenticatedProfile profile;

	private final ProtocolVersion protocolVersion;

	private final ClientType clientType;

	private final String clientVersion;

	private final String language;

	private final byte[] referralData;

	private final HostAddress referralSource;

	public ProxyPlayer(ProtocolConnection connection, AuthenticatedProfile profile, Connect connect) {
		this.connection = connection;
		this.profile = profile;
		protocolVersion = new ProtocolVersion(connect.protocolCrc(), connect.protocolBuildNumber());
		clientType = connect.clientType();
		clientVersion = connect.clientVersion();
		language = connect.language();
		referralData = connect.referralData() == null ? null : connect.referralData().clone();
		referralSource = connect.referralSource();
	}

	@Override
	public @NotNull UUID uuid() {
		return profile.uniqueId();
	}

	@Override
	public @NotNull String username() {
		return profile.username();
	}

	@Override
	public @NotNull SocketAddress remoteAddress() {
		return connection.remoteAddress();
	}

	@Override
	public boolean active() {
		return connection.active();
	}

	/// Backend permissions are not shared with the proxy. Named permissions are denied for now.
	@Override
	public boolean hasPermission(String permission) {
		return false;
	}

	@Override
	public void disconnect(@NotNull FormattedMessage reason) {
		Objects.requireNonNull(reason, "Reason cannot be null");
		connection.eventLoop().execute(() -> connection.disconnect(reason, DisconnectErrorCode.NO_ERROR));
	}

	@Override
	public void sendMessage(@NotNull FormattedMessage message) {
		Objects.requireNonNull(message, "Message cannot be null");
		connection.eventLoop().execute(() -> connection.write(new ServerMessage(message)));
	}

	public ProtocolConnection connection() {
		return connection;
	}

	public AuthenticatedProfile profile() {
		return profile;
	}

	public ProtocolVersion protocolVersion() {
		return protocolVersion;
	}

	public ClientType clientType() {
		return clientType;
	}

	public String clientVersion() {
		return clientVersion;
	}

	public String language() {
		return language;
	}

	public byte[] referralData() {
		return referralData == null ? null : referralData.clone();
	}

	public HostAddress referralSource() {
		return referralSource;
	}

	@Override
	public String toString() {
		return "ProxyPlayer[uuid=" + uuid() + ", username=" + username() + "]";
	}

}
