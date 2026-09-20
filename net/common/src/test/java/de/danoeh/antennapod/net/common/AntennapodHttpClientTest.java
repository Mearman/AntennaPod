package de.danoeh.antennapod.net.common;

import android.content.Context;
import de.danoeh.antennapod.model.download.ProxyConfig;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class AntennapodHttpClientTest {
    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        AntennapodHttpClient.setCacheDirectory(new File(context.getCacheDir(), "http-client-test"));
        AntennapodHttpClient.setProxyConfig(null);
        AntennapodHttpClient.reinit();
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        AntennapodHttpClient.setProxyConfig(null);
        AntennapodHttpClient.reinit();
        server.shutdown();
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    private Response get(String url) throws IOException {
        return AntennapodHttpClient.getHttpClient().newCall(new Request.Builder().url(url).build()).execute();
    }

    @Test
    public void getHttpClientReturnsSameInstanceUntilReinitialised() {
        OkHttpClient first = AntennapodHttpClient.getHttpClient();

        assertSame(first, AntennapodHttpClient.getHttpClient());
        AntennapodHttpClient.reinit();
        assertNotSame(first, AntennapodHttpClient.getHttpClient());
    }

    @Test
    public void newBuilderCreatesIndependentClientsWithoutSharedCookies() throws Exception {
        server.enqueue(new MockResponse().addHeader("Set-Cookie", "session=abc; Path=/"));
        server.enqueue(new MockResponse());
        OkHttpClient firstClient = AntennapodHttpClient.newBuilder().build();
        OkHttpClient secondClient = AntennapodHttpClient.newBuilder().build();
        Request request = new Request.Builder().url(server.url("/")).build();

        firstClient.newCall(request).execute().close();
        secondClient.newCall(request).execute().close();

        takeRequest();
        assertNull(takeRequest().getHeader("Cookie"));
    }

    @Test
    public void requestsCarryTheAntennaPodUserAgent() throws Exception {
        server.enqueue(new MockResponse());

        get(server.url("/").toString()).close();

        assertEquals(UserAgentInterceptor.USER_AGENT, takeRequest().getHeader("User-Agent"));
    }

    @Test
    public void redirectsAreFollowedToTheFinalLocation() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/final"));
        server.enqueue(new MockResponse().setBody("done"));

        try (Response response = get(server.url("/start").toString())) {
            assertEquals(200, response.code());
            assertEquals("done", response.body().string());
            assertEquals(server.url("/final"), response.request().url());
        }
    }

    @Test
    public void cookiesAreSentBackToTheServerThatSetThem() throws Exception {
        server.enqueue(new MockResponse().addHeader("Set-Cookie", "session=abc; Path=/"));
        server.enqueue(new MockResponse());

        get(server.url("/login").toString()).close();
        get(server.url("/feed").toString()).close();

        assertNull(takeRequest().getHeader("Cookie"));
        assertEquals("session=abc", takeRequest().getHeader("Cookie"));
    }

    @Test
    public void cacheableResponsesAreServedFromDiskCacheOnRepeatedRequests() throws Exception {
        server.enqueue(new MockResponse().addHeader("Cache-Control", "max-age=600").setBody("cached body"));

        try (Response first = get(server.url("/feed.xml").toString())) {
            assertEquals("cached body", first.body().string());
        }
        try (Response second = get(server.url("/feed.xml").toString())) {
            assertEquals("cached body", second.body().string());
        }

        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void responsesWithoutCacheHeadersAreNotServedFromTheCache() throws Exception {
        server.enqueue(new MockResponse().setBody("first"));
        server.enqueue(new MockResponse().setBody("second"));

        try (Response first = get(server.url("/feed.xml").toString())) {
            assertEquals("first", first.body().string());
        }
        try (Response second = get(server.url("/feed.xml").toString())) {
            assertEquals("second", second.body().string());
        }

        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void httpProxyReceivesRequestsWithAbsoluteTargetUrl() throws Exception {
        AntennapodHttpClient.setProxyConfig(
                new ProxyConfig(Proxy.Type.HTTP, server.getHostName(), server.getPort(), null, null));
        AntennapodHttpClient.reinit();
        server.enqueue(new MockResponse().setBody("via proxy"));

        try (Response response = get("http://podcasts.invalid/feed.xml")) {
            assertEquals("via proxy", response.body().string());
        }

        RecordedRequest request = takeRequest();
        assertEquals("GET http://podcasts.invalid/feed.xml HTTP/1.1", request.getRequestLine());
        assertEquals("podcasts.invalid", request.getHeader("Host"));
    }

    @Test
    public void proxyCredentialsAreSentAfterProxyAuthenticationChallenge() throws Exception {
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(
                Proxy.Type.HTTP, server.getHostName(), server.getPort(), "proxyuser", "proxypass"));
        AntennapodHttpClient.reinit();
        server.enqueue(new MockResponse().setResponseCode(407).addHeader("Proxy-Authenticate", "Basic realm=\"p\""));
        server.enqueue(new MockResponse().setBody("authenticated"));

        try (Response response = get("http://podcasts.invalid/feed.xml")) {
            assertEquals("authenticated", response.body().string());
        }

        assertNull(takeRequest().getHeader("Proxy-Authorization"));
        String expected = "Basic " + Base64.getEncoder().encodeToString("proxyuser:proxypass".getBytes());
        assertEquals(expected, takeRequest().getHeader("Proxy-Authorization"));
    }

    @Test
    public void directProxyTypeBypassesTheProxy() throws Exception {
        AntennapodHttpClient.setProxyConfig(
                new ProxyConfig(Proxy.Type.DIRECT, "proxy.invalid", 3128, null, null));
        AntennapodHttpClient.reinit();
        server.enqueue(new MockResponse().setBody("direct"));

        try (Response response = get(server.url("/feed.xml").toString())) {
            assertEquals("direct", response.body().string());
        }

        assertEquals("/feed.xml", takeRequest().getPath());
    }

    @Test
    public void proxyWithoutHostIsIgnored() throws Exception {
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "", 3128, null, null));
        AntennapodHttpClient.reinit();
        server.enqueue(new MockResponse().setBody("direct"));

        try (Response response = get(server.url("/feed.xml").toString())) {
            assertTrue(response.isSuccessful());
        }

        assertEquals("/feed.xml", takeRequest().getPath());
    }
}
