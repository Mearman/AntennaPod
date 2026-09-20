package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.MockResponse;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class ItunesTopListLoaderTest extends SearcherTestBase {
    private Locale originalLocale;

    @Before
    public void rememberLocale() {
        originalLocale = Locale.getDefault();
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    private ItunesTopListLoader newLoader() {
        return new ItunesTopListLoader(context, urlOf("/%s/rss/toppodcasts/json"));
    }

    private static String entry(String title, String id, String author) {
        String artist = author == null ? "" : ",\"im:artist\":{\"label\":\"" + author + "\"}";
        return "{\"im:name\":{\"label\":\"" + title + "\"},"
                + "\"im:image\":[{\"label\":\"https://img.example/" + id + "-55.jpg\",\"attributes\":{\"height\":\"55\"}},"
                + "{\"label\":\"https://img.example/" + id + "-170.jpg\",\"attributes\":{\"height\":\"170\"}}],"
                + "\"id\":{\"attributes\":{\"im:id\":\"" + id + "\"}}" + artist + "}";
    }

    private static String toplist(String... entries) {
        return "{\"feed\":{\"entry\":[" + String.join(",", entries) + "]}}";
    }

    private static Feed subscribedFeed(String title, String author) {
        Feed feed = new Feed("https://feeds.example/" + title + ".xml", null, title);
        feed.setAuthor(author);
        return feed;
    }

    @Test
    public void toplistMapsEntriesToSearchResultsWithLookupUrlAndFirstLargeImage() throws Exception {
        server.enqueue(new MockResponse().setBody(toplist(entry("Show A", "111", "Artist A"))));

        List<PodcastSearchResult> results = newLoader().loadToplist("us", 10, Collections.emptyList());

        assertEquals(1, results.size());
        assertEquals("Show A", results.get(0).title);
        assertEquals("https://img.example/111-170.jpg", results.get(0).imageUrl);
        assertEquals("https://itunes.apple.com/lookup?id=111", results.get(0).feedUrl);
        assertEquals("Artist A", results.get(0).author);
        assertEquals("/us/rss/toppodcasts/json", takeRequest().getPath());
    }

    @Test
    public void toplistEntryWithoutArtistHasNoAuthor() throws Exception {
        server.enqueue(new MockResponse().setBody(toplist(entry("Show A", "111", null))));

        List<PodcastSearchResult> results = newLoader().loadToplist("us", 10, Collections.emptyList());

        assertNull(results.get(0).author);
    }

    @Test
    public void toplistWithoutEntriesIsEmpty() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"feed\":{\"author\":{}}}"));

        assertTrue(newLoader().loadToplist("us", 10, Collections.emptyList()).isEmpty());
    }

    @Test
    public void toplistLeavesOutSubscribedPodcastsAndRespectsTheLimit() throws Exception {
        server.enqueue(new MockResponse().setBody(toplist(
                entry("Subscribed", "1", "Author"),
                entry("Second", "2", "Author"),
                entry("Third", "3", "Author"),
                entry("Fourth", "4", "Author"))));
        List<Feed> subscribed = new ArrayList<>();
        subscribed.add(subscribedFeed(" Subscribed ", "Author"));

        List<PodcastSearchResult> results = newLoader().loadToplist("us", 2, subscribed);

        assertEquals(2, results.size());
        assertEquals("Second", results.get(0).title);
        assertEquals("Third", results.get(1).title);
    }

    @Test
    public void toplistKeepsPodcastsThatAreOnlyKnownAsUnsubscribedOrWithoutAuthor() throws Exception {
        server.enqueue(new MockResponse().setBody(toplist(
                entry("Archived", "1", "Author"),
                entry("Untitled author", "2", "Author"))));
        Feed notSubscribed = subscribedFeed("Archived", "Author");
        notSubscribed.setState(Feed.STATE_NOT_SUBSCRIBED);
        Feed withoutAuthor = subscribedFeed("Untitled author", null);

        List<PodcastSearchResult> results = newLoader().loadToplist("us", 10,
                Arrays.asList(notSubscribed, withoutAuthor));

        assertEquals(2, results.size());
    }

    @Test
    public void unsetCountryUsesTheCountryOfTheDeviceLocale() throws Exception {
        Locale.setDefault(new Locale("de", "DE"));
        server.enqueue(new MockResponse().setBody(toplist(entry("Show A", "111", "Artist A"))));

        List<PodcastSearchResult> results = newLoader()
                .loadToplist(ItunesTopListLoader.COUNTRY_CODE_UNSET, 10, Collections.emptyList());

        assertEquals(1, results.size());
        assertEquals("/DE/rss/toppodcasts/json", takeRequest().getPath());
    }

    @Test
    public void unsetCountryFallsBackToUnitedStatesWhenTheLocaleCountryFails() throws Exception {
        Locale.setDefault(new Locale("xx", "XX"));
        server.enqueue(new MockResponse().setResponseCode(400));
        server.enqueue(new MockResponse().setBody(toplist(entry("Show A", "111", "Artist A"))));

        List<PodcastSearchResult> results = newLoader()
                .loadToplist(ItunesTopListLoader.COUNTRY_CODE_UNSET, 10, Collections.emptyList());

        assertEquals(1, results.size());
        assertEquals("/XX/rss/toppodcasts/json", takeRequest().getPath());
        assertEquals("/US/rss/toppodcasts/json", takeRequest().getPath());
    }

    @Test
    public void explicitCountryWithoutDataFailsWithoutFallback() {
        server.enqueue(new MockResponse().setResponseCode(400));

        IOException exception = assertThrows(IOException.class,
                () -> newLoader().loadToplist("xx", 10, Collections.emptyList()));

        assertEquals("iTunes does not have data for the selected country.", exception.getMessage());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void serverErrorIsReportedWithTheGenericErrorPrefix() {
        server.enqueue(new MockResponse().setResponseCode(503));

        IOException exception = assertThrows(IOException.class,
                () -> newLoader().loadToplist("us", 10, Collections.emptyList()));

        assertTrue(exception.getMessage().startsWith(context.getString(R.string.error_msg_prefix)));
        assertTrue(exception.getMessage().contains("503"));
    }

    @Test
    public void malformedToplistIsReportedAsJsonError() {
        server.enqueue(new MockResponse().setBody("not json"));

        assertThrows(JSONException.class, () -> newLoader().loadToplist("us", 10, Collections.emptyList()));
    }
}
