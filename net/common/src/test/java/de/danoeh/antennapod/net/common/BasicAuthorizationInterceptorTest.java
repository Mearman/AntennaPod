package de.danoeh.antennapod.net.common;

import android.content.Context;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.test.categories.IntegrationTest;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class BasicAuthorizationInterceptorTest {
    private static final String PASSWORD_WITH_UMLAUT = "pässword";
    private MockWebServer server;
    private MockWebServer otherServer;

    @Before
    public void setUp() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        AntennapodHttpClient.setCacheDirectory(new File(context.getCacheDir(), "interceptor-test"));
        AntennapodHttpClient.setProxyConfig(null);
        AntennapodHttpClient.reinit();
        server = new MockWebServer();
        server.start();
        otherServer = new MockWebServer();
        otherServer.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
        otherServer.shutdown();
    }

    private static RecordedRequest takeRequest(MockWebServer webServer) throws InterruptedException {
        RecordedRequest request = webServer.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    private static String basic(String username, String password, Charset charset) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(charset));
    }

    private static DownloadRequest downloadRequest(String source, String username, String password) {
        return new DownloadRequest("dest", source, "title", 0, 0, username, password, null, false);
    }

    private Response execute(Request request) throws IOException {
        return AntennapodHttpClient.getHttpClient().newCall(request).execute();
    }

    @Test
    public void unauthorizedResponseIsRetriedWithLatin1ThenUtf8EncodedCredentials() throws Exception {
        String url = server.url("/protected").toString();
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setBody("secret feed"));
        Request request = new Request.Builder().url(url)
                .tag(downloadRequest(url, "user", PASSWORD_WITH_UMLAUT)).build();

        try (Response response = execute(request)) {
            assertEquals(200, response.code());
            assertEquals("secret feed", response.body().string());
        }

        assertNull(takeRequest(server).getHeader("Authorization"));
        assertEquals(basic("user", PASSWORD_WITH_UMLAUT, StandardCharsets.ISO_8859_1),
                takeRequest(server).getHeader("Authorization"));
        assertEquals(basic("user", PASSWORD_WITH_UMLAUT, StandardCharsets.UTF_8),
                takeRequest(server).getHeader("Authorization"));
    }

    @Test
    public void retryStopsAfterFirstSuccessfulEncoding() throws Exception {
        String url = server.url("/protected").toString();
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setBody("ok"));
        Request request = new Request.Builder().url(url).tag(downloadRequest(url, "user", "secret")).build();

        try (Response response = execute(request)) {
            assertEquals(200, response.code());
        }

        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void credentialsEmbeddedInDownloadUrlAreUsedForRetry() throws Exception {
        String source = "http://embedded:token@" + server.getHostName() + ":" + server.getPort() + "/feed";
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setBody("ok"));
        Request request = new Request.Builder().url(server.url("/feed")).tag(downloadRequest(source, null, null))
                .build();

        try (Response response = execute(request)) {
            assertEquals(200, response.code());
        }

        takeRequest(server);
        assertEquals(basic("embedded", "token", StandardCharsets.ISO_8859_1),
                takeRequest(server).getHeader("Authorization"));
    }

    @Test
    public void persistentUnauthorizedResponseIsReturnedAfterBothEncodingsWereTried() throws Exception {
        String url = server.url("/protected").toString();
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(401));
        Request request = new Request.Builder().url(url).tag(downloadRequest(url, "user", "wrong")).build();

        try (Response response = execute(request)) {
            assertEquals(401, response.code());
        }

        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void unauthorizedResponseWithoutDownloadRequestTagIsNotRetried() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));

        try (Response response = execute(new Request.Builder().url(server.url("/protected")).build())) {
            assertEquals(401, response.code());
        }

        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void unauthorizedResponseWithoutCredentialsIsNotRetried() throws Exception {
        String url = server.url("/protected").toString();
        server.enqueue(new MockResponse().setResponseCode(401));
        Request request = new Request.Builder().url(url).tag(downloadRequest(url, null, null)).build();

        try (Response response = execute(request)) {
            assertEquals(401, response.code());
        }

        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void credentialsWithoutPasswordSeparatorAreNotRetried() throws Exception {
        String source = "http://onlyuser@" + server.getHostName() + ":" + server.getPort() + "/feed";
        server.enqueue(new MockResponse().setResponseCode(401));
        Request request = new Request.Builder().url(server.url("/feed")).tag(downloadRequest(source, null, null))
                .build();

        try (Response response = execute(request)) {
            assertEquals(401, response.code());
        }

        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void successfulResponsesPassThroughWithoutExtraRequests() throws Exception {
        String url = server.url("/open").toString();
        server.enqueue(new MockResponse().setBody("open feed"));
        Request request = new Request.Builder().url(url).tag(downloadRequest(url, "user", "secret")).build();

        try (Response response = execute(request)) {
            assertEquals("open feed", response.body().string());
        }

        assertEquals(1, server.getRequestCount());
        assertNull(takeRequest(server).getHeader("Authorization"));
    }

    @Test
    public void authorizationHeaderDroppedByCrossHostRedirectIsRestoredOnRetry() throws Exception {
        String authorization = basic("user", "secret", StandardCharsets.UTF_8);
        server.enqueue(new MockResponse().setResponseCode(302)
                .addHeader("Location", otherServer.url("/target").toString()));
        otherServer.enqueue(new MockResponse().setResponseCode(401));
        otherServer.enqueue(new MockResponse().setBody("target feed"));
        Request request = new Request.Builder().url(server.url("/start"))
                .header("Authorization", authorization).build();

        try (Response response = execute(request)) {
            assertEquals(200, response.code());
            assertEquals("target feed", response.body().string());
        }

        assertEquals(authorization, takeRequest(server).getHeader("Authorization"));
        assertNull(takeRequest(otherServer).getHeader("Authorization"));
        RecordedRequest retried = takeRequest(otherServer);
        assertEquals("/target", retried.getPath());
        assertEquals(authorization, retried.getHeader("Authorization"));
    }
}
