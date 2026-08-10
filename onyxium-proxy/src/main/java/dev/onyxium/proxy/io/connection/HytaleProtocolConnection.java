package dev.onyxium.proxy.io.connection;

import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Objects;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Per-connection state for a single Hytale client, attached to the [QuicChannel] once the TLS
/// handshake has completed.
///
/// Hytale clients authenticate with a TLS client certificate, so the certificate only exists after
/// the handshake; it is captured here rather than being re-read from the [javax.net.ssl.SSLEngine]
/// on every use.
@ApiStatus.Internal
public final class HytaleProtocolConnection {

    private static final AttributeKey<HytaleProtocolConnection> ATTRIBUTE =
            AttributeKey.valueOf(HytaleProtocolConnection.class, "connection");

    private final QuicChannel channel;
    private final SocketAddress remoteAddress;
    private final String applicationProtocol;
    private final X509Certificate clientCertificate;
    private final String certificateFingerprint;

    private HytaleProtocolConnection(
            @NotNull QuicChannel channel,
            @NotNull SocketAddress remoteAddress,
            @NotNull String applicationProtocol,
            @NotNull X509Certificate clientCertificate,
            @NotNull String certificateFingerprint) {
        this.channel = channel;
        this.remoteAddress = remoteAddress;
        this.applicationProtocol = applicationProtocol;
        this.clientCertificate = clientCertificate;
        this.certificateFingerprint = certificateFingerprint;
    }

    /// Captures the negotiated protocol and the client certificate from the completed handshake and
    /// attaches the resulting connection to the channel.
    ///
    /// @return the attached connection, or `null` when the peer did not present a usable X.509
    ///         certificate; the caller is expected to close the channel in that case
    @Nullable
    static HytaleProtocolConnection attach(@NotNull QuicChannel channel) {
        Objects.requireNonNull(channel, "channel");

        var sslEngine = channel.sslEngine();
        if (sslEngine == null) {
            return null;
        }

        var clientCertificate = peerCertificate(sslEngine.getSession());
        if (clientCertificate == null) {
            return null;
        }

        MessageDigest encoder;
        byte[] digest;
        try {
            encoder = MessageDigest.getInstance("SHA-256");
            digest = encoder.digest(clientCertificate.getEncoded());
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            e.printStackTrace();
            return null;
        }

        var fingerprint = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        var applicationProtocol = Objects.requireNonNullElse(sslEngine.getApplicationProtocol(), "");
        // Captured now because the channel reports a null address once it is closed, which is
        // exactly when the disconnect is logged.
        var connection = new HytaleProtocolConnection(
                channel, channel.remoteSocketAddress(), applicationProtocol, clientCertificate, fingerprint);
        channel.attr(ATTRIBUTE).set(connection);

        return connection;
    }
    
    /// Looks up the connection for a [QuicChannel] or one of its [QuicStreamChannel]s.
    ///
    /// @return the connection, or `null` when the handshake has not completed yet
    @Nullable
    public static HytaleProtocolConnection of(@NotNull Channel channel) {
        Objects.requireNonNull(channel, "channel");

        var owner = channel instanceof QuicStreamChannel streamChannel ? streamChannel.parent() : channel;
        return owner == null ? null : owner.attr(ATTRIBUTE).get();
    }

    @Nullable
    private static X509Certificate peerCertificate(@NotNull SSLSession session) {
        Certificate[] peerCertificates;
        try {
            peerCertificates = session.getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            return null;
        }

        // The peer's own certificate is always first; anything after it is the chain it was signed with.
        return peerCertificates.length > 0 && peerCertificates[0] instanceof X509Certificate certificate
                ? certificate
                : null;
    }

    @NotNull
    public QuicChannel channel() {
        return this.channel;
    }

    /// The client's UDP address as seen at handshake time. Remains readable after the channel has
    /// closed, unlike [QuicChannel#remoteSocketAddress()].
    @NotNull
    public SocketAddress remoteAddress() {
        return this.remoteAddress;
    }

    /// The ALPN protocol negotiated for this connection
    @NotNull
    public String applicationProtocol() {
        return this.applicationProtocol;
    }

    /// The certificate the client authenticated with. It is self-signed and deliberately not
    /// validated against a trust store: identity is established from its contents, not its issuer.
    @NotNull
    public X509Certificate clientCertificate() {
        return this.clientCertificate;
    }

    /// Returns the base64 unpadded fingerprint of the certificate used for comparison with the session token (x5t#S256)
    @NotNull
    public String certificateFingerprint() {
        return this.certificateFingerprint;
    }

    @Override
    public String toString() {
        return "HytaleProtocolConnection [channel=" + channel + ", remoteAddress=" + remoteAddress
                + ", applicationProtocol=" + applicationProtocol + ", clientCertificate=" + clientCertificate
                + ", certificateFingerprint=" + certificateFingerprint + "]";
    }


}
