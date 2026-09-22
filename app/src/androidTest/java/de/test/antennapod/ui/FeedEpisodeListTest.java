package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
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

import java.io.File;
import java.util.Collections;
import java.util.Date;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.ui.FeedRobot.awaitAssertion;
import static de.test.antennapod.ui.FeedRobot.waitUntilDisplayed;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class FeedEpisodeListTest {
    private static final String FEED_PATH = "/feeds/list.xml";
    private static final long PAUSED_POSITION_MS = 60000;
    private static final String DONATE_URL = "https://example.com/donate";
    private static final String OTHER_URL = "https://example.com/other";

    private static final String DOCUMENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\""
            + " xmlns:podcast=\"https://podcastindex.org/namespace/1.0\"><channel>"
            + "<title>List Feed</title><link>https://example.com/list</link>"
            + "<description>Everything about the list</description>"
            + "<podcast:funding url=\"" + DONATE_URL + "\">Support the show</podcast:funding>"
            + "<podcast:funding url=\"" + DONATE_URL + "\">Support</podcast:funding>"
            + "<podcast:funding url=\"" + OTHER_URL + "\"></podcast:funding>"
            + episode("alpha", "Alpha", "Fri, 06 Jan 2023 10:00:00 +0000", "00:10:00", 3000, "Opening episode")
            + episode("bravo", "Bravo", "Wed, 04 Jan 2023 10:00:00 +0000", "00:30:00", 1000,
                    "Talk about quantum entanglement")
            + episode("charlie", "Charlie", "Thu, 05 Jan 2023 10:00:00 +0000", "00:20:00", 2000, "Middle episode")
            + "<item><guid>delta</guid><title>Delta</title><pubDate>Tue, 03 Jan 2023 10:00:00 +0000</pubDate>"
            + "<description>Text only post</description></item>"
            + episode("echo", "Echo", "Mon, 02 Jan 2023 10:00:00 +0000", "01:00:00", 5000, "Closing episode")
            + "</channel></rss>";

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

    private static String episode(String guid, String title, String pubDate, String duration, int size,
                                  String description) {
        return "<item><guid>" + guid + "</guid><title>" + title + "</title><pubDate>" + pubDate + "</pubDate>"
                + "<itunes:duration>" + duration + "</itunes:duration><description>" + description
                + "</description><enclosure url=\"${BASE}/media/" + guid + ".mp3\" length=\"" + size
                + "\" type=\"audio/mpeg\"/></item>";
    }

    private FeedItem item(String guid) {
        return FeedRobot.itemByGuid(FeedRobot.reload(feed), guid);
    }

    private void markStates() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(item("alpha"))).get();
        FeedItem bravo = item("bravo");
        DBWriter.addFavoriteItems(Collections.singletonList(bravo)).get();
        bravo.getMedia().setPosition((int) PAUSED_POSITION_MS);
        bravo.getMedia().setLastPlayedTimeHistory(new Date());
        DBWriter.setFeedMediaPlaybackInformation(bravo.getMedia()).get();
        DBWriter.addQueueItem(context, item("charlie")).get();
        FeedItem echo = item("echo");
        echo.getMedia().setDownloaded(true, System.currentTimeMillis());
        echo.getMedia().setLocalFileUrl(new File(context.getFilesDir(), "echo.mp3").getPath());
        DBWriter.setMediaDownloadInformation(echo.getMedia()).get();
    }

    private void chooseSort(int labelRes) {
        String label = InstrumentationRegistry.getInstrumentation().getTargetContext().getString(labelRes);
        FeedRobot.openFeedMenu(R.string.sort);
        onView(withText(startsWith(label))).inRoot(isDialog()).perform(click());
        Espresso.pressBack();
    }

    private void chooseFilters(int... labels) {
        onView(withId(R.id.butFilter)).perform(click());
        for (int label : labels) {
            FeedRobot.chooseOption(label);
        }
        onView(withId(R.id.confirmFiltermenu)).inRoot(isDialog()).perform(click());
    }

    private void resetFilter() {
        onView(withId(R.id.butFilter)).perform(click());
        onView(withId(R.id.resetFiltermenu)).inRoot(isDialog()).perform(click());
        onView(withId(R.id.confirmFiltermenu)).inRoot(isDialog()).perform(click());
    }

    private void assertFiltered(int label, String... titles) {
        chooseFilters(label);
        FeedRobot.assertListedTitles(titles);
        resetFilter();
        FeedRobot.assertListedTitles("Alpha", "Charlie", "Bravo", "Delta", "Echo");
    }

    @Test
    public void episodesAreListedNewestFirstByDefault() {
        FeedRobot.assertListedTitles("Alpha", "Charlie", "Bravo", "Delta", "Echo");
        assertNull(FeedRobot.reload(feed).getSortOrder());
    }

    @Test
    public void episodesCanBeSortedByTitleInBothDirections() {
        chooseSort(R.string.episode_title);

        FeedRobot.assertListedTitles("Alpha", "Bravo", "Charlie", "Delta", "Echo");
        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getSortOrder() == SortOrder.EPISODE_TITLE_A_Z);

        chooseSort(R.string.episode_title);

        FeedRobot.assertListedTitles("Echo", "Delta", "Charlie", "Bravo", "Alpha");
        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getSortOrder() == SortOrder.EPISODE_TITLE_Z_A);
    }

    @Test
    public void episodesCanBeSortedByDuration() {
        chooseSort(R.string.duration);

        FeedRobot.assertListedTitles("Delta", "Alpha", "Charlie", "Bravo", "Echo");
        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getSortOrder() == SortOrder.DURATION_SHORT_LONG);

        chooseSort(R.string.duration);

        FeedRobot.assertListedTitles("Echo", "Bravo", "Charlie", "Alpha", "Delta");
    }

    @Test
    public void episodesCanBeSortedOldestFirstAndBackToTheGlobalDefault() {
        chooseSort(R.string.date);
        FeedRobot.assertListedTitles("Alpha", "Charlie", "Bravo", "Delta", "Echo");
        chooseSort(R.string.date);

        FeedRobot.assertListedTitles("Echo", "Delta", "Bravo", "Charlie", "Alpha");
        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getSortOrder() == SortOrder.DATE_OLD_NEW);

        chooseSort(R.string.global_default);

        FeedRobot.assertListedTitles("Alpha", "Charlie", "Bravo", "Delta", "Echo");
        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getSortOrder() == SortOrder.GLOBAL_DEFAULT);
    }

    @Test
    public void playedFilterKeepsOnlyPlayedEpisodes() throws Exception {
        markStates();
        assertFiltered(R.string.hide_played_episodes_label, "Alpha");
    }

    @Test
    public void unplayedFilterHidesPlayedEpisodes() throws Exception {
        markStates();
        assertFiltered(R.string.not_played, "Charlie", "Bravo", "Delta", "Echo");
    }

    @Test
    public void pausedFilterKeepsEpisodesWithAPlaybackPosition() throws Exception {
        markStates();
        assertFiltered(R.string.hide_paused_episodes_label, "Bravo");
        assertFiltered(R.string.not_paused, "Alpha", "Charlie", "Delta", "Echo");
    }

    @Test
    public void favoriteFilterSeparatesFavoritesFromTheRest() throws Exception {
        markStates();
        assertFiltered(R.string.hide_is_favorite_label, "Bravo");
        assertFiltered(R.string.not_favorite, "Alpha", "Charlie", "Delta", "Echo");
    }

    @Test
    public void mediaFilterSeparatesEpisodesWithAndWithoutAudio() {
        assertFiltered(R.string.no_media, "Delta");
        assertFiltered(R.string.has_media, "Alpha", "Charlie", "Bravo", "Echo");
    }

    @Test
    public void queueFilterSeparatesQueuedEpisodesFromTheRest() throws Exception {
        markStates();
        assertFiltered(R.string.queued_label, "Charlie");
        assertFiltered(R.string.not_queued_label, "Alpha", "Bravo", "Delta", "Echo");
    }

    @Test
    public void downloadFilterSeparatesDownloadedEpisodesFromTheRest() throws Exception {
        markStates();
        assertFiltered(R.string.hide_downloaded_episodes_label, "Echo");
        assertFiltered(R.string.hide_not_downloaded_episodes_label, "Alpha", "Charlie", "Bravo");
    }

    @Test
    public void filtersFromDifferentGroupsCombineAndAreStoredWithTheFeed() throws Exception {
        markStates();

        chooseFilters(R.string.not_played, R.string.has_media);

        FeedRobot.assertListedTitles("Charlie", "Bravo", "Echo");
        onView(withId(R.id.txtvInformation)).check(matches(withText(R.string.filtered_label)));
        FeedItemFilter stored = FeedRobot.reload(feed).getItemFilter();
        assertEquals(2, stored.getValues().length);

        onView(withId(R.id.butFilter)).perform(click());
        onView(withId(R.id.resetFiltermenu)).inRoot(isDialog()).perform(click());
        onView(withId(R.id.confirmFiltermenu)).inRoot(isDialog()).perform(click());

        FeedRobot.assertListedTitles("Alpha", "Charlie", "Bravo", "Delta", "Echo");
        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getItemFilter().getValues().length == 0);
    }

    @Test
    public void searchFindsEpisodesOfTheFeedByTitleAndDescription() {
        onView(withId(R.id.action_search)).perform(click());
        waitUntilDisplayed(withId(R.id.search_src_text), FeedRobot.UI_TIMEOUT_MS);

        onView(withId(R.id.search_src_text)).perform(replaceText("quantum"));

        waitUntilDisplayed(allOf(withId(R.id.txtvTitle), withText("Bravo"), isDisplayed()), FeedRobot.UI_TIMEOUT_MS);
        onView(allOf(withId(R.id.recyclerView), isDisplayed())).check(matches(FeedRobot.hasItemCount(1)));

        onView(withId(R.id.search_src_text)).perform(replaceText("Alpha"));

        waitUntilDisplayed(allOf(withId(R.id.txtvTitle), withText("Alpha"), isDisplayed()), FeedRobot.UI_TIMEOUT_MS);
        onView(allOf(withId(R.id.recyclerView), isDisplayed())).check(matches(FeedRobot.hasItemCount(1)));
    }

    @Test
    public void searchWithoutMatchReportsNoResults() {
        onView(withId(R.id.action_search)).perform(click());
        waitUntilDisplayed(withId(R.id.search_src_text), FeedRobot.UI_TIMEOUT_MS);

        onView(withId(R.id.search_src_text)).perform(replaceText("zzzzzz"));

        String expected = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getString(R.string.no_results_for_query, "zzzzzz");
        waitUntilDisplayed(allOf(withId(R.id.emptyViewTitle), withText(expected)), FeedRobot.UI_TIMEOUT_MS);
    }

    @Test
    public void feedInfoShowsDescriptionAddressAndDeduplicatedFundingLinks() {
        onView(withId(R.id.butShowInfo)).perform(click());

        awaitAssertion(() -> onView(withId(R.id.descriptionLabel)).perform(scrollTo()));
        onView(withId(R.id.descriptionLabel)).check(matches(withText("Everything about the list")));
        onView(withId(R.id.urlLabel)).perform(scrollTo())
                .check(matches(withText(server.getBaseUrl() + FEED_PATH)));
        String support = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getString(R.string.support_podcast);
        onView(withId(R.id.supportUrl)).perform(scrollTo()).check(matches(withText(
                "Support the show " + DONATE_URL + "\n" + support + " " + OTHER_URL)));
    }

    @Test
    public void archivingKeepsTheFeedAndItsEpisodes() {
        FeedRobot.openFeedMenu(R.string.remove_archive_feed_label);
        FeedRobot.awaitDialogText(R.string.archive_feed_label_verb);
        onView(withId(R.id.archiveButton)).inRoot(isDialog()).perform(click());

        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getState() == Feed.STATE_ARCHIVED);
        assertEquals(5, FeedRobot.reload(feed).getItems().size());
    }

    @Test
    public void unsubscribingDeletesTheFeedWithItsEpisodes() {
        FeedRobot.openFeedMenu(R.string.remove_archive_feed_label);
        onView(withId(R.id.removeButton)).inRoot(isDialog()).perform(click());
        onView(withId(R.id.removeConfirmButton)).inRoot(isDialog()).perform(click());

        FeedRobot.awaitCondition(() -> DBReader.getFeedList().isEmpty());
        assertEquals(0, DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE).size());
    }

    @Test
    public void cancellingTheRemovalKeepsTheFeed() {
        FeedRobot.openFeedMenu(R.string.remove_archive_feed_label);
        onView(withId(R.id.cancelButton)).inRoot(isDialog()).perform(click());

        waitUntilDisplayed(allOf(withId(R.id.txtvTitle), withText("List Feed"), isDisplayed()),
                FeedRobot.UI_TIMEOUT_MS);
        assertEquals(1, DBReader.getFeedList().size());
    }
}
