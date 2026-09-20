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
public class FyydPodcastSearcherTest extends SearcherTestBase {

    private FyydPodcastSearcher newSearcher() {
        return new FyydPodcastSearcher(urlOf("/search/podcast?title=%s&count=10"));
    }

    @Test
    public void searchMapsFyydFieldsToSearchResults() {
        server.enqueue(new MockResponse().setBody("{\"data\":["
                + "{\"title\":\"Show A\",\"thumbImageURL\":\"https://img.example/a.jpg\","
                + "\"xmlURL\":\"https://feeds.example/a.xml\",\"author\":\"Artist A\"},"
                + "{\"xmlURL\":\"https://feeds.example/b.xml\"}]}"));

        List<PodcastSearchResult> results = resultsOf(newSearcher().search("show").test());

        assertEquals(2, results.size());
        assertEquals("Show A", results.get(0).title);
        assertEquals("https://img.example/a.jpg", results.get(0).imageUrl);
        assertEquals("https://feeds.example/a.xml", results.get(0).feedUrl);
        assertEquals("Artist A", results.get(0).author);
        assertEquals("Unknown", results.get(1).title);
        assertEquals("", results.get(1).imageUrl);
        assertEquals("Unknown", results.get(1).author);
    }

    @Test
    public void searchEncodesTheQueryInTheRequest() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"data\":[]}"));

        List<PodcastSearchResult> results = resultsOf(newSearcher().search("news & views").test());

        assertTrue(results.isEmpty());
        assertEquals("/search/podcast?title=news+%26+views&count=10", takeRequest().getPath());
    }

    @Test
    public void searchFailsWhenServerRespondsWithError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        newSearcher().search("show").test().assertError(IOException.class);
    }

    @Test
    public void searchFailsWhenDataArrayIsMissing() {
        server.enqueue(new MockResponse().setBody("{\"status\":0}"));

        TestObserver<List<PodcastSearchResult>> observer = newSearcher().search("show").test();

        observer.assertError(error -> error instanceof IOException && "Null response".equals(error.getMessage()));
    }

    @Test
    public void searchFailsWhenResponseIsNotJson() {
        server.enqueue(new MockResponse().setBody("not json"));

        newSearcher().search("show").test().assertError(JSONException.class);
    }

    @Test
    public void lookupReturnsTheGivenUrlWithoutContactingTheServer() {
        newSearcher().lookupUrl("https://feeds.example/a.xml").test()
                .assertNoErrors().assertValue("https://feeds.example/a.xml");

        assertEquals(0, server.getRequestCount());
        assertFalse(newSearcher().urlNeedsLookup("https://feeds.example/a.xml"));
        assertEquals("fyyd", newSearcher().getName());
    }
}
