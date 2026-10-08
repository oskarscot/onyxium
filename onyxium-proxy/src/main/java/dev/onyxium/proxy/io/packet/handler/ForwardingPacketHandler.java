package dev.onyxium.proxy.io.packet.handler;

import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.Future;
import module java.base;

import dev.onyxium.command.CommandDispatcher;
import dev.onyxium.proxy.io.connection.HytaleProtocolConnection;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.chat.ChatMessage;
import dev.onyxium.proxy.player.ProxyPlayer;

public class ForwardingPacketHandler extends GenericPacketHandler {

	ProxyPlayer player;

	CommandDispatcher commands;

	QuicStreamChannel game;

	Consumer<String> onFailure;

	public ForwardingPacketHandler(ProxyPlayer player, CommandDispatcher commands, QuicStreamChannel game,
			Consumer<String> onFailure) {
		super(player.connection());
		this.player = player;
		this.commands = commands;
		this.game = game;
		this.onFailure = onFailure;
	}

	@Override
	protected void handle(Packet forwarded) {
		if (forwarded instanceof ChatMessage chat && isCommand(chat.message())) {
			var handled = commands.dispatch(player, chat.message());
			if (handled) {
				return;
			}
		}

		game.writeAndFlush(forwarded).addListener(this::writeComplete);
		((HytaleProtocolConnection) connection).readEnabled(game.isWritable());
	}

	static boolean isCommand(String message) {
		return message != null && message.startsWith("/");
	}

	void writeComplete(Future<? super Void> result) {
		if (!result.isSuccess()) {
			onFailure.accept("Backend write failed.");
		}
	}

}
