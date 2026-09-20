package de.danoeh.antennapod.net.ssl;

import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CompositeX509TrustManagerTest {
    private static final String AUTH_TYPE = "RSA";

    private X509Certificate[] chain;
    private X509TrustManager first;
    private X509TrustManager second;

    @Before
    public void setUp() {
        chain = new X509Certificate[] {mock(X509Certificate.class)};
        first = mock(X509TrustManager.class);
        second = mock(X509TrustManager.class);
    }

    private CompositeX509TrustManager compositeOfFirstAndSecond() {
        return new CompositeX509TrustManager(Arrays.asList(first, second));
    }

    @Test
    public void testServerIsTrustedWithoutAskingLaterManagersWhenFirstTrusts() throws CertificateException {
        compositeOfFirstAndSecond().checkServerTrusted(chain, AUTH_TYPE);
        verify(first).checkServerTrusted(chain, AUTH_TYPE);
        verify(second, never()).checkServerTrusted(any(), any());
    }

    @Test
    public void testServerIsTrustedWhenOnlyLaterManagerTrusts() throws CertificateException {
        doThrow(new CertificateException("first rejects")).when(first).checkServerTrusted(chain, AUTH_TYPE);
        compositeOfFirstAndSecond().checkServerTrusted(chain, AUTH_TYPE);
        verify(second).checkServerTrusted(chain, AUTH_TYPE);
    }

    @Test
    public void testServerIsRejectedWithExceptionOfLastManagerWhenNoManagerTrusts() throws CertificateException {
        CertificateException firstReason = new CertificateException("first rejects");
        CertificateException secondReason = new CertificateException("second rejects");
        doThrow(firstReason).when(first).checkServerTrusted(chain, AUTH_TYPE);
        doThrow(secondReason).when(second).checkServerTrusted(chain, AUTH_TYPE);
        CertificateException thrown = assertThrows(CertificateException.class,
                () -> compositeOfFirstAndSecond().checkServerTrusted(chain, AUTH_TYPE));
        assertSame(secondReason, thrown);
        verify(first).checkServerTrusted(chain, AUTH_TYPE);
    }

    @Test
    public void testClientIsTrustedWithoutAskingLaterManagersWhenFirstTrusts() throws CertificateException {
        compositeOfFirstAndSecond().checkClientTrusted(chain, AUTH_TYPE);
        verify(first).checkClientTrusted(chain, AUTH_TYPE);
        verify(second, never()).checkClientTrusted(any(), any());
    }

    @Test
    public void testClientIsTrustedWhenOnlyLaterManagerTrusts() throws CertificateException {
        doThrow(new CertificateException("first rejects")).when(first).checkClientTrusted(chain, AUTH_TYPE);
        compositeOfFirstAndSecond().checkClientTrusted(chain, AUTH_TYPE);
        verify(second).checkClientTrusted(chain, AUTH_TYPE);
    }

    @Test
    public void testClientIsRejectedWithExceptionOfLastManagerWhenNoManagerTrusts() throws CertificateException {
        CertificateException secondReason = new CertificateException("second rejects");
        doThrow(new CertificateException("first rejects")).when(first).checkClientTrusted(chain, AUTH_TYPE);
        doThrow(secondReason).when(second).checkClientTrusted(chain, AUTH_TYPE);
        CertificateException thrown = assertThrows(CertificateException.class,
                () -> compositeOfFirstAndSecond().checkClientTrusted(chain, AUTH_TYPE));
        assertSame(secondReason, thrown);
    }

    @Test
    public void testServerTrustDoesNotConsultClientTrustChecks() throws CertificateException {
        compositeOfFirstAndSecond().checkServerTrusted(chain, AUTH_TYPE);
        verify(first, never()).checkClientTrusted(any(), any());
    }

    @Test
    public void testAcceptedIssuersAreConcatenatedInManagerOrder() {
        X509Certificate a = mock(X509Certificate.class);
        X509Certificate b = mock(X509Certificate.class);
        X509Certificate c = mock(X509Certificate.class);
        when(first.getAcceptedIssuers()).thenReturn(new X509Certificate[] {a, b});
        when(second.getAcceptedIssuers()).thenReturn(new X509Certificate[] {c});
        assertArrayEquals(new X509Certificate[] {a, b, c}, compositeOfFirstAndSecond().getAcceptedIssuers());
    }

    @Test
    public void testAcceptedIssuersAreEmptyWithoutManagers() {
        CompositeX509TrustManager composite = new CompositeX509TrustManager(Collections.emptyList());
        assertEquals(0, composite.getAcceptedIssuers().length);
    }
}
