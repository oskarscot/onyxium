package dev.onyxium.proxy.io.connection;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.quic.QuicChannel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import dev.onyxium.proxy.io.packet.handler.LoginContext;

@ApiStatus.Internal
public final class QuicConnectionInitializer extends ChannelInitializer<QuicChannel> {

	private final LoginContext login;

	ChannelGroup connections;

	public QuicConnectionInitializer(LoginContext login, ChannelGroup connections) {
		this.login = login;
		this.connections = connections;
	}

	@Override
	protected void initChannel(@NotNull QuicChannel channel) {
		connections.add(channel);
		channel.pipeline().addLast(new QuicConnectionHandler(login));
	}

}
