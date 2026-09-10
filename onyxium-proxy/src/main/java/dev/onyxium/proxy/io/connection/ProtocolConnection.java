package dev.onyxium.proxy.io.connection;

import java.net.SocketAddress;

import io.netty.util.concurrent.EventExecutor;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.io.packet.handler.GenericPacketHandler;

public interface ProtocolConnection {

	EventExecutor eventLoop();

	SocketAddress remoteAddress();

	String certificateFingerprint();

	String serverCertificateFingerprint();

	boolean active();

	void write(Packet packet);

	void setPacketHandler(GenericPacketHandler handler);

	void disconnect(FormattedMessage reason, DisconnectErrorCode errorCode);

	void close();

	void onClose(Runnable listener);

	void authenticated();

}
