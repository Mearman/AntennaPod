package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.service.download.DownloadTestFixture;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickChildViewWithId;
import static de.test.antennapod.EspressoTestUtils.performWhenReady;
import static de.test.antennapod.EspressoTestUtils.waitForView;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class EpisodeListActionsTest {
    private static final long TIMEOUT_SECONDS = 60;
    private static final long VIEW_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Context context;
    private Feed feed;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        UserPreferences.setAllowMobileEpisodeDownload(true);
        feed = fixture.subscribe("Listed", 3);
    }

    @After
    public void tearDown() throws Exception {
        activityRule.finishActivity();
        DownloadServiceInterface.get().cancelAll(context);
        fixture.server().releaseStalled();
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> DownloadServiceInterface.get().getNumberOfActiveDownloads(context) == 0);
        WorkManager.getInstance(context).pruneWork().getResult().get();
        fixture.tearDown();
    }

    private void openFeed() {
        EspressoTestUtils.setLaunchScreen("" + feed.getId());
        activityRule.launchActivity(new Intent());
        waitForViewGlobally(withText(title(0)), VIEW_TIMEOUT_MILLIS);
    }

    private String title(int index) {
        return feed.getItemAtIndex(index).getTitle();
    }

    private FeedItem item(int index) {
        return DBReader.getFeedItem(feed.getItemAtIndex(index).getId());
    }

    private FeedMedia media(int index) {
        return DBReader.getFeedMedia(feed.getItemAtIndex(index).getMedia().getId());
    }

    private void onEpisode(int index, ViewAction action) {
        onView(allOf(withId(R.id.recyclerView), isDisplayed())).perform(
                RecyclerViewActions.actionOnItem(hasDescendant(withText(title(index))), action));
    }

    private void clickSecondaryAction(int index) {
        onEpisode(index, clickChildViewWithId(R.id.secondaryActionButton));
    }

    private void chooseFromLongPressMenu(int index, int menuLabel) {
        onEpisode(index, longClick());
        performWhenReady(allOf(withText(menuLabel), isDisplayed()), click(), VIEW_TIMEOUT_MILLIS);
    }

    private void awaitSecondaryAction(int label) {
        onView(isRoot()).perform(waitForView(allOf(withContentDescription(label), isDisplayed()),
                VIEW_TIMEOUT_MILLIS));
    }

    private void pointMediaAtStall(int index) throws Exception {
        FeedMedia stored = media(index);
        String url = fixture.url("/stall/" + fixture.idOf(stored.getDownloadUrl()));
        DBWriter.setFeedMedia(new FeedMedia(stored.getId(), stored.getItem(), 0, 0, stored.getSize(),
                stored.getMimeType(), null, url, 0, null, 0, 0)).get();
    }

    @Test
    public void downloadButtonDownloadsTheEpisodeAndOffersPlayback() throws Exception {
        openFeed();
        awaitSecondaryAction(R.string.download_label);

        clickSecondaryAction(0);

        DownloadTestFixture.awaitDownloaded(media(0));
        assertTrue(new File(media(0).getLocalFileUrl()).exists());
        assertFalse(media(1).isDownloaded());

        DBWriter.addQueueItem(context, feed.getItemAtIndex(1)).get();
        awaitSecondaryAction(R.string.play_label);
    }

    @Test
    public void cancelButtonStopsARunningDownload() throws Exception {
        pointMediaAtStall(1);
        openFeed();

        clickSecondaryAction(1);
        assertTrue(fixture.server().awaitStalled(1, TIMEOUT_SECONDS, TimeUnit.SECONDS));
        awaitSecondaryAction(R.string.cancel_download_label);
        clickSecondaryAction(1);

        awaitSecondaryAction(R.string.download_label);
        assertFalse(media(1).isDownloaded());
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> DownloadServiceInterface.get().getNumberOfActiveDownloads(context) == 0);
    }

    @Test
    public void episodeCanBeQueuedAndUnqueuedFromTheMenu() throws Exception {
        openFeed();

        chooseFromLongPressMenu(2, R.string.add_to_queue_label);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> DBReader.getQueue().size() == 1);
        assertEquals(item(2).getId(), DBReader.getQueue().get(0).getId());

        chooseFromLongPressMenu(2, R.string.remove_from_queue_label);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> DBReader.getQueue().isEmpty());
    }

    @Test
    public void episodeCanBeMarkedPlayedAndUnplayedFromTheMenu() throws Exception {
        openFeed();

        chooseFromLongPressMenu(0, R.string.mark_as_played_label);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> item(0).isPlayed());

        chooseFromLongPressMenu(0, R.string.mark_as_unplayed_label);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> !item(0).isPlayed());
    }

    @Test
    public void episodeCanBeFavouritedAndUnfavouritedFromTheMenu() throws Exception {
        openFeed();

        chooseFromLongPressMenu(1, R.string.add_to_favorite_label);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> item(1).isTagged(FeedItem.TAG_FAVORITE));

        chooseFromLongPressMenu(1, R.string.remove_from_favorite_label);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> !item(1).isTagged(FeedItem.TAG_FAVORITE));
    }

    @Test
    public void downloadedEpisodeCanBeDeletedFromTheMenu() throws Exception {
        fixture.markDownloaded(feed.getItemAtIndex(0));
        File downloaded = new File(media(0).getLocalFileUrl());
        openFeed();

        chooseFromLongPressMenu(0, R.string.delete_label);

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> !item(0).isDownloaded());
        assertFalse(downloaded.exists());
    }
}
