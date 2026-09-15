package dev.onyxium.backend;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.NetworkChannel;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.io.ChannelConnection;
import com.hypixel.hytale.protocol.io.ConnectionHandler;
import com.hypixel.hytale.protocol.io.PacketStatsRecorder;
import com.hypixel.hytale.protocol.packets.connection.QuicApplicationErrorCode;
import org.jetbrains.annotations.NotNull;

public record ForwardedChannelConnection(ChannelConnection delegate,
		InetSocketAddress address) implements ChannelConnection {
	@Override
	public SocketAddress remoteAddress() {
		return address;
	}

	@Override
	public String formatRemoteAddress() {
		return address.toString();
	}

	@Override
	public boolean isFromSameOrigin(ChannelConnection other) {
		return other.remoteAddress() instanceof InetSocketAddress remote
				&& address.getAddress().equals(remote.getAddress());
	}

	@Override
	public @NotNull CompletableFuture<Void> setupAuxiliaryChannels(@NotNull ConnectionHandler handler,
			@NotNull BiConsumer<NetworkChannel, ChannelConnection> onChannelReady) {
		return delegate.setupAuxiliaryChannels(handler,
				(type, channel) -> onChannelReady.accept(type, new ForwardedChannelConnection(channel, address)));
	}

	@Override
	public void flush() {
		delegate.flush();
	}

	@Override
	public void write(ToClientPacket packet) {
		delegate.write(packet);
	}

	@Override
	public void writeAndFlush(ToClientPacket packet) {
		delegate.writeAndFlush(packet);
	}

	@Override
	public void write(ToClientPacket[] packets) {
		delegate.write(packets);
	}

	@Override
	public void writeAndFlush(ToClientPacket[] packets) {
		delegate.writeAndFlush(packets);
	}

	@Override
	public boolean isActive() {
		return delegate.isActive();
	}

	@Override
	public boolean isWritable() {
		return delegate.isWritable();
	}

	@Override
	public void disconnect(@NotNull FormattedMessage message) {
		delegate.disconnect(message);
	}

	@Override
	public PacketStatsRecorder getPacketStatsRecorder() {
		return delegate.getPacketStatsRecorder();
	}

	@Override
	public String getSniHostname() {
		return delegate.getSniHostname();
	}

	@Override
	public void execute(Runnable action) {
		delegate.execute(action);
	}

	@Override
	public X509Certificate getClientCertificate() {
		return delegate.getClientCertificate();
	}

	@Override
	public void initTimeoutContext(@NotNull String stage, @NotNull String identifier) {
		delegate.initTimeoutContext(stage, identifier);
	}

	@Override
	public void updateTimeoutContext(@NotNull String stage, @NotNull String identifier) {
		delegate.updateTimeoutContext(stage, identifier);
	}

	@Override
	public void updateTimeoutContext(@NotNull String stage) {
		delegate.updateTimeoutContext(stage);
	}

	@Override
	public void setPacketTimeout(@NotNull Duration timeout) {
		delegate.setPacketTimeout(timeout);
	}

	@Override
	public void clearPacketTimeout() {
		delegate.clearPacketTimeout();
	}

	@Override
	public void setStageTimeout(@NotNull String stage, @NotNull Duration timeout, @NotNull BooleanSupplier condition,
			@NotNull Runnable onTimeout) {
		delegate.setStageTimeout(stage, timeout, condition, onTimeout);
	}

	@Override
	public void clearStageTimeout() {
		delegate.clearStageTimeout();
	}

	@Override
	public void logConnectionTimings(@NotNull String message, @NotNull Level level) {
		delegate.logConnectionTimings(message, level);
	}

	@Override
	public void setChannelHandler(@NotNull ConnectionHandler handler) {
		delegate.setChannelHandler(handler);
	}

	@Override
	public void closeConnection() {
		delegate.closeConnection();
	}

	@Override
	public void closeApplicationConnection() {
		delegate.closeApplicationConnection();
	}

	@Override
	public void closeApplicationConnection(@NotNull QuicApplicationErrorCode code) {
		delegate.closeApplicationConnection(code);
	}

	@Override
	public void closeApplicationConnection(@NotNull QuicApplicationErrorCode code, @NotNull FormattedMessage reason) {
		delegate.closeApplicationConnection(code, reason);
	}

	@Override
	public void updateStreamPriority(int urgency, boolean incremental) {
		delegate.updateStreamPriority(urgency, incremental);
	}

}
