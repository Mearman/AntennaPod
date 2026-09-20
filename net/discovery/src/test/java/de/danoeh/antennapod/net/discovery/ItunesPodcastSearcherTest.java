package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.test.categories.IntegrationTest;
import io.reactivex.rxjava3.observers.TestObserver;
import okhttp3.mockwebserver.MockResponse;
import org.json.JSONException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class ItunesPodcastSearcherTest extends SearcherTestBase {

    private ItunesPodcastSearcher newSearcher() {
        return new ItunesPodcastSearcher(urlOf("/search?media=podcast&term=%s"), urlOf("/lookup?id="));
    }

    @Test
    public void searchMapsResultsAndSkipsEntriesWithoutFeedUrl() {
        server.enqueue(new MockResponse().setBody("{\"resultCount\":3,\"results\":["
                + "{\"collectionName\":\"Show A\",\"artworkUrl100\":\"https://img.example/a.jpg\","
                + "\"feedUrl\":\"https://feeds.example/a.xml\",\"artistName\":\"Artist A\"},"
                + "{\"collectionName\":\"Not a podcast\",\"artistName\":\"Nobody\"},"
                + "{\"feedUrl\":\"https://feeds.example/b.xml\"}]}"));

        List<PodcastSearchResult> results = resultsOf(newSearcher().search("show").test());

        assertEquals(2, results.size());
        assertEquals("Show A", results.get(0).title);
        assertEquals("https://img.example/a.jpg", results.get(0).imageUrl);
        assertEquals("https://feeds.example/a.xml", results.get(0).feedUrl);
        assertEquals("Artist A", results.get(0).author);
        assertEquals("Unknown", results.get(1).title);
        assertEquals("", results.get(1).imageUrl);
        assertEquals("https://feeds.example/b.xml", results.get(1).feedUrl);
        assertEquals("Unknown", results.get(1).author);
    }

    @Test
    public void searchEncodesTheQueryInTheRequest() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"results\":[]}"));

        List<PodcastSearchResult> results = resultsOf(newSearcher().search("cats & dogs/über").test());

        assertTrue(results.isEmpty());
        assertEquals("/search?media=podcast&term=cats+%26+dogs%2F%C3%BCber", takeRequest().getPath());
    }

    @Test
    public void searchFailsWhenServerRespondsWithError() {
        server.enqueue(new MockResponse().setResponseCode(503));

        TestObserver<List<PodcastSearchResult>> observer = newSearcher().search("show").test();

        observer.assertError(IOException.class);
        assertTrue(observer.values().isEmpty());
    }

    @Test
    public void searchFailsWhenResponseIsNotJson() {
        server.enqueue(new MockResponse().setBody("<html>rate limited</html>"));

        newSearcher().search("show").test().assertError(JSONException.class);
    }

    @Test
    public void searchFailsWhenResultsAreMissing() {
        server.enqueue(new MockResponse().setBody("{\"resultCount\":0}"));

        newSearcher().search("show").test().assertError(JSONException.class);
    }

    @Test
    public void lookupOfArbitraryUrlReturnsTheFeedUrlFromTheResponse() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"results\":[{\"feedUrl\":\"https://feeds.example/a.xml\"}]}"));

        newSearcher().lookupUrl(urlOf("/custom-lookup")).test()
                .assertNoErrors().assertValue("https://feeds.example/a.xml");

        assertEquals("/custom-lookup", takeRequest().getPath());
    }

    @Test
    public void lookupOfApplePodcastsPageUsesTheNumericIdOfTheLookupApi() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"results\":[{\"feedUrl\":\"https://feeds.example/a.xml\"}]}"));

        newSearcher().lookupUrl("https://podcasts.apple.com/us/podcast/some-show/id123456789?i=1").test()
                .assertNoErrors().assertValue("https://feeds.example/a.xml");

        assertEquals("/lookup?id=123456789", takeRequest().getPath());
    }

    @Test
    public void lookupWithoutFeedUrlReportsArtistAndTrackName() {
        server.enqueue(new MockResponse().setBody("{\"results\":[{\"artistName\":\"Artist\","
                + "\"trackName\":\"Track\"}]}"));

        TestObserver<String> observer = newSearcher().lookupUrl(urlOf("/custom-lookup")).test();

        observer.assertError(error -> error instanceof FeedUrlNotFoundException
                && "Artist".equals(((FeedUrlNotFoundException) error).getArtistName())
                && "Track".equals(((FeedUrlNotFoundException) error).getTrackName())
                && "Result does not specify a feed url".equals(error.getMessage()));
    }

    @Test
    public void lookupFailsWhenServerRespondsWithError() {
        server.enqueue(new MockResponse().setResponseCode(404));

        newSearcher().lookupUrl(urlOf("/custom-lookup")).test().assertError(IOException.class);
    }

    @Test
    public void lookupFailsWhenResultListIsEmpty() {
        server.enqueue(new MockResponse().setBody("{\"results\":[]}"));

        newSearcher().lookupUrl(urlOf("/custom-lookup")).test().assertError(JSONException.class);
    }

    @Test
    public void urlNeedsLookupForAppleDirectoryUrlsOnly() {
        ItunesPodcastSearcher searcher = newSearcher();

        assertTrue(searcher.urlNeedsLookup("https://itunes.apple.com/us/podcast/some-show/id123"));
        assertTrue(searcher.urlNeedsLookup("https://podcasts.apple.com/us/podcast/some-show/id123456789"));
        assertFalse(searcher.urlNeedsLookup("https://podcasts.apple.com/us/podcast/some-show"));
        assertFalse(searcher.urlNeedsLookup("https://example.com/feed.xml"));
        assertEquals("Apple", searcher.getName());
    }
}
