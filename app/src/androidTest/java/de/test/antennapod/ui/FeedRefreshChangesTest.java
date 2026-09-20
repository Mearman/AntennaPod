package de.test.antennapod.ui;

import android.content.Intent;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.service.download.StaticContentServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickChildViewWithId;
import static de.test.antennapod.ui.FeedRobot.waitUntilDisplayed;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FeedRefreshChangesTest {
    private static final String FEED_PATH = "/feeds/changes.xml";
    private static final String RSS_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\""
            + " xmlns:atom=\"http://www.w3.org/2005/Atom\""
            + " xmlns:podcast=\"https://podcastindex.org/namespace/1.0\"><channel>";
    private static final String RSS_TAIL = "</channel></rss>";

    private StaticContentServer server;

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        UserPreferences.setAllowMobileFeedRefresh(true);
        EspressoTestUtils.setLaunchScreen(AddFeedFragment.TAG);
        server = new StaticContentServer();
        server.start();
        activityRule.launchActivity(new Intent());
    }

    @After
    public void tearDown() {
        server.stop();
        PodDBAdapter.deleteDatabase();
    }

    private static String enclosure(String file, long length) {
        return "<enclosure url=\"${BASE}/media/" + file + "\" length=\"" + length + "\" type=\"audio/mpeg\"/>";
    }

    private static String item(String guid, String title, String enclosure, String extra) {
        return "<item><guid>" + guid + "</guid><title>" + title + "</title>"
                + "<pubDate>Tue, 03 Jan 2023 10:00:00 +0000</pubDate>" + enclosure + extra + "</item>";
    }

    private String feed(String... items) {
        return RSS_HEAD + "<title>Changes Feed</title><link>https://example.com/changes</link>"
                + "<description>Feed for refresh changes</description>" + String.join("", items) + RSS_TAIL;
    }

    private String pagedFeed(String nextLink, String... items) {
        return RSS_HEAD + "<atom:link rel=\"next\" href=\"" + nextLink + "\"/>"
                + "<title>Changes Feed</title><link>https://example.com/changes</link>"
                + "<description>Feed for refresh changes</description>" + String.join("", items) + RSS_TAIL;
    }

    private String publish(String document) {
        return server.publish(FEED_PATH, "application/rss+xml", document);
    }

    private Feed subscribeTo(String document) throws Exception {
        String url = publish(document);
        FeedRobot.subscribeByUrl(url);
        return FeedRobot.awaitFeed(url);
    }

    private Feed refresh(Feed feed) throws Exception {
        FeedRobot.refreshFromMenu();
        return FeedRobot.reload(feed);
    }

    @Test
    public void changedEnclosureUrlIsAppliedToTheStoredEpisode() throws Exception {
        Feed feed = subscribeTo(feed(item("a", "Episode A", enclosure("a1.mp3", 20000), "")));

        publish(feed(item("a", "Episode A", enclosure("a2.mp3", 30000), "")));
        Feed refreshed = refresh(feed);

        FeedItem item = FeedRobot.itemByGuid(refreshed, "a");
        assertEquals(feed.getId(), refreshed.getId());
        assertEquals(server.getBaseUrl() + "/media/a2.mp3", item.getMedia().getDownloadUrl());
        assertEquals(30000, item.getMedia().getSize());
    }

    @Test
    public void enclosureSizeAndDurationAreRefreshedForTheSameUrl() throws Exception {
        Feed feed = subscribeTo(feed(item("a", "Episode A", enclosure("a1.mp3", 20000), "")));
        assertEquals(0, FeedRobot.itemByGuid(feed, "a").getMedia().getDuration());

        publish(feed(item("a", "Episode A", enclosure("a1.mp3", 45000),
                "<itunes:duration>900</itunes:duration>")));
        Feed refreshed = refresh(feed);

        FeedItem item = FeedRobot.itemByGuid(refreshed, "a");
        assertEquals(server.getBaseUrl() + "/media/a1.mp3", item.getMedia().getDownloadUrl());
        assertEquals(45000, item.getMedia().getSize());
        assertEquals(900000, item.getMedia().getDuration());
    }

    @Test
    public void episodeWithoutEnclosureGainsPlayableMediaOnRefresh() throws Exception {
        Feed feed = subscribeTo(feed("<item><guid>a</guid><title>Episode A</title>"
                + "<pubDate>Tue, 03 Jan 2023 10:00:00 +0000</pubDate></item>"));
        assertFalse(feed.getItems().get(0).hasMedia());

        publish(feed(item("a", "Episode A", enclosure("a.mp3", 20000), "")));
        Feed refreshed = refresh(feed);

        FeedItem item = FeedRobot.itemByGuid(refreshed, "a");
        assertTrue(item.hasMedia());
        assertTrue(item.isNew());
        assertEquals(server.getBaseUrl() + "/media/a.mp3", item.getMedia().getDownloadUrl());
    }

    @Test
    public void nonPagedFeedBecomesPagedWhenTheDocumentGrows() throws Exception {
        Feed feed = subscribeTo(feed(item("a", "Episode A", enclosure("a.mp3", 20000), "")));
        assertNull(feed.getNextPageLink());

        publish(pagedFeed(server.getBaseUrl() + "/feeds/page2.xml",
                item("a", "Episode A", enclosure("a.mp3", 20000), "")));
        Feed refreshed = refresh(feed);

        assertEquals(server.getBaseUrl() + "/feeds/page2.xml", refreshed.getNextPageLink());
    }

    @Test
    public void changedItemPaymentLinkIsStoredOnRefresh() throws Exception {
        String oldTip = "<atom:link rel=\"payment\" href=\"https://example.com/old-tip\"/>";
        String newTip = "<atom:link rel=\"payment\" href=\"https://example.com/new-tip\"/>";
        Feed feed = subscribeTo(feed(item("a", "Episode A", enclosure("a.mp3", 20000), oldTip)));
        assertEquals("https://example.com/old-tip", FeedRobot.itemByGuid(feed, "a").getPaymentLink());

        publish(feed(item("a", "Episode A", enclosure("a.mp3", 20000), newTip)));
        Feed refreshed = refresh(feed);

        assertEquals("https://example.com/new-tip", FeedRobot.itemByGuid(refreshed, "a").getPaymentLink());
    }

    @Test
    public void jsonTranscriptWinsOverSrtWhenBothAreOffered() throws Exception {
        server.publish("/transcripts/episode.json", "application/json", "{\"version\":\"1.0\",\"segments\":[]}");
        server.publish("/transcripts/episode.srt", "application/srt", "1\n00:00:01,000 --> 00:00:02,000\nHello\n");
        String transcripts = "<podcast:transcript url=\"${BASE}/transcripts/episode.srt\" type=\"application/srt\"/>"
                + "<podcast:transcript url=\"${BASE}/transcripts/episode.json\" type=\"application/json\"/>";

        Feed feed = subscribeTo(feed(item("a", "Episode A", enclosure("a.mp3", 20000), transcripts)));

        FeedItem item = FeedRobot.itemByGuid(feed, "a");
        assertEquals(server.getBaseUrl() + "/transcripts/episode.json", item.getTranscriptUrl());
        assertEquals("application/json", item.getTranscriptType());
    }

    @Test
    public void interactedPreviewFeedShowsTheSubscribeHint() throws Exception {
        UserPreferences.setAllowMobileEpisodeDownload(true);
        String url = publish(feed(item("a", "Episode A", enclosure("a.mp3", 20000), "")));
        FeedRobot.addFeedByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);
        assertEquals(Feed.STATE_NOT_SUBSCRIBED, FeedRobot.reload(feed).getState());

        FeedRobot.awaitAssertion(() -> onView(allOf(withText("Episode A"),
                isDescendantOfA(withId(R.id.recyclerView)))).check(matches(isDisplayed())));
        FeedRobot.awaitAssertion(() -> onView(allOf(withText("Episode A"),
                isDescendantOfA(withId(R.id.recyclerView))))
                .perform(clickChildViewWithId(R.id.secondaryActionButton)));
        FeedRobot.awaitCondition(() -> FeedRobot.itemByGuid(FeedRobot.reload(feed), "a").isDownloaded());

        Espresso.pressBackUnconditionally();
        FeedRobot.addFeedByUrl(url);
        waitUntilDisplayed(withId(R.id.subscribeNagLabel), FeedRobot.UI_TIMEOUT_MS);
    }
}
