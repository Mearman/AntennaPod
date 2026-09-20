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
public class ItunesPodcastSearcherTest {
    private static final String SEARCH_RESULTS = "{\"resultCount\":3,\"results\":["
            + "{\"collectionName\":\"First Show\",\"artworkUrl100\":\"https://img.example/1.jpg\","
            + "\"feedUrl\":\"https://feeds.example/1.xml\",\"artistName\":\"Alice\"},"
            + "{\"collectionName\":\"Without Feed\",\"artistName\":\"Bob\"},"
            + "{\"feedUrl\":\"https://feeds.example/3.xml\"}]}";
    private static final String PODCASTS_APPLE_URL
            = "https://podcasts.apple.com/us/podcast/some-name/id1234567890?uo=4";

    @Rule
    public final FakeHttpRule http = new FakeHttpRule();
    @Rule
    public final ImmediateSchedulersRule schedulers = new ImmediateSchedulersRule();

    private final ItunesPodcastSearcher searcher = new ItunesPodcastSearcher();

    @Test
    public void testSearchParsesResultsAndSkipsEntriesWithoutFeedUrl() {
        http.respondWith(200, SEARCH_RESULTS);
        TestObserver<List<PodcastSearchResult>> observer = searcher.search("news").test();
        observer.assertNoErrors();
        List<PodcastSearchResult> results = observer.values().get(0);
        assertEquals(2, results.size());
        assertEquals("First Show", results.get(0).title);
        assertEquals("https://img.example/1.jpg", results.get(0).imageUrl);
        assertEquals("https://feeds.example/1.xml", results.get(0).feedUrl);
        assertEquals("Alice", results.get(0).author);
    }

    @Test
    public void testSearchUsesDefaultsForMissingFields() {
        http.respondWith(200, SEARCH_RESULTS);
        PodcastSearchResult result = searcher.search("news").test().values().get(0).get(1);
        assertEquals("Unknown", result.title);
        assertEquals("", result.imageUrl);
        assertEquals("https://feeds.example/3.xml", result.feedUrl);
        assertEquals("Unknown", result.author);
    }

    @Test
    public void testSearchReturnsEmptyListForEmptyResults() {
        http.respondWith(200, "{\"resultCount\":0,\"results\":[]}");
        TestObserver<List<PodcastSearchResult>> observer = searcher.search("news").test();
        observer.assertNoErrors();
        assertTrue(observer.values().get(0).isEmpty());
    }

    @Test
    public void testSearchEncodesQueryInRequestUrl() {
        http.respondWith(200, "{\"results\":[]}");
        searcher.search("hello world & co").test();
        assertEquals("https://itunes.apple.com/search?media=podcast&term=hello+world+%26+co",
                http.onlyRequest().url().toString());
    }

    @Test
    public void testSearchReportsUnsuccessfulResponseAsIoException() {
        http.respondWith(500, "");
        Throwable error = errorOf(searcher.search("news"));
        assertTrue(error instanceof IOException);
        assertTrue(error.getMessage().contains("code=500"));
    }

    @Test
    public void testSearchReportsMalformedJsonAsJsonException() {
        http.respondWith(200, "this is not json");
        searcher.search("news").test().assertError(JSONException.class).assertNoValues();
    }

    @Test
    public void testSearchReportsMissingResultsArrayAsJsonException() {
        http.respondWith(200, "{\"resultCount\":0}");
        searcher.search("news").test().assertError(JSONException.class).assertNoValues();
    }

    @Test
    public void testSearchReportsNetworkFailure() {
        http.failWith(new IOException("offline"));
        Throwable error = errorOf(searcher.search("news"));
        assertTrue(error instanceof IOException);
        assertEquals("offline", error.getMessage());
    }

    @Test
    public void testLookupOfApplePodcastsUrlQueriesLookupEndpointByPodcastId() {
        http.respondWith(200, "{\"results\":[{\"feedUrl\":\"https://feeds.example/show.xml\"}]}");
        TestObserver<String> observer = searcher.lookupUrl(PODCASTS_APPLE_URL).test();
        observer.assertValue("https://feeds.example/show.xml");
        assertEquals("https://itunes.apple.com/lookup?id=1234567890", http.onlyRequest().url().toString());
    }

    @Test
    public void testLookupOfOtherUrlRequestsThatUrlDirectly() {
        http.respondWith(200, "{\"results\":[{\"feedUrl\":\"https://feeds.example/show.xml\"}]}");
        searcher.lookupUrl("https://itunes.apple.com/lookup?id=42").test()
                .assertValue("https://feeds.example/show.xml");
        assertEquals("https://itunes.apple.com/lookup?id=42", http.onlyRequest().url().toString());
    }

    @Test
    public void testLookupWithoutFeedUrlFailsWithArtistAndTrackName() {
        http.respondWith(200, "{\"results\":[{\"artistName\":\"Alice\",\"trackName\":\"Some Track\"}]}");
        Throwable error = errorOf(searcher.lookupUrl(PODCASTS_APPLE_URL));
        assertTrue(error instanceof FeedUrlNotFoundException);
        assertEquals("Alice", ((FeedUrlNotFoundException) error).getArtistName());
        assertEquals("Some Track", ((FeedUrlNotFoundException) error).getTrackName());
    }

    @Test
    public void testLookupReportsUnsuccessfulResponseAsIoException() {
        http.respondWith(404, "");
        Throwable error = errorOf(searcher.lookupUrl(PODCASTS_APPLE_URL));
        assertTrue(error instanceof IOException);
        assertTrue(error.getMessage().contains("code=404"));
    }

    @Test
    public void testLookupWithoutResultsFailsWithJsonException() {
        http.respondWith(200, "{\"results\":[]}");
        searcher.lookupUrl(PODCASTS_APPLE_URL).test().assertError(JSONException.class);
    }

    @Test
    public void testLookupReportsNetworkFailure() {
        http.failWith(new IOException("offline"));
        searcher.lookupUrl(PODCASTS_APPLE_URL).test().assertError(IOException.class);
    }

    @Test
    public void testUrlNeedsLookupForItunesAndApplePodcastsUrlsOnly() {
        assertTrue(searcher.urlNeedsLookup("https://itunes.apple.com/us/podcast/some-name/id1234567890"));
        assertTrue(searcher.urlNeedsLookup(PODCASTS_APPLE_URL));
        assertFalse(searcher.urlNeedsLookup("https://podcasts.apple.com/us/show/some-name"));
        assertFalse(searcher.urlNeedsLookup("https://example.com/feed.xml"));
    }

    @Test
    public void testNameIsApple() {
        assertEquals("Apple", searcher.getName());
    }
}
