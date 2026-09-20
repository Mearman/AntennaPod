package de.danoeh.antennapod.net.ssl;

import org.junit.Test;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AntennaPodSslSocketFactoryProtocolsTest {

    @Test
    public void testFactoryOffersCipherSuitesOfPlatformProvider() {
        AntennaPodSslSocketFactory factory = new AntennaPodSslSocketFactory(mock(X509TrustManager.class));
        List<String> supported = Arrays.asList(factory.getSupportedCipherSuites());
        assertTrue(factory.getDefaultCipherSuites().length > 0);
        assertTrue(supported.containsAll(Arrays.asList(factory.getDefaultCipherSuites())));
    }

    @Test
    public void testSocketOfPlatformProviderDoesNotEnableLegacyProtocols() throws IOException {
        AntennaPodSslSocketFactory factory = new AntennaPodSslSocketFactory(mock(X509TrustManager.class));
        Socket connected = mock(Socket.class);
        when(connected.isConnected()).thenReturn(true);
        SSLSocket socket = (SSLSocket) factory.createSocket(connected, "example.com", 443, true);
        List<String> protocols = Arrays.asList(socket.getEnabledProtocols());
        assertEquals(Arrays.asList("TLSv1.3", "TLSv1.2"), protocols);
        assertFalse(protocols.contains("TLSv1"));
        assertFalse(protocols.contains("TLSv1.1"));
    }
}
