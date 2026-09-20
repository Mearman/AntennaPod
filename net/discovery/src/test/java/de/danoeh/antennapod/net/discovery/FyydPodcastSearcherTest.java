package de.danoeh.antennapod.net.discovery;

import io.reactivex.rxjava3.observers.TestObserver;
import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.List;

import static de.danoeh.antennapod.net.discovery.Errors.errorOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FyydPodcastSearcherTest {
    private static final String SEARCH_RESULTS = "{\"status\":1,\"data\":["
            + "{\"title\":\"First Show\",\"thumbImageURL\":\"https://img.example/1.jpg\","
            + "\"xmlURL\":\"https://feeds.example/1.xml\",\"author\":\"Alice\"},"
            + "{\"id\":2}]}";

    @Rule
    public final FakeHttpRule http = new FakeHttpRule();
    @Rule
    public final ImmediateSchedulersRule schedulers = new ImmediateSchedulersRule();

    private final FyydPodcastSearcher searcher = new FyydPodcastSearcher();

    @Test
    public void testSearchMapsFyydFieldsToSearchResults() {
        http.respondWith(200, SEARCH_RESULTS);
        TestObserver<List<PodcastSearchResult>> observer = searcher.search("news").test();
        observer.assertNoErrors();
        PodcastSearchResult result = observer.values().get(0).get(0);
        assertEquals("First Show", result.title);
        assertEquals("https://img.example/1.jpg", result.imageUrl);
        assertEquals("https://feeds.example/1.xml", result.feedUrl);
        assertEquals("Alice", result.author);
    }

    @Test
    public void testSearchKeepsEntriesWithoutFeedUrlUsingDefaults() {
        http.respondWith(200, SEARCH_RESULTS);
        List<PodcastSearchResult> results = searcher.search("news").test().values().get(0);
        assertEquals(2, results.size());
        assertEquals("Unknown", results.get(1).title);
        assertEquals("", results.get(1).imageUrl);
        assertEquals("", results.get(1).feedUrl);
        assertEquals("Unknown", results.get(1).author);
    }

    @Test
    public void testSearchReturnsEmptyListForEmptyData() {
        http.respondWith(200, "{\"data\":[]}");
        TestObserver<List<PodcastSearchResult>> observer = searcher.search("news").test();
        observer.assertNoErrors();
        assertTrue(observer.values().get(0).isEmpty());
    }

    @Test
    public void testSearchEncodesQueryAndRequestsTenResults() {
        http.respondWith(200, "{\"data\":[]}");
        searcher.search("café & tea").test();
        assertEquals("https://api.fyyd.de/0.2/search/podcast?title=caf%C3%A9+%26+tea&count=10",
                http.onlyRequest().url().toString());
    }

    @Test
    public void testSearchReportsMissingDataArrayAsNullResponse() {
        http.respondWith(200, "{\"status\":0}");
        Throwable error = errorOf(searcher.search("news"));
        assertTrue(error instanceof IOException);
        assertEquals("Null response", error.getMessage());
    }

    @Test
    public void testSearchReportsUnsuccessfulResponseAsIoException() {
        http.respondWith(503, "");
        Throwable error = errorOf(searcher.search("news"));
        assertTrue(error instanceof IOException);
        assertTrue(error.getMessage().contains("code=503"));
    }

    @Test
    public void testSearchReportsMalformedJsonAsJsonException() {
        http.respondWith(200, "<html>not json</html>");
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
        assertFalse(searcher.urlNeedsLookup("https://api.fyyd.de/0.2/podcast?id=1"));
    }

    @Test
    public void testNameIsFyyd() {
        assertEquals("fyyd", searcher.getName());
    }
}
