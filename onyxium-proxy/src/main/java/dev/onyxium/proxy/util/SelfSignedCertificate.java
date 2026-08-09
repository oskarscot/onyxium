package dev.onyxium.proxy.util;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public record SelfSignedCertificate(@NotNull X509Certificate certificate, @NotNull PrivateKey privateKey) {

    public static final String DEFAULT_COMMON_NAME = "Hytale Server";

    private static final String KEY_ALGORITHM = "EC";
    private static final String CURVE = "secp256r1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final Duration VALIDITY = Duration.ofDays(365);
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    private static final int SERIAL_BITS = 159;

    @NotNull
    public static SelfSignedCertificate generate(@NotNull String commonName) throws GeneralSecurityException {
        var keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        keyPairGenerator.initialize(new ECGenParameterSpec(CURVE));
        var keyPair = keyPairGenerator.generateKeyPair();

        var now = Instant.now();
        var notBefore = Date.from(now.minus(CLOCK_SKEW));
        var notAfter = Date.from(now.plus(VALIDITY));

        var name = new X500Principal("CN=" + commonName);
        var serial = new BigInteger(SERIAL_BITS, new SecureRandom());

        try {
            var certificateBuilder = new JcaX509v3CertificateBuilder(
                    name, serial, notBefore, notAfter, name, keyPair.getPublic())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

            var signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keyPair.getPrivate());
            var certificate = new JcaX509CertificateConverter().getCertificate(certificateBuilder.build(signer));

            return new SelfSignedCertificate(certificate, keyPair.getPrivate());
        } catch (IOException | OperatorCreationException e) {
            throw new GeneralSecurityException("Failed to generate a self-signed certificate for " + commonName, e);
        }
    }

    @NotNull
    public KeyStore toKeyStore(@NotNull String alias, char @NotNull [] password) throws GeneralSecurityException {
        try {
            var keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry(alias, privateKey, password, new Certificate[] { certificate });

            return keyStore;
        } catch (IOException e) {
            throw new KeyStoreException("Failed to assemble an in-memory PKCS12 key store", e);
        }
    }
}
