package de.danoeh.antennapod.net.discovery;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ItunesTopListLoaderTest {
    @Rule
    public final FakeHttpRule http = new FakeHttpRule();

    private Locale originalLocale;
    private Context context;
    private ItunesTopListLoader loader;

    @Before
    public void setUp() {
        originalLocale = Locale.getDefault();
        context = RuntimeEnvironment.getApplication();
        loader = new ItunesTopListLoader(context);
    }

    @After
    public void tearDown() {
        Locale.setDefault(originalLocale);
    }

    private static String entry(String name, String id, String artist, String images) {
        String artistJson = artist == null ? "" : ",\"im:artist\":{\"label\":\"" + artist + "\"}";
        return "{\"im:name\":{\"label\":\"" + name + "\"},\"im:image\":[" + images + "],"
                + "\"id\":{\"attributes\":{\"im:id\":\"" + id + "\"}}" + artistJson + "}";
    }

    private static String image(String url, int height) {
        return "{\"label\":\"" + url + "\",\"attributes\":{\"height\":\"" + height + "\"}}";
    }

    private static String feedOf(String... entries) {
        return "{\"feed\":{\"entry\":[" + String.join(",", entries) + "]}}";
    }

    private static Feed subscribedFeed(String title, String author, int state) {
        Feed feed = new Feed("https://feeds.example/" + title.replace(' ', '-'), null, title);
        feed.setAuthor(author);
        feed.setState(state);
        return feed;
    }

    private List<String> loadTitles(String feedJson, int limit, List<Feed> subscribed) throws Exception {
        http.respondWith(200, feedJson);
        List<String> titles = new ArrayList<>();
        for (PodcastSearchResult result : loader.loadToplist("US", limit, subscribed)) {
            titles.add(result.title);
        }
        return titles;
    }

    @Test
    public void testLoadToplistMapsEntriesToSearchResults() throws Exception {
        http.respondWith(200, feedOf(
                entry("Top One", "111", "Artist One", image("https://img.example/55.png", 55) + ","
                        + image("https://img.example/170.png", 170))));
        List<PodcastSearchResult> results = loader.loadToplist("US", 10, Collections.emptyList());
        assertEquals(1, results.size());
        assertEquals("Top One", results.get(0).title);
        assertEquals("https://img.example/170.png", results.get(0).imageUrl);
        assertEquals("https://itunes.apple.com/lookup?id=111", results.get(0).feedUrl);
        assertEquals("Artist One", results.get(0).author);
    }

    @Test
    public void testLoadToplistUsesFirstImageOfAtLeastHundredPixels() throws Exception {
        http.respondWith(200, feedOf(entry("Top One", "111", "Artist",
                image("https://img.example/60.png", 60) + "," + image("https://img.example/100.png", 100) + ","
                        + image("https://img.example/170.png", 170))));
        assertEquals("https://img.example/100.png",
                loader.loadToplist("US", 10, Collections.emptyList()).get(0).imageUrl);
    }

    @Test
    public void testLoadToplistLeavesImageAndAuthorNullWhenUnavailable() throws Exception {
        http.respondWith(200, feedOf(entry("Top One", "111", null, image("https://img.example/60.png", 60))));
        PodcastSearchResult result = loader.loadToplist("US", 10, Collections.emptyList()).get(0);
        assertNull(result.imageUrl);
        assertNull(result.author);
    }

    @Test
    public void testLoadToplistRequestsCountrySpecificFeedWithStaleCachingAllowed() throws Exception {
        http.respondWith(200, feedOf());
        loader.loadToplist("GB", 10, Collections.emptyList());
        assertEquals("https://itunes.apple.com/GB/rss/toppodcasts/limit=25/explicit=true/json",
                http.onlyRequest().url().toString());
        assertEquals("max-stale=86400", http.onlyRequest().header("Cache-Control"));
    }

    @Test
    public void testLoadToplistWithUnsetCountryUsesCountryOfDefaultLocale() throws Exception {
        Locale.setDefault(Locale.GERMANY);
        http.respondWith(200, feedOf());
        loader.loadToplist(ItunesTopListLoader.COUNTRY_CODE_UNSET, 10, Collections.emptyList());
        assertTrue(http.onlyRequest().url().toString().startsWith("https://itunes.apple.com/DE/"));
    }

    @Test
    public void testLoadToplistWithUnsetCountryFallsBackToUsIfLocaleCountryHasNoData() throws Exception {
        Locale.setDefault(Locale.GERMANY);
        http.respondWith(request -> request.url().toString().contains("/DE/")
                ? FakeHttpRule.response(request, 400, "")
                : FakeHttpRule.response(request, 200, feedOf(entry("Top One", "111", "Artist", image("u", 100)))));
        List<PodcastSearchResult> results = loader.loadToplist(ItunesTopListLoader.COUNTRY_CODE_UNSET, 10,
                Collections.emptyList());
        assertEquals(1, results.size());
        assertEquals(2, http.requests().size());
        assertTrue(http.requests().get(1).url().toString().startsWith("https://itunes.apple.com/US/"));
    }

    @Test
    public void testLoadToplistWithUnsetCountryFailsWhenUsFallbackAlsoFails() {
        Locale.setDefault(Locale.GERMANY);
        http.respondWith(400, "");
        assertThrows(IOException.class,
                () -> loader.loadToplist(ItunesTopListLoader.COUNTRY_CODE_UNSET, 10, Collections.emptyList()));
        assertEquals(2, http.requests().size());
    }

    @Test
    public void testLoadToplistOfExplicitCountryWithoutDataFailsWithoutFallback() {
        http.respondWith(400, "");
        IOException error = assertThrows(IOException.class,
                () -> loader.loadToplist("GB", 10, Collections.emptyList()));
        assertEquals("iTunes does not have data for the selected country.", error.getMessage());
        assertEquals(1, http.requests().size());
    }

    @Test
    public void testLoadToplistFailsWithPrefixedResponseForOtherErrorCodes() {
        http.respondWith(500, "");
        IOException error = assertThrows(IOException.class,
                () -> loader.loadToplist("GB", 10, Collections.emptyList()));
        assertTrue(error.getMessage().startsWith(context.getString(R.string.error_msg_prefix)));
        assertTrue(error.getMessage().contains("code=500"));
    }

    @Test
    public void testLoadToplistPropagatesNetworkFailureOfExplicitCountry() {
        http.failWith(new IOException("offline"));
        IOException error = assertThrows(IOException.class,
                () -> loader.loadToplist("GB", 10, Collections.emptyList()));
        assertEquals("offline", error.getMessage());
    }

    @Test
    public void testLoadToplistReturnsEmptyListWhenFeedHasNoEntries() throws Exception {
        http.respondWith(200, "{\"feed\":{}}");
        assertTrue(loader.loadToplist("US", 10, Collections.emptyList()).isEmpty());
        http.respondWith(200, "{}");
        assertTrue(loader.loadToplist("US", 10, Collections.emptyList()).isEmpty());
    }

    @Test
    public void testLoadToplistFailsOnMalformedJson() {
        http.respondWith(200, "not json");
        assertThrows(JSONException.class, () -> loader.loadToplist("US", 10, Collections.emptyList()));
    }

    @Test
    public void testLoadToplistFailsOnEntryWithoutName() {
        http.respondWith(200, "{\"feed\":{\"entry\":[{}]}}");
        assertThrows(JSONException.class, () -> loader.loadToplist("US", 10, Collections.emptyList()));
    }

    @Test
    public void testLoadToplistStopsAtLimit() throws Exception {
        String feed = feedOf(entry("One", "1", "A", image("u", 100)), entry("Two", "2", "A", image("u", 100)),
                entry("Three", "3", "A", image("u", 100)));
        assertEquals(List.of("One", "Two"), loadTitles(feed, 2, Collections.emptyList()));
    }

    @Test
    public void testLoadToplistOmitsSubscribedPodcastsBeforeApplyingLimit() throws Exception {
        String feed = feedOf(entry("One", "1", "A", image("u", 100)), entry("Two", "2", "A", image("u", 100)),
                entry("Three", "3", "A", image("u", 100)));
        List<Feed> subscribed = List.of(subscribedFeed("One", "Author", Feed.STATE_SUBSCRIBED));
        assertEquals(List.of("Two", "Three"), loadTitles(feed, 2, subscribed));
    }

    @Test
    public void testSubscribedPodcastIsMatchedByTrimmedTitle() throws Exception {
        String feed = feedOf(entry("One", "1", "A", image("u", 100)), entry("Two", "2", "A", image("u", 100)));
        List<Feed> subscribed = List.of(subscribedFeed("  One ", "Author", Feed.STATE_ARCHIVED));
        assertEquals(List.of("Two"), loadTitles(feed, 10, subscribed));
    }

    @Test
    public void testUnsubscribedFeedsDoNotHideSuggestions() throws Exception {
        String feed = feedOf(entry("One", "1", "A", image("u", 100)));
        List<Feed> subscribed = List.of(subscribedFeed("One", "Author", Feed.STATE_NOT_SUBSCRIBED));
        assertEquals(List.of("One"), loadTitles(feed, 10, subscribed));
    }

    @Test
    public void testFeedsWithoutAuthorDoNotHideSuggestions() throws Exception {
        String feed = feedOf(entry("One", "1", "A", image("u", 100)));
        List<Feed> subscribed = List.of(subscribedFeed("One", null, Feed.STATE_SUBSCRIBED));
        assertEquals(List.of("One"), loadTitles(feed, 10, subscribed));
    }
}
