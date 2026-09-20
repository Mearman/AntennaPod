package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.net.common.UserAgentInterceptor;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PodcastIndexPodcastSearcherTest extends SearcherTestBase {

    private PodcastIndexPodcastSearcher newSearcher() {
        return new PodcastIndexPodcastSearcher(urlOf("/search/byterm?q=%s"));
    }

    private static String sha1Hex(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    @Test
    public void searchMapsFeedsToSearchResultsAndSkipsEntriesWithoutUrl() {
        server.enqueue(new MockResponse().setBody("{\"status\":\"true\",\"feeds\":["
                + "{\"title\":\"Show A\",\"image\":\"https://img.example/a.jpg\","
                + "\"url\":\"https://feeds.example/a.xml\",\"author\":\"Artist A\"},"
                + "{\"title\":\"Feed without url\"},"
                + "{\"url\":\"https://feeds.example/b.xml\"}]}"));

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
    public void searchSendsAuthenticationHeadersDerivedFromKeySecretAndTime() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"feeds\":[]}"));

        resultsOf(newSearcher().search("radio & tv").test());

        RecordedRequest request = takeRequest();
        assertEquals("/search/byterm?q=radio+%26+tv", request.getPath());
        String authDate = request.getHeader("X-Auth-Date");
        assertTrue(authDate.matches("\\d+"));
        assertEquals(BuildConfig.PODCASTINDEX_API_KEY, request.getHeader("X-Auth-Key"));
        assertEquals(sha1Hex(BuildConfig.PODCASTINDEX_API_KEY + BuildConfig.PODCASTINDEX_API_SECRET + authDate),
                request.getHeader("Authorization"));
        assertEquals(UserAgentInterceptor.USER_AGENT, request.getHeader("User-Agent"));
    }

    @Test
    public void searchFailsWhenServerRespondsWithError() {
        server.enqueue(new MockResponse().setResponseCode(401));

        newSearcher().search("show").test().assertError(IOException.class);
    }

    @Test
    public void searchFailsWhenFeedsAreMissing() {
        server.enqueue(new MockResponse().setBody("{\"status\":\"false\"}"));

        newSearcher().search("show").test().assertError(JSONException.class);
    }

    @Test
    public void lookupReturnsTheGivenUrlWithoutContactingTheServer() {
        PodcastIndexPodcastSearcher searcher = newSearcher();

        searcher.lookupUrl("https://feeds.example/a.xml").test()
                .assertNoErrors().assertValue("https://feeds.example/a.xml");

        assertEquals(0, server.getRequestCount());
        assertFalse(searcher.urlNeedsLookup("https://feeds.example/a.xml"));
        assertEquals("Podcast Index", searcher.getName());
    }
}
