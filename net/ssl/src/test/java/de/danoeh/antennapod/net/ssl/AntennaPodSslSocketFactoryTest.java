package de.danoeh.antennapod.net.ssl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AntennaPodSslSocketFactoryTest {
    private static final String[] MODERN_PROTOCOLS = {"TLSv1.3", "TLSv1.2"};
    private static final String[] LEGACY_PROTOCOLS = {"TLSv1.2", "TLSv1.1", "TLSv1"};

    private X509TrustManager trustManager;
    private SSLContext tls13Context;
    private SSLContext tls12Context;
    private SSLSocketFactory delegate;
    private SSLSocket delegateSocket;
    private MockedStatic<SSLContext> sslContextFactory;

    @Before
    public void setUp() throws GeneralSecurityException {
        trustManager = mock(X509TrustManager.class);
        tls13Context = mock(SSLContext.class);
        tls12Context = mock(SSLContext.class);
        delegate = mock(SSLSocketFactory.class);
        delegateSocket = mock(SSLSocket.class);
        when(tls13Context.getSocketFactory()).thenReturn(delegate);
        when(tls12Context.getSocketFactory()).thenReturn(delegate);
        sslContextFactory = mockStatic(SSLContext.class);
        sslContextFactory.when(() -> SSLContext.getInstance("TLSv1.3")).thenReturn(tls13Context);
        sslContextFactory.when(() -> SSLContext.getInstance("TLSv1.2")).thenReturn(tls12Context);
    }

    @After
    public void tearDown() {
        sslContextFactory.close();
    }

    private AntennaPodSslSocketFactory createFactory() {
        return new AntennaPodSslSocketFactory(trustManager);
    }

    private void assertConfiguredForModernProtocols(Socket socket) {
        assertSame(delegateSocket, socket);
        verify(delegateSocket).setEnabledProtocols(MODERN_PROTOCOLS);
    }

    @Test
    public void testContextIsInitialisedWithOnlyTheGivenTrustManager() throws GeneralSecurityException {
        createFactory();
        verify(tls13Context).init(isNull(), eq(new TrustManager[] {trustManager}),
                isNull());
    }

    @Test
    public void testTls13ContextIsPreferredOverTls12() {
        createFactory();
        sslContextFactory.verify(() -> SSLContext.getInstance("TLSv1.2"), never());
    }

    @Test
    public void testTls12ContextIsUsedWhenTls13IsUnavailable() throws IOException, GeneralSecurityException {
        sslContextFactory.when(() -> SSLContext.getInstance("TLSv1.3")).thenThrow(new NoSuchAlgorithmException());
        when(delegate.createSocket()).thenReturn(delegateSocket);
        Socket socket = createFactory().createSocket();
        verify(tls12Context).init(isNull(), eq(new TrustManager[] {trustManager}),
                isNull());
        assertConfiguredForModernProtocols(socket);
    }

    @Test
    public void testCipherSuitesAreThoseOfTheUnderlyingFactory() {
        when(delegate.getDefaultCipherSuites()).thenReturn(new String[] {"DEFAULT_SUITE"});
        when(delegate.getSupportedCipherSuites()).thenReturn(new String[] {"DEFAULT_SUITE", "OTHER_SUITE"});
        AntennaPodSslSocketFactory factory = createFactory();
        assertArrayEquals(new String[] {"DEFAULT_SUITE"}, factory.getDefaultCipherSuites());
        assertArrayEquals(new String[] {"DEFAULT_SUITE", "OTHER_SUITE"}, factory.getSupportedCipherSuites());
    }

    @Test
    public void testUnconnectedSocketOnlyEnablesModernProtocols() throws IOException {
        when(delegate.createSocket()).thenReturn(delegateSocket);
        assertConfiguredForModernProtocols(createFactory().createSocket());
    }

    @Test
    public void testSocketForHostAndPortOnlyEnablesModernProtocols() throws IOException {
        when(delegate.createSocket("example.com", 443)).thenReturn(delegateSocket);
        assertConfiguredForModernProtocols(createFactory().createSocket("example.com", 443));
    }

    @Test
    public void testSocketLayeredOverExistingSocketOnlyEnablesModernProtocols() throws IOException {
        Socket underlying = mock(Socket.class);
        when(delegate.createSocket(underlying, "example.com", 443, true)).thenReturn(delegateSocket);
        assertConfiguredForModernProtocols(createFactory().createSocket(underlying, "example.com", 443, true));
    }

    @Test
    public void testSocketForAddressAndPortOnlyEnablesModernProtocols() throws IOException {
        InetAddress address = mock(InetAddress.class);
        when(delegate.createSocket(address, 443)).thenReturn(delegateSocket);
        assertConfiguredForModernProtocols(createFactory().createSocket(address, 443));
    }

    @Test
    public void testSocketForHostWithLocalAddressOnlyEnablesModernProtocols() throws IOException {
        InetAddress local = mock(InetAddress.class);
        when(delegate.createSocket("example.com", 443, local, 8080)).thenReturn(delegateSocket);
        assertConfiguredForModernProtocols(createFactory().createSocket("example.com", 443, local, 8080));
    }

    @Test
    public void testSocketForAddressWithLocalAddressOnlyEnablesModernProtocols() throws IOException {
        InetAddress remote = mock(InetAddress.class);
        InetAddress local = mock(InetAddress.class);
        when(delegate.createSocket(remote, 443, local, 8080)).thenReturn(delegateSocket);
        assertConfiguredForModernProtocols(createFactory().createSocket(remote, 443, local, 8080));
    }

    @Test
    public void testSocketFallsBackToOlderProtocolsWhenModernOnesAreUnsupported() throws IOException {
        when(delegate.createSocket()).thenReturn(delegateSocket);
        doThrow(new IllegalArgumentException("Unsupported protocol TLSv1.3"))
                .when(delegateSocket).setEnabledProtocols(MODERN_PROTOCOLS);
        Socket socket = createFactory().createSocket();
        assertSame(delegateSocket, socket);
        verify(delegateSocket).setEnabledProtocols(LEGACY_PROTOCOLS);
    }

    @Test
    public void testModernProtocolsAreNotReplacedByOlderOnesWhenSupported() throws IOException {
        when(delegate.createSocket()).thenReturn(delegateSocket);
        createFactory().createSocket();
        verify(delegateSocket, never()).setEnabledProtocols(LEGACY_PROTOCOLS);
    }
}
