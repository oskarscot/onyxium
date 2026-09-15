package dev.onyxium.backend;

import java.lang.foreign.MemorySegment;
import java.time.Instant;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.ProtocolSettings;
import com.hypixel.hytale.protocol.packets.auth.AuthGrant;
import com.hypixel.hytale.protocol.packets.connection.ClientType;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.auth.PlayerAuthentication;
import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.ProtocolVersion;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.handlers.InitialPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.SetupPacketHandler;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.Universe;

import dev.onyxium.forwarding.ForwardingToken;

final class ForwardingFilter implements PacketFilter {

	private final ForwardingToken tokens;

	private final ForwardingToken.ReplayGuard replays = new ForwardingToken.ReplayGuard();

	ForwardingFilter(ForwardingToken tokens) {
		this.tokens = tokens;
	}

	@Override
	public boolean test(PacketHandler handler, Packet packet) {
		if (!(handler instanceof InitialPacketHandler))
			return false;

		var channel = handler.getChannel();

		try {
			if (tokens == null || !(packet instanceof Connect connect))
				throw new IllegalArgumentException();

			if (!ProtocolSettings.validateCrc(connect.protocolCrc)) {
				channel.disconnect(
						Message.raw("Backend and proxy Hytale protocol versions differ.").getFormattedMessage());
				return true;
			}

			if (connect.clientType != ClientType.Game)
				throw new IllegalArgumentException();

			var clientCertificate = channel.getClientCertificate();
			var serverFingerprint = ServerAuthManager.getInstance().getServerCertificateFingerprint();

			if (clientCertificate == null || serverFingerprint == null)
				throw new IllegalArgumentException();

			var now = Instant.now();
			var claims = tokens.verify(connect.identityToken, ForwardingToken.fingerprint(clientCertificate),
					serverFingerprint, unsignedPayload(connect), now);

			if (!replays.accept(claims, now))
				throw new IllegalArgumentException();

			var server = HytaleServer.get();

			if (!server.isBooted() || server.isShuttingDown()) {
				channel.disconnect(Message.raw("Backend is not ready.").getFormattedMessage());
				return true;
			}

			var maxPlayers = server.getConfig().getMaxPlayers();

			if (maxPlayers > 0 && Universe.get().getPlayerCount() >= maxPlayers) {
				channel.disconnect(Message.translation("client.general.disconnect.serverFull").getFormattedMessage());
				return true;
			}

			if (connect.referralData != null
					&& (connect.referralSource == null || connect.referralSource.host.isBlank()))
				throw new IllegalArgumentException();

			var identity = claims.identity();
			var auth = new PlayerAuthentication(identity.uuid(), identity.username());

			auth.setReferralData(connect.referralData);
			auth.setReferralSource(connect.referralSource);

			if (identity.skin() != null && !identity.skin().isEmpty()) {
				var skin = CosmeticsModule.get().parseSkinFromJson(identity.skin());
				if (skin == null)
					throw new IllegalArgumentException();
				CosmeticsModule.get().validateSkin(skin);
				auth.setSkin(skin);
			}

			var forwarded = new ForwardedChannelConnection(channel, identity.address());
			var setup = new SetupPacketHandler(forwarded, new ProtocolVersion(connect.protocolCrc),
					connect.language.isBlank() ? I18nModule.DEFAULT_LANGUAGE : connect.language, auth);

			channel.writeAndFlush(new AuthGrant(tokens.acknowledgement(connect.identityToken), null));
			channel.setChannelHandler(setup);
		}

		catch (Exception exception) {
			// PacketAdapters logs and continues when filters throw; catch here to fail
			// closed.
			channel.closeConnection();
		}
		return true;
	}

	static byte[] unsignedPayload(Connect connect) {
		var copy = new Connect(connect);
		copy.identityToken = null;
		var bytes = new byte[copy.computeSize()];
		copy.serialize(MemorySegment.ofArray(bytes), 0);
		return bytes;
	}

}
