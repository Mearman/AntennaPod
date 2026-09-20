package de.test.antennapod.ui;

import android.content.Intent;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.service.download.StaticContentServer;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FeedRefreshTest {
    private static final String FEED_PATH = "/feeds/live.xml";
    private static final String RSS_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\"><channel>";
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

    private static String episode(String guid, String title, String pubDate, String extra) {
        return "<item><guid>" + guid + "</guid><title>" + title + "</title><pubDate>" + pubDate + "</pubDate>"
                + "<enclosure url=\"${BASE}/media/" + guid + ".mp3\" length=\"20000\" type=\"audio/mpeg\"/>"
                + extra + "</item>";
    }

    private static String feed(String title, String... items) {
        return RSS_HEAD + "<title>" + title + "</title><link>https://example.com/live</link>"
                + String.join("", items) + RSS_TAIL;
    }

    private static final String EPISODE_A = episode("a", "Episode A", "Tue, 03 Jan 2023 10:00:00 +0000", "");
    private static final String EPISODE_B = episode("b", "Episode B", "Mon, 02 Jan 2023 10:00:00 +0000", "");
    private static final String EPISODE_C = episode("c", "Episode C", "Wed, 04 Jan 2023 10:00:00 +0000", "");

    private String publishLive(String document) {
        return server.publish(FEED_PATH, "application/rss+xml", document);
    }

    private Feed subscribeToLiveFeed(String document) throws Exception {
        String url = publishLive(document);
        FeedRobot.subscribeByUrl(url);
        return FeedRobot.awaitFeed(url);
    }

    private List<DownloadResult> awaitDownloadLogEntries(Feed feed, int atLeast) {
        Awaitility.await().atMost(FeedRobot.DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> DBReader.getFeedDownloadLog(feed.getId(), 50).size() >= atLeast);
        return DBReader.getFeedDownloadLog(feed.getId(), 50);
    }

    @Test
    public void refreshAddsNewEpisodesAndShowsThemInTheList() throws Exception {
        Feed feed = subscribeToLiveFeed(feed("Live Feed", EPISODE_A, EPISODE_B));
        assertEquals(2, feed.getItems().size());

        publishLive(feed("Live Feed", EPISODE_A, EPISODE_B, EPISODE_C));
        FeedRobot.refreshFromMenu();

        waitForViewGlobally(allOf(withId(R.id.txtvTitle), withText("Episode C")), FeedRobot.UI_TIMEOUT_MS);
        Feed refreshed = FeedRobot.reload(feed);
        assertEquals(3, refreshed.getItems().size());
        assertTrue(FeedRobot.itemByGuid(refreshed, "c").isNew());
        assertEquals(FeedRobot.itemByGuid(feed, "a").getId(), FeedRobot.itemByGuid(refreshed, "a").getId());
        assertFalse(refreshed.hasLastUpdateFailed());
    }

    @Test
    public void refreshUpdatesChangedEpisodesInPlace() throws Exception {
        Feed feed = subscribeToLiveFeed(feed("Live Feed", EPISODE_A,
                episode("b", "Episode B", "Mon, 02 Jan 2023 10:00:00 +0000", "<description>Old text</description>")));

        publishLive(feed("Live Feed Renamed", EPISODE_A,
                episode("b", "Episode B edited", "Mon, 02 Jan 2023 10:00:00 +0000",
                        "<description>Newer and longer text</description>")));
        FeedRobot.refreshFromMenu();

        Awaitility.await().atMost(FeedRobot.DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> "Live Feed Renamed".equals(FeedRobot.reload(feed).getTitle()));
        Feed refreshed = FeedRobot.reload(feed);
        assertEquals(2, refreshed.getItems().size());
        FeedItem edited = FeedRobot.itemByGuid(refreshed, "b");
        assertEquals("Episode B edited", edited.getTitle());
        assertEquals("Newer and longer text", edited.getDescription());
        assertEquals(FeedRobot.itemByGuid(feed, "b").getId(), edited.getId());
        onView(allOf(withId(R.id.txtvTitle), withText("Episode B edited"))).check(matches(isDisplayed()));
    }

    @Test
    public void episodeListedTwiceIsReportedInTheDownloadLog() throws Exception {
        Feed feed = subscribeToLiveFeed(feed("Live Feed", EPISODE_A));

        publishLive(feed("Live Feed", EPISODE_A, EPISODE_B, EPISODE_B));
        FeedRobot.refreshFromMenu();

        List<DownloadResult> log = awaitDownloadLogEntries(feed, 1);
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, log.get(0).getReason());
        assertTrue(log.get(0).getReasonDetailed().contains("added the same episode twice"));
        Awaitility.await().atMost(FeedRobot.DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> FeedRobot.reload(feed).getItems().size() == 2);
    }

    @Test
    public void episodeWithChangedGuidIsRepairedInsteadOfDuplicated() throws Exception {
        Feed feed = subscribeToLiveFeed(feed("Live Feed", EPISODE_A, EPISODE_B));
        long episodeBId = FeedRobot.itemByGuid(feed, "b").getId();

        publishLive(feed("Live Feed", EPISODE_A, episode("b-new", "Episode B", "Mon, 02 Jan 2023 10:00:00 +0000",
                "")));
        FeedRobot.refreshFromMenu();

        List<DownloadResult> log = awaitDownloadLogEntries(feed, 1);
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, log.get(0).getReason());
        assertTrue(log.get(0).getReasonDetailed().contains("changed the ID of an existing episode"));
        Feed refreshed = FeedRobot.reload(feed);
        assertEquals(2, refreshed.getItems().size());
        assertEquals(episodeBId, FeedRobot.itemByGuid(refreshed, "b-new").getId());
    }

    @Test
    public void unchangedFeedIsAnsweredWithNotModified() throws Exception {
        String etag = "\"revision-1\"";
        String url = server.publishWithValidators(FEED_PATH, "application/rss+xml",
                feed("Live Feed", EPISODE_A, EPISODE_B), null, etag);
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        refreshAllAndAwaitStoredValidators(feed);
        int before = server.requestsFor(FEED_PATH).size();
        FeedRobot.refreshAllFromSubscriptions();

        awaitRequestsAfter(before);
        List<StaticContentServer.RecordedRequest> requests = server.requestsFor(FEED_PATH);
        assertEquals(etag, requests.get(requests.size() - 1).header("If-None-Match"));
        assertEquals(2, FeedRobot.reload(feed).getItems().size());
        assertFalse(FeedRobot.reload(feed).hasLastUpdateFailed());
    }

    @Test
    public void recentLastModifiedDateIsSentBack() throws Exception {
        String lastModified = StaticContentServer.httpDate(System.currentTimeMillis());
        String url = server.publishWithValidators(FEED_PATH, "application/rss+xml",
                feed("Live Feed", EPISODE_A), lastModified, null);
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        refreshAllAndAwaitStoredValidators(feed);
        int before = server.requestsFor(FEED_PATH).size();
        FeedRobot.refreshAllFromSubscriptions();

        awaitRequestsAfter(before);
        List<StaticContentServer.RecordedRequest> requests = server.requestsFor(FEED_PATH);
        assertEquals(lastModified, requests.get(requests.size() - 1).header("If-Modified-Since"));
    }

    private void refreshAllAndAwaitStoredValidators(Feed feed) throws Exception {
        int before = server.requestsFor(FEED_PATH).size();
        FeedRobot.refreshAllFromSubscriptions();
        awaitRequestsAfter(before);
        Awaitility.await().atMost(FeedRobot.DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> FeedRobot.reload(feed).getLastModified() != null);
    }

    private void awaitRequestsAfter(int before) {
        Awaitility.await().atMost(FeedRobot.DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> server.requestsFor(FEED_PATH).size() > before);
    }

    @Test
    public void permanentRedirectMovesTheFeedToTheNewAddress() throws Exception {
        Feed feed = subscribeToLiveFeed(feed("Moved Feed", EPISODE_A));
        String newUrl = server.publish("/feeds/moved.xml", "application/rss+xml", feed("Moved Feed", EPISODE_A));
        server.redirect(FEED_PATH, 301, "/feeds/moved.xml");

        FeedRobot.refreshFromMenu();

        Awaitility.await().atMost(FeedRobot.DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> newUrl.equals(FeedRobot.reload(feed).getDownloadUrl()));
    }

    @Test
    public void temporaryRedirectKeepsTheOriginalAddress() throws Exception {
        server.publish("/feeds/target.xml", "application/rss+xml", feed("Redirected Feed", EPISODE_A));
        String url = server.redirect("/feeds/temporary.xml", 302, server.getBaseUrl() + "/feeds/target.xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        FeedRobot.refreshFromMenu();
        Awaitility.await().atMost(FeedRobot.DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> server.requestsFor("/feeds/target.xml").size() >= 3);

        assertEquals(url, FeedRobot.reload(feed).getDownloadUrl());
    }

    @Test
    public void newFeedUrlTagMovesTheFeed() throws Exception {
        Feed feed = subscribeToLiveFeed(feed("Relocated", EPISODE_A));
        String newUrl = server.publish("/feeds/relocated.xml", "application/rss+xml", feed("Relocated", EPISODE_A));
        publishLive(RSS_HEAD + "<title>Relocated</title><itunes:new-feed-url>" + newUrl
                + "</itunes:new-feed-url>" + EPISODE_A + RSS_TAIL);

        FeedRobot.refreshFromMenu();

        Awaitility.await().atMost(FeedRobot.DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> newUrl.equals(FeedRobot.reload(feed).getDownloadUrl()));
    }

    @Test
    public void failingRefreshIsReportedAndClearedByTheNextSuccess() throws Exception {
        Feed feed = subscribeToLiveFeed(feed("Live Feed", EPISODE_A));

        String[] brokenDocuments = {
                "<rss version=\"2.0\"><channel><item><title>No channel title</title></item></channel></rss>",
                "<html><head><title>Website</title></head><body>Not a feed</body></html>",
                "<rss version=\"1.0\"><channel><title>Unsupported</title></channel></rss>",
                "<rss version=\"2.0\"><channel><title>Cut off",
        };
        DownloadError[] expectedReasons = {
                DownloadError.ERROR_PARSER_EXCEPTION,
                DownloadError.ERROR_UNSUPPORTED_TYPE_HTML,
                DownloadError.ERROR_UNSUPPORTED_TYPE,
                DownloadError.ERROR_PARSER_EXCEPTION,
        };
        for (int i = 0; i < brokenDocuments.length; i++) {
            server.publish(FEED_PATH, "application/rss+xml", brokenDocuments[i]);
            FeedRobot.refreshFromMenu();
            List<DownloadResult> log = awaitDownloadLogEntries(feed, i + 1);
            assertEquals(expectedReasons[i], log.get(0).getReason());
            assertFalse(log.get(0).isSuccessful());
            assertTrue(FeedRobot.reload(feed).hasLastUpdateFailed());
        }
        onView(withId(R.id.txtvFailure)).check(matches(isDisplayed()));

        publishLive(feed("Live Feed", EPISODE_A, EPISODE_B));
        FeedRobot.refreshFromMenu();
        Awaitility.await().atMost(FeedRobot.DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> !FeedRobot.reload(feed).hasLastUpdateFailed());
        assertEquals(2, FeedRobot.reload(feed).getItems().size());
        assertTrue(DBReader.getFeedDownloadLog(feed.getId(), 1).get(0).isSuccessful());
    }

    @Test
    public void serverErrorsAreReportedWithTheirReason() throws Exception {
        String url = publishLive(feed("Live Feed", EPISODE_A));
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);
        int[] statuses = {404, 410, 403, 500};
        DownloadError[] expectedReasons = {DownloadError.ERROR_NOT_FOUND, DownloadError.ERROR_NOT_FOUND,
                DownloadError.ERROR_FORBIDDEN, DownloadError.ERROR_HTTP_DATA_ERROR};

        for (int i = 0; i < statuses.length; i++) {
            server.respondWithStatus(FEED_PATH, statuses[i]);
            FeedRobot.refreshFromMenu();
            List<DownloadResult> log = awaitDownloadLogEntries(feed, i + 1);
            assertEquals(expectedReasons[i], log.get(0).getReason());
        }
        assertNotNull(FeedRobot.reload(feed));
        assertNotEquals(0, FeedRobot.reload(feed).getItems().size());
    }

    @Test
    public void pagedFeedLoadsFurtherPagesOnRequest() throws Exception {
        String pageTwoUrl = server.publish("/feeds/page2.xml", "application/atom+xml",
                atomPage(1, "Second page ", null));
        String url = server.publish("/feeds/paged.xml", "application/atom+xml", atomPage(15, "First page ", pageTwoUrl));
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);
        assertTrue(feed.isPaged());
        assertEquals(pageTwoUrl, feed.getNextPageLink());
        assertEquals(15, feed.getItems().size());

        FeedRobot.awaitAssertion(() -> {
            onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.scrollToPosition(14));
            onView(withText(R.string.load_next_page_label)).check(matches(isDisplayed()));
        });
        onView(withText(R.string.load_next_page_label)).perform(click());

        Awaitility.await().atMost(FeedRobot.DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> FeedRobot.reload(feed).getItems().size() == 16);
        assertEquals(1, server.requestsFor("/feeds/page2.xml").size());
        assertNotNull(FeedRobot.itemByTitle(FeedRobot.reload(feed), "Second page 1"));
        assertNull(FeedRobot.reload(feed).getNextPageLink());
    }

    private static String atomPage(int entries, String titlePrefix, String nextUrl) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\"><id>urn:paged</id><title>Paged Atom</title>");
        if (nextUrl != null) {
            xml.append("<link rel=\"next\" href=\"").append(nextUrl).append("\"/>");
        }
        for (int i = 1; i <= entries; i++) {
            xml.append("<entry><id>urn:").append(titlePrefix.trim().replace(' ', '-')).append(i).append("</id><title>")
                    .append(titlePrefix).append(i).append("</title><updated>2023-01-")
                    .append(String.format(Locale.US, "%02d", i)).append("T10:00:00Z</updated>")
                    .append("<link rel=\"enclosure\" type=\"audio/mpeg\" href=\"${BASE}/media/")
                    .append(titlePrefix.trim().replace(' ', '-')).append(i)
                    .append(".mp3\" length=\"20000\"/></entry>");
        }
        return xml.append("</feed>").toString();
    }
}
