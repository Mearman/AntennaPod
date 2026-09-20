package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.screen.FavoritesFragment;
import de.danoeh.antennapod.ui.screen.InboxFragment;
import de.danoeh.antennapod.ui.screen.PlaybackHistoryFragment;
import de.danoeh.antennapod.ui.screen.download.CompletedDownloadsFragment;
import de.danoeh.antennapod.ui.screen.queue.QueueFragment;
import de.danoeh.antennapod.ui.statistics.StatisticsFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.service.download.DownloadTestFixture;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.waitForView;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static de.test.antennapod.NthMatcher.first;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Uses the queue, history, downloads, favourites, inbox and statistics screens.
 */
@RunWith(AndroidJUnit4.class)
public class EpisodeScreensTest {
    private static final long TIMEOUT_SECONDS = 30;
    private static final long VIEW_TIMEOUT_MILLIS = 10000;

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Context context;
    private Feed feed;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        feed = fixture.subscribe("Screens", 3);
    }

    @After
    public void tearDown() throws Exception {
        activityRule.finishActivity();
        fixture.tearDown();
    }

    private String title(int index) {
        return feed.getItemAtIndex(index).getTitle();
    }

    private FeedItem item(int index) {
        return DBReader.getFeedItem(feed.getItemAtIndex(index).getId());
    }

    private void open(String fragmentTag) {
        EspressoTestUtils.setLaunchScreen(fragmentTag);
        activityRule.launchActivity(new Intent());
    }

    private void onEpisode(int index, ViewAction action) {
        onView(allOf(withId(R.id.recyclerView), isDisplayed())).perform(
                RecyclerViewActions.actionOnItem(hasDescendant(withText(title(index))), action));
    }

    private void chooseFromLongPressMenu(int index, int menuLabel) {
        onEpisode(index, longClick());
        onView(allOf(withText(menuLabel), isDisplayed())).perform(click());
    }

    private void confirmDialog() {
        onView(allOf(withText(R.string.confirm_label), isDisplayed())).perform(click());
    }

    @Test
    public void queueScreenListsQueuedEpisodesAndCanBeCleared() throws Exception {
        DBWriter.addQueueItem(context, item(0), item(1), item(2)).get();
        open(QueueFragment.TAG);
        waitForViewGlobally(withText(title(0)), VIEW_TIMEOUT_MILLIS);

        onView(first(EspressoTestUtils.actionBarOverflow())).perform(click());
        onView(allOf(withText(R.string.clear_queue_label), isDisplayed())).perform(click());
        confirmDialog();

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> DBReader.getQueue().isEmpty());
    }

    @Test
    public void queuedEpisodesCanBeMovedAndRemovedFromTheMenu() throws Exception {
        DBWriter.addQueueItem(context, item(0), item(1), item(2)).get();
        open(QueueFragment.TAG);
        waitForViewGlobally(withText(title(2)), VIEW_TIMEOUT_MILLIS);

        chooseFromLongPressMenu(2, R.string.move_to_top_label);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> DBReader.getQueue().get(0).getId() == item(2).getId());

        chooseFromLongPressMenu(2, R.string.move_to_bottom_label);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> DBReader.getQueue().get(2).getId() == item(2).getId());

        chooseFromLongPressMenu(1, R.string.remove_from_queue_label);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> DBReader.getQueue().size() == 2);
        assertFalse(DBReader.getQueue().contains(item(1)));
    }

    @Test
    public void historyScreenListsPlayedEpisodesAndCanBeCleared() throws Exception {
        long now = System.currentTimeMillis();
        DBWriter.addItemToPlaybackHistory(item(0).getMedia(), new Date(now - 1000)).get();
        DBWriter.addItemToPlaybackHistory(item(1).getMedia(), new Date(now)).get();
        open(PlaybackHistoryFragment.TAG);
        waitForViewGlobally(withText(title(1)), VIEW_TIMEOUT_MILLIS);
        waitForViewGlobally(withText(title(0)), VIEW_TIMEOUT_MILLIS);

        onView(allOf(withContentDescription(R.string.clear_history_label), isDisplayed())).perform(click());
        confirmDialog();

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY)) == 0);
    }

    @Test
    public void downloadsScreenListsDownloadedEpisodesAndDeletesThem() throws Exception {
        fixture.markDownloaded(feed.getItemAtIndex(1));
        File downloaded = new File(DBReader.getFeedMedia(feed.getItemAtIndex(1).getMedia().getId())
                .getLocalFileUrl());
        open(CompletedDownloadsFragment.TAG);
        waitForViewGlobally(withText(title(1)), VIEW_TIMEOUT_MILLIS);

        chooseFromLongPressMenu(1, R.string.delete_label);

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> !item(1).isDownloaded());
        assertFalse(downloaded.exists());
    }

    @Test
    public void favouritesScreenListsFavouritesAndRemovesThemFromTheMenu() throws Exception {
        DBWriter.addFavoriteItems(Arrays.asList(item(0), item(2))).get();
        open(FavoritesFragment.TAG);
        waitForViewGlobally(withText(title(2)), VIEW_TIMEOUT_MILLIS);

        chooseFromLongPressMenu(2, R.string.remove_from_favorite_label);

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> !item(2).isTagged(FeedItem.TAG_FAVORITE));
        assertTrue(item(0).isTagged(FeedItem.TAG_FAVORITE));
    }

    @Test
    public void inboxScreenListsNewEpisodesAndRemovesThemFromTheInbox() throws Exception {
        DBWriter.markItemsPlayed(FeedItem.NEW, false, Collections.singletonList(item(1))).get();
        open(InboxFragment.TAG);
        waitForViewGlobally(withText(title(1)), VIEW_TIMEOUT_MILLIS);

        chooseFromLongPressMenu(1, R.string.remove_inbox_label);

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> !item(1).isNew());
        assertEquals(0, DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.NEW)));
    }

    private void recordPlayedTime() throws Exception {
        FeedMedia played = DBReader.getFeedMedia(feed.getItemAtIndex(0).getMedia().getId());
        played.setPlayedDuration(120_000);
        played.setLastPlayedTimeStatistics(System.currentTimeMillis());
        played.setLastPlayedTimeHistory(new Date());
        DBWriter.setFeedMediaPlaybackInformation(played).get();
    }

    @Test
    public void statisticsScreenListsSubscriptionsAndOpensTheirStatistics() throws Exception {
        recordPlayedTime();
        open(StatisticsFragment.TAG);
        onView(isRoot()).perform(waitForView(withText(feed.getTitle()), VIEW_TIMEOUT_MILLIS));

        onView(allOf(withText(R.string.years_statistics_label), isDescendantOfA(withId(R.id.sliding_tabs)))).perform(click());
        onView(allOf(withText(R.string.subscriptions_label), isDescendantOfA(withId(R.id.sliding_tabs)))).perform(click());
        onView(first(allOf(withText(feed.getTitle()), isDisplayed()))).perform(click());

        waitForViewGlobally(withText(R.string.statistics_episodes_space), VIEW_TIMEOUT_MILLIS);
    }

    @Test
    public void statisticsCanBeReset() throws Exception {
        recordPlayedTime();
        open(StatisticsFragment.TAG);
        onView(isRoot()).perform(waitForView(withText(feed.getTitle()), VIEW_TIMEOUT_MILLIS));

        onView(first(EspressoTestUtils.actionBarOverflow())).perform(click());
        onView(allOf(withText(R.string.statistics_reset_data), isDisplayed())).perform(click());
        confirmDialog();

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> DBReader.getFeedMedia(feed.getItemAtIndex(0).getMedia().getId()).getPlayedDuration() == 0);
    }
}
