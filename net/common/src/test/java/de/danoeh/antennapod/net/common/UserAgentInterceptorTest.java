package de.danoeh.antennapod.net.common;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserAgentInterceptorTest {
    private String originalUserAgent;

    @Before
    public void setUp() {
        originalUserAgent = UserAgentInterceptor.USER_AGENT;
    }

    @After
    public void tearDown() {
        UserAgentInterceptor.USER_AGENT = originalUserAgent;
    }

    private Request interceptAndCaptureForwardedRequest(Request original) throws IOException {
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(original);
        when(chain.proceed(any())).thenReturn(FakeHttp.response(original, 200));
        new UserAgentInterceptor().intercept(chain);
        ArgumentCaptor<Request> forwarded = ArgumentCaptor.forClass(Request.class);
        verify(chain).proceed(forwarded.capture());
        return forwarded.getValue();
    }

    @Test
    public void testInterceptAddsConfiguredUserAgentHeader() throws IOException {
        UserAgentInterceptor.USER_AGENT = "AntennaPod/3.5.0";
        Request forwarded = interceptAndCaptureForwardedRequest(FakeHttp.request("https://example.com/feed"));
        assertEquals("AntennaPod/3.5.0", forwarded.header("User-Agent"));
    }

    @Test
    public void testInterceptReplacesExistingUserAgentHeader() throws IOException {
        UserAgentInterceptor.USER_AGENT = "AntennaPod/3.5.0";
        Request original = new Request.Builder()
                .url("https://example.com/feed")
                .header("User-Agent", "SomethingElse/1.0")
                .build();
        Request forwarded = interceptAndCaptureForwardedRequest(original);
        assertEquals(1, forwarded.headers("User-Agent").size());
        assertEquals("AntennaPod/3.5.0", forwarded.header("User-Agent"));
    }

    @Test
    public void testInterceptKeepsUrlAndOtherHeaders() throws IOException {
        Request original = new Request.Builder()
                .url("https://example.com/feed?page=2")
                .header("Accept", "application/rss+xml")
                .build();
        Request forwarded = interceptAndCaptureForwardedRequest(original);
        assertEquals("https://example.com/feed?page=2", forwarded.url().toString());
        assertEquals("application/rss+xml", forwarded.header("Accept"));
    }

    @Test
    public void testInterceptReturnsResponseOfChain() throws IOException {
        Request original = FakeHttp.request("https://example.com/feed");
        Response expected = FakeHttp.response(original, 204);
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(original);
        when(chain.proceed(any())).thenReturn(expected);
        assertSame(expected, new UserAgentInterceptor().intercept(chain));
    }

    @Test
    public void testInterceptPropagatesIoExceptionOfChain() throws IOException {
        Request original = FakeHttp.request("https://example.com/feed");
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(original);
        when(chain.proceed(any())).thenThrow(new IOException("offline"));
        IOException thrown = assertThrows(IOException.class, () -> new UserAgentInterceptor().intercept(chain));
        assertEquals("offline", thrown.getMessage());
    }
}
