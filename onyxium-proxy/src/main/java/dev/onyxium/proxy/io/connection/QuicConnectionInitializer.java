package dev.onyxium.proxy.io.connection;

import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicChannel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import dev.onyxium.proxy.io.packet.handler.LoginContext;

@ApiStatus.Internal
public final class QuicConnectionInitializer extends ChannelInitializer<QuicChannel> {

	private final LoginContext login;

	public QuicConnectionInitializer(LoginContext login) {
		this.login = login;
	}

	@Override
	protected void initChannel(@NotNull QuicChannel channel) {
		channel.pipeline().addLast(new QuicConnectionHandler(login));
	}

}
