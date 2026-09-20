package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment;
import de.danoeh.antennapod.ui.screen.FavoritesFragment;
import de.danoeh.antennapod.ui.screen.InboxFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.service.download.StaticContentServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.hamcrest.Matchers.allOf;

@RunWith(AndroidJUnit4.class)
public class EpisodeStateScreensTest {
    private static final String FEED_PATH = "/feeds/states.xml";
    private static final int PAUSED_POSITION_MS = 60000;
    private static final String DOCUMENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel>"
            + "<title>State Feed</title><link>https://example.com/states</link>"
            + "<item><guid>alpha</guid><title>Alpha</title><pubDate>Wed, 04 Jan 2023 10:00:00 +0000</pubDate>"
            + "<enclosure url=\"${BASE}/media/alpha.mp3\" length=\"20000\" type=\"audio/mpeg\"/></item>"
            + "<item><guid>bravo</guid><title>Bravo</title><pubDate>Tue, 03 Jan 2023 10:00:00 +0000</pubDate>"
            + "<enclosure url=\"${BASE}/media/bravo.mp3\" length=\"20000\" type=\"audio/mpeg\"/></item>"
            + "<item><guid>charlie</guid><title>Charlie</title><pubDate>Mon, 02 Jan 2023 10:00:00 +0000</pubDate>"
            + "</item></channel></rss>";

    private StaticContentServer server;
    private Feed feed;

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
        String url = server.publish(FEED_PATH, "application/rss+xml", DOCUMENT);
        FeedRobot.subscribeByUrl(url);
        feed = FeedRobot.awaitFeed(url);
    }

    @After
    public void tearDown() {
        server.stop();
        PodDBAdapter.deleteDatabase();
    }

    private FeedItem item(String guid) {
        return FeedRobot.itemByGuid(FeedRobot.reload(feed), guid);
    }

    private void applyEveryState(String guid) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(item(guid))).get();
        DBWriter.addFavoriteItems(Collections.singletonList(item(guid))).get();
        FeedMedia media = item(guid).getMedia();
        media.setPosition(PAUSED_POSITION_MS);
        media.setLastPlayedTimeHistory(new Date());
        DBWriter.setFeedMediaPlaybackInformation(media).get();
        DBWriter.addQueueItem(context, item(guid)).get();
        media = item(guid).getMedia();
        media.setDownloaded(true, System.currentTimeMillis());
        media.setLocalFileUrl(new File(context.getFilesDir(), guid + ".mp3").getPath());
        DBWriter.setMediaDownloadInformation(media).get();
    }

    private void openScreen(String tag) {
        EspressoTestUtils.setLaunchScreen(tag);
        activityRule.finishActivity();
        activityRule.launchActivity(new Intent());
    }

    private void assertListed(String title) {
        waitForViewGlobally(allOf(withId(R.id.txtvTitle), withText(title), isDisplayed()), FeedRobot.UI_TIMEOUT_MS);
    }

    private void assertNotListed(String title) {
        FeedRobot.awaitAssertion(() -> onView(allOf(withId(R.id.txtvTitle), withText(title), isDisplayed()))
                .check(doesNotExist()));
    }

    @Test
    public void inboxListsNewEpisodesAndDropsThemOncePlayed() throws Exception {
        DBWriter.markItemsPlayed(FeedItem.NEW, false, Arrays.asList(item("alpha"), item("bravo"))).get();
        DBWriter.markItemsPlayed(FeedItem.UNPLAYED, false, Collections.singletonList(item("charlie"))).get();
        openScreen(InboxFragment.TAG);
        assertListed("Alpha");
        assertListed("Bravo");
        assertNotListed("Charlie");

        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(item("alpha"))).get();

        assertNotListed("Alpha");
        onView(allOf(withId(R.id.txtvTitle), withText("Bravo"))).check(matches(isDisplayed()));
    }

    @Test
    public void inboxPicksUpEpisodesThatBecomeNew() throws Exception {
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Arrays.asList(item("alpha"), item("bravo"),
                item("charlie"))).get();
        openScreen(InboxFragment.TAG);
        waitForViewGlobally(withText(R.string.no_inbox_head_label), FeedRobot.UI_TIMEOUT_MS);

        DBWriter.markItemsPlayed(FeedItem.NEW, false, Collections.singletonList(item("bravo"))).get();

        assertListed("Bravo");
        assertNotListed("Alpha");
    }

    @Test
    public void favoritesListEpisodesWhileTheyAreMarked() throws Exception {
        openScreen(FavoritesFragment.TAG);
        waitForViewGlobally(withText(R.string.no_fav_episodes_head_label), FeedRobot.UI_TIMEOUT_MS);

        DBWriter.addFavoriteItems(Arrays.asList(item("alpha"), item("charlie"))).get();

        assertListed("Alpha");
        assertListed("Charlie");
        assertNotListed("Bravo");

        DBWriter.removeFavoriteItems(Collections.singletonList(item("alpha"))).get();

        assertNotListed("Alpha");
        onView(allOf(withId(R.id.txtvTitle), withText("Charlie"))).check(matches(isDisplayed()));
    }

    @Test
    public void episodesScreenListsEpisodesMatchingEveryChosenFilter() throws Exception {
        applyEveryState("alpha");
        UserPreferences.setPrefFilterAllEpisodes(String.join(",", FeedItemFilter.PLAYED, FeedItemFilter.PAUSED,
                FeedItemFilter.QUEUED, FeedItemFilter.DOWNLOADED, FeedItemFilter.HAS_MEDIA,
                FeedItemFilter.IS_FAVORITE));
        openScreen(AllEpisodesFragment.TAG);
        assertListed("Alpha");
        assertNotListed("Bravo");
        assertNotListed("Charlie");

        DBWriter.setFeedItem(item("alpha"), false).get();
        assertListed("Alpha");
        applyEveryState("bravo");
        DBWriter.setFeedItem(item("bravo"), false).get();

        assertListed("Bravo");
        assertListed("Alpha");
        assertNotListed("Charlie");
    }

    @Test
    public void episodesScreenListsUndownloadedEpisodesWithMediaThatHaveNoState() throws Exception {
        UserPreferences.setPrefFilterAllEpisodes(String.join(",", FeedItemFilter.UNPLAYED,
                FeedItemFilter.NOT_PAUSED, FeedItemFilter.NOT_QUEUED, FeedItemFilter.NOT_DOWNLOADED,
                FeedItemFilter.HAS_MEDIA, FeedItemFilter.NOT_FAVORITE));
        openScreen(AllEpisodesFragment.TAG);
        assertListed("Alpha");
        assertListed("Bravo");
        assertNotListed("Charlie");

        DBWriter.setFeedItem(item("bravo"), false).get();
        assertListed("Bravo");
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(item("bravo"))).get();

        assertNotListed("Bravo");
        assertListed("Alpha");
    }

    @Test
    public void episodesScreenListsEpisodesWithoutMediaThatHaveNoState() throws Exception {
        UserPreferences.setPrefFilterAllEpisodes(String.join(",", FeedItemFilter.UNPLAYED,
                FeedItemFilter.NOT_PAUSED, FeedItemFilter.NOT_QUEUED, FeedItemFilter.NO_MEDIA,
                FeedItemFilter.NOT_FAVORITE));
        openScreen(AllEpisodesFragment.TAG);
        assertListed("Charlie");
        assertNotListed("Alpha");
        assertNotListed("Bravo");

        DBWriter.setFeedItem(item("charlie"), false).get();
        assertListed("Charlie");
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(item("charlie"))).get();

        assertNotListed("Charlie");
    }

    private void assertOnlyListed(String filter, String... titles) throws Exception {
        UserPreferences.setPrefFilterAllEpisodes(filter);
        openScreen(AllEpisodesFragment.TAG);
        for (String title : titles) {
            assertListed(title);
        }
        for (String other : Arrays.asList("Alpha", "Bravo", "Charlie")) {
            if (!Arrays.asList(titles).contains(other)) {
                assertNotListed(other);
            }
        }
        for (String guid : Arrays.asList("alpha", "bravo", "charlie")) {
            DBWriter.setFeedItem(item(guid), false).get();
        }
        for (String title : titles) {
            assertListed(title);
        }
    }

    @Test
    public void everyEpisodeFilterListsExactlyTheMatchingEpisodes() throws Exception {
        applyEveryState("alpha");

        assertOnlyListed(FeedItemFilter.PLAYED, "Alpha");
        assertOnlyListed(FeedItemFilter.UNPLAYED, "Bravo", "Charlie");
        assertOnlyListed(FeedItemFilter.PAUSED, "Alpha");
        assertOnlyListed(FeedItemFilter.NOT_PAUSED, "Bravo", "Charlie");
        assertOnlyListed(FeedItemFilter.QUEUED, "Alpha");
        assertOnlyListed(FeedItemFilter.NOT_QUEUED, "Bravo", "Charlie");
        assertOnlyListed(FeedItemFilter.DOWNLOADED, "Alpha");
        assertOnlyListed(FeedItemFilter.NOT_DOWNLOADED, "Bravo");
        assertOnlyListed(FeedItemFilter.HAS_MEDIA, "Alpha", "Bravo");
        assertOnlyListed(FeedItemFilter.NO_MEDIA, "Charlie");
        assertOnlyListed(FeedItemFilter.IS_FAVORITE, "Alpha");
        assertOnlyListed(FeedItemFilter.NOT_FAVORITE, "Bravo", "Charlie");
    }
}
