package de.test.antennapod.playback;

import android.content.Context;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.ui.UITestUtils;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Plays episodes through the Media3 playback service and verifies the effects on the playback preferences and the database.
 */
@LargeTest
public class Media3PlaybackTest {
    private static final long TIMEOUT_SECONDS = 30;

    private Context context;
    private UITestUtils uiTestUtils;
    private MediaController controller;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        uiTestUtils = new UITestUtils(context);
        uiTestUtils.setup();
        uiTestUtils.addLocalFeedData(true);
        controller = Media3TestUtils.connectController(context);
    }

    @After
    public void tearDown() throws Exception {
        Media3TestUtils.stopPlaybackService(context, controller);
        controller = null;
        uiTestUtils.tearDown();
    }

    @Test
    public void testPlayFromQueueWritesPlaybackPreferences() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);

        awaitCurrentMedia(media);
        awaitPlaying();
        assertEquals(PlaybackPreferences.PLAYER_STATUS_PLAYING, PlaybackPreferences.getCurrentPlayerStatus());
        assertEquals(FeedMedia.PLAYABLE_TYPE_FEEDMEDIA, PlaybackPreferences.getCurrentlyPlayingMediaType());
        assertFalse(PlaybackPreferences.getCurrentEpisodeIsVideo());
        assertTrue(PlaybackService.isRunning);
    }

    @Test
    public void testPauseStopsPlaybackAndSavesPosition() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        awaitPositionAtLeast(500);
        Media3TestUtils.runOnMain(controller::pause);

        Awaitility.await("playback paused")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentPlayerStatus()
                        == PlaybackPreferences.PLAYER_STATUS_PAUSED);
        assertFalse(PlaybackService.isRunning);
        Awaitility.await("position stored in database")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> DBReader.getFeedMedia(media.getId()).getPosition() > 0);
        assertNotNull(DBReader.getFeedMedia(media.getId()).getLastPlayedTimeHistory());
    }

    @Test
    public void testResumeContinuesFromStoredPosition() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        awaitPositionAtLeast(700);
        Media3TestUtils.runOnMain(controller::pause);
        Awaitility.await("paused")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> !PlaybackService.isRunning);
        long pausedPosition = Media3TestUtils.getOnMain(controller::getCurrentPosition);

        Media3TestUtils.runOnMain(controller::play);
        awaitPlaying();

        assertTrue("Resume must not restart from the beginning",
                Media3TestUtils.getOnMain(controller::getCurrentPosition) > pausedPosition / 2);
        assertEquals(media.getId(), PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
    }

    @Test
    public void testSeekToUpdatesPositionAndDatabase() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        awaitDurationKnown();
        Media3TestUtils.runOnMain(() -> {
            controller.pause();
            controller.seekTo(2000);
        });

        Awaitility.await("seek applied")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller::getCurrentPosition) >= 1900);
        Awaitility.await("seek stored in database")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> DBReader.getFeedMedia(media.getId()).getPosition() >= 1900);
    }

    @Test
    public void testSeekForwardAndBackUseConfiguredIntervals() {
        UserPreferences.setFastForwardSecs(1);
        UserPreferences.setRewindSecs(1);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        awaitDurationKnown();
        Media3TestUtils.runOnMain(() -> {
            controller.pause();
            controller.seekTo(0);
            controller.seekForward();
        });

        Awaitility.await("seek forward applied")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller::getCurrentPosition) >= 1000);

        Media3TestUtils.runOnMain(controller::seekBack);
        Awaitility.await("seek back applied")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller::getCurrentPosition) < 500);
    }

    @Test
    public void testSeekBackDoesNotGoBeforeStart() {
        UserPreferences.setRewindSecs(60);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        awaitDurationKnown();
        Media3TestUtils.runOnMain(() -> {
            controller.pause();
            controller.seekTo(1000);
            controller.seekBack();
        });

        Awaitility.await("rewound to start")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller::getCurrentPosition) == 0);
    }

    @Test
    public void testSetPlaybackSpeedIsStoredAsTemporarySpeed() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        Media3TestUtils.runOnMain(() -> controller.setPlaybackSpeed(1.5f));

        Awaitility.await("speed applied")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentlyPlayingTemporaryPlaybackSpeed() == 1.5f);
        assertEquals(1.5f, Media3TestUtils.getOnMain(() ->
                controller.getPlaybackParameters().speed), 0.01f);
    }

    @Test
    public void testContinuousPlaybackStartsNextQueueItem() {
        UserPreferences.setFollowQueue(true);
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia first = queue.get(0).getMedia();
        FeedMedia second = queue.get(1).getMedia();

        play(first);
        awaitCurrentMedia(first);
        awaitCurrentMedia(second);

        assertTrue("Finished episode is marked as played",
                DBReader.getFeedItem(first.getItem().getId()).isPlayed());
        assertFalse("Finished episode is removed from the queue",
                DBReader.getQueueIDList().contains(first.getItem().getId()));
    }

    @Test
    public void testContinuousPlaybackDisabledLoadsNextButDoesNotPlay() {
        UserPreferences.setFollowQueue(false);
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia first = queue.get(0).getMedia();
        FeedMedia second = queue.get(1).getMedia();

        play(first);
        awaitCurrentMedia(first);
        awaitCurrentMedia(second);

        Awaitility.await("playback stopped after the episode ended")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> !PlaybackService.isRunning);
        assertEquals(PlaybackPreferences.PLAYER_STATUS_PAUSED, PlaybackPreferences.getCurrentPlayerStatus());
    }

    @Test
    public void testPlaybackOfLastQueueItemClearsCurrentlyPlaying() throws Exception {
        List<FeedItem> queue = DBReader.getQueue();
        for (int i = 1; i < queue.size(); i++) {
            DBWriter.removeQueueItem(context, false, queue.get(i)).get();
        }
        FeedMedia media = queue.get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);

        Awaitility.await("playback preferences cleared")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
                        == PlaybackPreferences.NO_MEDIA_PLAYING);
        assertTrue(DBReader.getFeedItem(media.getItem().getId()).isPlayed());
    }

    @Test
    public void testSeekToNextMediaItemSkipsToNextEpisode() {
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia first = queue.get(0).getMedia();
        FeedMedia second = queue.get(1).getMedia();

        play(first);
        awaitCurrentMedia(first);
        awaitPositionAtLeast(300);
        Media3TestUtils.runOnMain(controller::seekToNextMediaItem);

        awaitCurrentMedia(second);
        assertNotEquals(first.getId(), PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
    }

    @Test
    public void testPlayingAnEpisodeAddsItToTheQueue() throws Exception {
        DBWriter.clearQueue().get();
        List<FeedItem> episodes = DBReader.getEpisodes(0, 5,
                FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD);
        FeedMedia media = episodes.get(0).getMedia();
        assertEquals(0, DBReader.getQueueIDList().size());

        play(media);
        awaitCurrentMedia(media);

        Awaitility.await("episode added to the queue")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> DBReader.getQueueIDList().contains(media.getItem().getId()));
    }

    @Test
    public void testUnknownMediaIdDoesNotStartPlayback() {
        Media3TestUtils.runOnMain(() -> {
            controller.setMediaItem(MediaItemAdapter.fromMediaIdStub(123456789L));
            controller.prepare();
            controller.play();
        });

        Awaitility.await("media items rejected")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller::getMediaItemCount) == 0);
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING,
                PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
    }

    private void play(FeedMedia media) {
        Media3TestUtils.runOnMain(() -> {
            controller.setMediaItem(MediaItemAdapter.fromMediaIdStub(media.getId()));
            controller.prepare();
            controller.play();
        });
    }

    private void awaitCurrentMedia(FeedMedia media) {
        Awaitility.await("media " + media.getId() + " is current")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == media.getId());
    }

    private void awaitPlaying() {
        Awaitility.await("playback started")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller::isPlaying));
    }

    private void awaitDurationKnown() {
        Awaitility.await("duration known")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller::getPlaybackState) == Player.STATE_READY);
    }

    private void awaitPositionAtLeast(long positionMs) {
        Awaitility.await("position " + positionMs + " reached")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller::getCurrentPosition) >= positionMs);
    }
}
