package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;
import de.test.antennapod.EspressoTestUtils;
import org.awaitility.Awaitility;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount;
import static org.hamcrest.Matchers.allOf;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static de.test.antennapod.EspressoTestUtils.clickBottomNavOverflow;
import static de.test.antennapod.EspressoTestUtils.clickChildViewWithId;
import static de.test.antennapod.EspressoTestUtils.waitForView;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class PlaybackStatePersistenceTest {
    private UITestUtils uiTestUtils;
    private Context context;

    @Rule
    public ActivityTestRule<MainActivity> activityTestRule =
            new ActivityTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        uiTestUtils = new UITestUtils(context);
        uiTestUtils.setup();
    }

    @After
    public void tearDown() throws Exception {
        EspressoTestUtils.tryKillPlaybackService();
        activityTestRule.finishActivity();
        uiTestUtils.tearDown();
    }

    private void startPlaybackOfFirstEpisode() throws Exception {
        uiTestUtils.addLocalFeedData(true);
        activityTestRule.launchActivity(new Intent());
        DBWriter.clearQueue().get();

        clickBottomNavOverflow(R.string.episodes_label);
        List<FeedItem> episodes = DBReader.getEpisodes(0, 10,
                FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD);
        Matcher<View> episodesMatcher = allOf(withId(R.id.recyclerView),
                isDisplayed(), hasMinimumChildCount(2));
        onView(isRoot()).perform(waitForView(episodesMatcher, 10000));
        onView(episodesMatcher).perform(actionOnItemAtPosition(0,
                clickChildViewWithId(R.id.secondaryActionButton)));

        FeedItem item = episodes.get(0);
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() ->
                item.getMedia().getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
    }

    @Test
    public void testPlayingEpisodeWritesPlaybackState() throws Exception {
        startPlaybackOfFirstEpisode();
        List<FeedItem> episodes = DBReader.getEpisodes(0, 10,
                FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD);
        assertEquals(FeedMedia.PLAYABLE_TYPE_FEEDMEDIA, PlaybackPreferences.getCurrentlyPlayingMediaType());
        assertEquals(episodes.get(0).getMedia().getId(),
                PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertTrue(PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING
                || PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PAUSED);
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() ->
                PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING);
        assertEquals(false, PlaybackPreferences.getCurrentEpisodeIsVideo());

        context.sendBroadcast(MediaButtonStarter.createIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() ->
                PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PAUSED);
    }

    @Test
    public void testStoppingPlaybackClearsPlayingMedia() throws Exception {
        startPlaybackOfFirstEpisode();
        context.sendBroadcast(MediaButtonStarter.createIntent(context, KeyEvent.KEYCODE_MEDIA_STOP));
        Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() ->
                PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
                        == PlaybackPreferences.NO_MEDIA_PLAYING);
        assertEquals(PlaybackPreferences.PLAYER_STATUS_OTHER,
                PlaybackPreferences.getCurrentPlayerStatus());
    }
}
