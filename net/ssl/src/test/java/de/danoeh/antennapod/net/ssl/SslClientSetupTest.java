package de.danoeh.antennapod.net.ssl;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SslClientSetupTest {

    @Test
    public void testInstallCertificatesUsesBackportTrustManagerAndAntennaPodSocketFactory() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        SslClientSetup.installCertificates(builder);
        OkHttpClient client = builder.build();
        assertTrue(client.sslSocketFactory() instanceof AntennaPodSslSocketFactory);
        assertTrue(client.x509TrustManager() instanceof CompositeX509TrustManager);
    }

    @Test
    public void testInstallCertificatesAllowsOnlyModernTlsAndCleartext() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        SslClientSetup.installCertificates(builder);
        assertEquals(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT),
                builder.build().connectionSpecs());
    }
}
