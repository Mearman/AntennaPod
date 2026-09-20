package de.test.antennapod.ui;

import android.content.Intent;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.danoeh.antennapod.ui.screen.FavoritesFragment;
import de.danoeh.antennapod.ui.screen.InboxFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.service.download.StaticContentServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

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
    private static final String DOCUMENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel>"
            + "<title>State Feed</title><link>https://example.com/states</link>"
            + "<item><guid>alpha</guid><title>Alpha</title><pubDate>Wed, 04 Jan 2023 10:00:00 +0000</pubDate></item>"
            + "<item><guid>bravo</guid><title>Bravo</title><pubDate>Tue, 03 Jan 2023 10:00:00 +0000</pubDate></item>"
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
}
