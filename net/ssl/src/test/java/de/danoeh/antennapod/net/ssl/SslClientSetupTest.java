package de.danoeh.antennapod.net.ssl;

import okhttp3.OkHttpClient;
import org.junit.Test;

import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.Assert.assertNotNull;

public class SslClientSetupTest {

    @Test
    public void testInstalledTrustManagerTrustsBackportedRootCertificates() throws GeneralSecurityException {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        SslClientSetup.installCertificates(builder);
        X509TrustManager installed = builder.build().x509TrustManager();
        assertNotNull(installed);
        X509Certificate root = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(BackportCaCerts.LETSENCRYPT_ISRG.getBytes(StandardCharsets.UTF_8)));
        installed.checkServerTrusted(new X509Certificate[] {root}, root.getPublicKey().getAlgorithm());
    }
}
