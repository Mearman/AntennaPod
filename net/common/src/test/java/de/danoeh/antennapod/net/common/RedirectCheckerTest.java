package de.danoeh.antennapod.net.common;

import android.content.Context;
import de.danoeh.antennapod.test.categories.IntegrationTest;
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
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class RedirectCheckerTest {
    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        AntennapodHttpClient.setCacheDirectory(new File(context.getCacheDir(), "redirect-test"));
        AntennapodHttpClient.setProxyConfig(null);
        AntennapodHttpClient.reinit();
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void permanentRedirectResponseYieldsNewUrl() {
        server.enqueue(new MockResponse().setResponseCode(301).addHeader("Location", "/moved"));
        server.enqueue(new MockResponse());

        String newUrl = RedirectChecker.getNewUrlIfPermanentRedirect(server.url("/old").toString());

        assertEquals(server.url("/moved").toString(), newUrl);
    }

    @Test
    public void permanentRedirectWithStatus308YieldsNewUrl() {
        server.enqueue(new MockResponse().setResponseCode(308).addHeader("Location", "/moved"));
        server.enqueue(new MockResponse());

        String newUrl = RedirectChecker.getNewUrlIfPermanentRedirect(server.url("/old").toString());

        assertEquals(server.url("/moved").toString(), newUrl);
    }

    @Test
    public void temporaryRedirectYieldsNoNewUrl() {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/temporary"));
        server.enqueue(new MockResponse());

        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(server.url("/old").toString()));
    }

    @Test
    public void urlWithoutRedirectYieldsNoNewUrl() {
        server.enqueue(new MockResponse());

        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(server.url("/stable").toString()));
    }

    @Test
    public void permanentRedirectIsDetectedFromTheFirstHopOfAChain() {
        server.enqueue(new MockResponse().setResponseCode(301).addHeader("Location", "/second"));
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/third"));
        server.enqueue(new MockResponse());

        String newUrl = RedirectChecker.getNewUrlIfPermanentRedirect(server.url("/first").toString());

        assertEquals(server.url("/second").toString(), newUrl);
    }

    @Test
    public void unreachableServerYieldsNoNewUrl() throws IOException {
        String url = server.url("/gone").toString();
        server.shutdown();

        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(url));
    }

    @Test
    public void redirectCheckUsesHeadRequests() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(301).addHeader("Location", "/moved"));
        server.enqueue(new MockResponse());

        RedirectChecker.getNewUrlIfPermanentRedirect(server.url("/old").toString());

        RecordedRequest first = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(first);
        assertEquals("HEAD", first.getMethod());
        RecordedRequest second = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(second);
        assertEquals("HEAD", second.getMethod());
        assertEquals("/moved", second.getPath());
    }

    @Test
    public void finalUrlFollowsAllRedirects() {
        server.enqueue(new MockResponse().setResponseCode(301).addHeader("Location", "/second"));
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/third"));
        server.enqueue(new MockResponse());

        assertEquals(server.url("/third").toString(), RedirectChecker.getFinalUrl(server.url("/first").toString()));
    }

    @Test
    public void finalUrlOfNonRedirectingUrlIsUnchanged() {
        String url = server.url("/stable").toString();
        server.enqueue(new MockResponse());

        assertEquals(url, RedirectChecker.getFinalUrl(url));
    }

    @Test
    public void finalUrlOfUnreachableServerIsTheOriginalUrl() throws IOException {
        String url = server.url("/gone").toString();
        server.shutdown();

        assertEquals(url, RedirectChecker.getFinalUrl(url));
    }

    @Test
    public void finalUrlLeavesNonHttpAndEmptyUrlsUntouched() {
        assertEquals("", RedirectChecker.getFinalUrl(""));
        assertEquals("ftp://example.com/feed", RedirectChecker.getFinalUrl("ftp://example.com/feed"));
        assertEquals(0, server.getRequestCount());
    }
}
