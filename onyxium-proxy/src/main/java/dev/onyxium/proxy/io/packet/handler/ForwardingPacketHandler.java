package dev.onyxium.proxy.io.packet.handler;

import java.util.function.Consumer;

import io.netty.handler.codec.quic.QuicStreamChannel;

import dev.onyxium.proxy.io.connection.HytaleProtocolConnection;
import dev.onyxium.proxy.io.connection.ProtocolConnection;
import dev.onyxium.proxy.io.packet.Packet;

public class ForwardingPacketHandler extends GenericPacketHandler {

	private final QuicStreamChannel game;

	private final Consumer<String> onFailure;

	public ForwardingPacketHandler(ProtocolConnection connection, QuicStreamChannel game, Consumer<String> onFailure) {
		super(connection);
		this.game = game;
		this.onFailure = onFailure;
	}

	@Override
	protected void handle(Packet forwarded) {
		game.writeAndFlush(forwarded).addListener(result -> {
			if (!result.isSuccess())
				onFailure.accept("Backend write failed.");
		});
		((HytaleProtocolConnection) connection).readEnabled(game.isWritable());
	}

}
