package dev.onyxium.proxy.io.packet.handler;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.io.connection.DisconnectErrorCode;
import dev.onyxium.proxy.io.connection.ProtocolConnection;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.connection.ClientDisconnect;

public abstract class GenericPacketHandler {

	protected final ProtocolConnection connection;

	private ScheduledFuture<?> timeout;

	protected GenericPacketHandler(ProtocolConnection connection) {
		this.connection = connection;
	}

	public void activated() {
	}

	public void deactivated() {
		clearTimeout();
	}

	public final void accept(Packet packet) {
		if (!connection.active())
			return;
		if (packet instanceof ClientDisconnect) {
			connection.close();
			return;
		}
		handle(packet);
	}

	protected abstract void handle(Packet packet);

	protected final void unexpected(Packet packet) {
		connection.disconnect(FormattedMessage.builder()
			.translation("client.general.disconnect.protocol.unexpectedPacket")
			.param("packetId", packet.id())
			.build(), DisconnectErrorCode.AUTH_FAILED);
	}

	protected final void timeout(String stage, long seconds) {
		clearTimeout();
		timeout = connection.eventLoop()
			.schedule(() -> connection.disconnect(FormattedMessage.text("Timed out during " + stage),
					DisconnectErrorCode.TIMEOUT), seconds, TimeUnit.SECONDS);
	}

	protected final void clearTimeout() {
		if (timeout != null) {
			timeout.cancel(false);
			timeout = null;
		}
	}

}
