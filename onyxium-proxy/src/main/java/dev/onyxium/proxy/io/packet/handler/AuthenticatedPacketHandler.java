package dev.onyxium.proxy.io.packet.handler;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.io.connection.DisconnectErrorCode;
import dev.onyxium.proxy.io.connection.ProtocolConnection;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.connection.Ping;
import dev.onyxium.proxy.io.packet.connection.Pong;

public final class AuthenticatedPacketHandler extends GenericPacketHandler {

	private ScheduledFuture<?> keepAlive;

	private ScheduledFuture<?> backendTimeout;

	private int pingId;

	private Instant pingTime;

	public AuthenticatedPacketHandler(ProtocolConnection connection) {
		super(connection);
	}

	@Override
	public void activated() {
		keepAlive = connection.eventLoop().scheduleAtFixedRate(() -> {
			pingTime = Instant.now();
			connection.write(new Ping(++pingId, pingTime, 0, 0, 0));
		}, 5, 5, TimeUnit.SECONDS);
		backendTimeout = connection.eventLoop()
			.schedule(() -> connection.disconnect(
					FormattedMessage.builder().text("Backend connection timed out.").color("#ffaa00").build(),
					DisconnectErrorCode.TIMEOUT), 30, TimeUnit.SECONDS);
	}

	@Override
	protected void handle(Packet packet) {
		if (packet instanceof Pong pong && pong.pingId() == pingId && pong.time().equals(pingTime)) {
			return;
		}
		unexpected(packet);
	}

	@Override
	public void deactivated() {
		if (keepAlive != null) {
			keepAlive.cancel(false);
		}
		if (backendTimeout != null) {
			backendTimeout.cancel(false);
		}
		super.deactivated();
	}

}
