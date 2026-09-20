package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.net.common.UserAgentInterceptor;
import io.reactivex.rxjava3.observers.TestObserver;
import okhttp3.Request;
import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static de.danoeh.antennapod.net.discovery.Errors.errorOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PodcastIndexPodcastSearcherTest {
    private static final String SEARCH_RESULTS = "{\"status\":\"true\",\"feeds\":["
            + "{\"title\":\"First Show\",\"image\":\"https://img.example/1.jpg\","
            + "\"url\":\"https://feeds.example/1.xml\",\"author\":\"Alice\"},"
            + "{\"title\":\"Without Url\",\"author\":\"Bob\"},"
            + "{\"url\":\"https://feeds.example/3.xml\"}]}";

    @Rule
    public final FakeHttpRule http = new FakeHttpRule();
    @Rule
    public final ImmediateSchedulersRule schedulers = new ImmediateSchedulersRule();

    private final PodcastIndexPodcastSearcher searcher = new PodcastIndexPodcastSearcher();

    private static String sha1Hex(String input) throws NoSuchAlgorithmException {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    public void testSearchParsesResultsAndSkipsEntriesWithoutUrl() {
        http.respondWith(200, SEARCH_RESULTS);
        TestObserver<List<PodcastSearchResult>> observer = searcher.search("news").test();
        observer.assertNoErrors();
        List<PodcastSearchResult> results = observer.values().get(0);
        assertEquals(2, results.size());
        assertEquals("First Show", results.get(0).title);
        assertEquals("https://img.example/1.jpg", results.get(0).imageUrl);
        assertEquals("https://feeds.example/1.xml", results.get(0).feedUrl);
        assertEquals("Alice", results.get(0).author);
        assertEquals("Unknown", results.get(1).title);
        assertEquals("", results.get(1).imageUrl);
        assertEquals("https://feeds.example/3.xml", results.get(1).feedUrl);
        assertEquals("Unknown", results.get(1).author);
    }

    @Test
    public void testSearchEncodesQueryInRequestUrl() {
        http.respondWith(200, "{\"feeds\":[]}");
        searcher.search("hello world").test();
        assertEquals("https://api.podcastindex.org/api/1.0/search/byterm?q=hello+world",
                http.onlyRequest().url().toString());
    }

    @Test
    public void testSearchSignsRequestWithApiKeySecretAndCurrentTime() throws NoSuchAlgorithmException {
        http.respondWith(200, "{\"feeds\":[]}");
        long before = System.currentTimeMillis() / 1000L;
        searcher.search("news").test();
        long after = System.currentTimeMillis() / 1000L;

        Request request = http.onlyRequest();
        long signedTime = Long.parseLong(request.header("X-Auth-Date"));
        assertTrue(signedTime >= before && signedTime <= after);
        assertEquals(BuildConfig.PODCASTINDEX_API_KEY, request.header("X-Auth-Key"));
        assertEquals(sha1Hex(BuildConfig.PODCASTINDEX_API_KEY + BuildConfig.PODCASTINDEX_API_SECRET + signedTime),
                request.header("Authorization"));
        assertEquals(UserAgentInterceptor.USER_AGENT, request.header("User-Agent"));
    }

    @Test
    public void testSearchDoesNotSendApiSecretAsHeader() {
        http.respondWith(200, "{\"feeds\":[]}");
        searcher.search("news").test();
        for (String name : http.onlyRequest().headers().names()) {
            for (String value : http.onlyRequest().headers(name)) {
                assertFalse(value.contains(BuildConfig.PODCASTINDEX_API_SECRET));
            }
        }
    }

    @Test
    public void testSearchReportsUnsuccessfulResponseAsIoException() {
        http.respondWith(401, "");
        Throwable error = errorOf(searcher.search("news"));
        assertTrue(error instanceof IOException);
        assertTrue(error.getMessage().contains("code=401"));
    }

    @Test
    public void testSearchReportsMalformedJsonAsJsonException() {
        http.respondWith(200, "oops");
        searcher.search("news").test().assertError(JSONException.class).assertNoValues();
    }

    @Test
    public void testSearchReportsMissingFeedsArrayAsJsonException() {
        http.respondWith(200, "{\"status\":\"true\"}");
        searcher.search("news").test().assertError(JSONException.class).assertNoValues();
    }

    @Test
    public void testSearchReportsNetworkFailure() {
        http.failWith(new IOException("offline"));
        searcher.search("news").test().assertError(IOException.class).assertNoValues();
    }

    @Test
    public void testLookupReturnsInputUrlWithoutNetworkAccess() {
        searcher.lookupUrl("https://example.com/feed.xml").test().assertValue("https://example.com/feed.xml");
        assertTrue(http.requests().isEmpty());
    }

    @Test
    public void testUrlNeverNeedsLookup() {
        assertFalse(searcher.urlNeedsLookup("https://api.podcastindex.org/feed"));
    }

    @Test
    public void testNameIsPodcastIndex() {
        assertEquals("Podcast Index", searcher.getName());
    }
}
