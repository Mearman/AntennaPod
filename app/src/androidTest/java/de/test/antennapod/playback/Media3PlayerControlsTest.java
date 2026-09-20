package de.test.antennapod.playback;

import androidx.test.filters.LargeTest;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.playback.base.RewindAfterPauseUtils;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.awaitility.Awaitility;
import org.junit.Test;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the player controls of the Media3 playback service, using an episode that is long enough to seek around in it.
 */
@LargeTest
public class Media3PlayerControlsTest extends Media3ServiceTest {

    @Override
    protected String mediaFileName() {
        return "30sec.mp3";
    }

    @Test
    public void testSeekToStoresThePositionInTheDatabase() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        awaitReady();
        Media3TestUtils.runOnMain(() -> controller().seekTo(10000));
        awaitPositionAtLeast(10000);
        Media3TestUtils.runOnMain(controller()::pause);

        Awaitility.await("seeked position stored in the database")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> DBReader.getFeedMedia(media.getId()).getPosition() >= 10000);
    }

    @Test
    public void testSeekForwardUsesTheConfiguredInterval() {
        UserPreferences.setFastForwardSecs(5);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        prepare(media);
        awaitCurrentMedia(media);
        awaitReady();
        Media3TestUtils.runOnMain(controller()::seekForward);

        Awaitility.await("seeked forward by the configured interval")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() >= 5000 && position() < 10000);
    }

    @Test
    public void testSeekBackUsesTheConfiguredInterval() {
        UserPreferences.setRewindSecs(5);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        prepare(media);
        awaitCurrentMedia(media);
        awaitReady();
        Media3TestUtils.runOnMain(() -> {
            controller().seekTo(15000);
            controller().seekBack();
        });

        Awaitility.await("seeked back by the configured interval")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() >= 9000 && position() <= 11000);
    }

    @Test
    public void testSeekBackDoesNotGoBeforeTheStart() {
        UserPreferences.setRewindSecs(60);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        prepare(media);
        awaitCurrentMedia(media);
        awaitReady();
        Media3TestUtils.runOnMain(() -> {
            controller().seekTo(3000);
            controller().seekBack();
        });

        Awaitility.await("rewound to the start of the episode")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() == 0);
    }

    @Test
    public void testSeekForwardBeyondTheEndStartsTheNextEpisode() {
        UserPreferences.setFastForwardSecs(600);
        FeedMedia first = DBReader.getQueue().get(0).getMedia();
        FeedMedia second = DBReader.getQueue().get(1).getMedia();

        play(first);
        awaitCurrentMedia(first);
        awaitReady();
        Media3TestUtils.runOnMain(controller()::seekForward);

        awaitCurrentMedia(second);
    }

    @Test
    public void testSetPlaybackSpeedIsStoredAsTemporarySpeed() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        Media3TestUtils.runOnMain(() -> controller().setPlaybackSpeed(1.5f));

        Awaitility.await("speed stored in the playback preferences")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentlyPlayingTemporaryPlaybackSpeed() == 1.5f);
        assertEquals(1.5f, Media3TestUtils.getOnMain(() ->
                controller().getPlaybackParameters().speed), 0.01f);
    }

    @Test
    public void testFeedPlaybackSpeedIsAppliedWhenPlaybackStarts() throws Exception {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        FeedPreferences preferences = media.getItem().getFeed().getPreferences();
        preferences.setFeedPlaybackSpeed(1.25f);
        DBWriter.setFeedPreferences(preferences).get();

        play(media);
        awaitCurrentMedia(media);

        Awaitility.await("feed speed applied")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(() ->
                        controller().getPlaybackParameters().speed) == 1.25f);
    }

    @Test
    public void testPlaybackResumesAtTheStoredPositionWithRewind() throws Exception {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        media.setPosition(20000);
        media.setLastPlayedTimeStatistics(System.currentTimeMillis()
                - RewindAfterPauseUtils.ELAPSED_TIME_FOR_MEDIUM_REWIND - 1000);
        media.setLastPlayedTimeHistory(new Date());
        DBWriter.setFeedMediaPlaybackInformation(media).get();

        prepare(media);
        awaitCurrentMedia(media);
        awaitReady();

        long expected = 20000 - RewindAfterPauseUtils.MEDIUM_REWIND;
        Awaitility.await("playback starts at the stored position minus the rewind")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() == expected);
    }

    @Test
    public void testPlaybackStartsAtTheConfiguredSkipIntro() throws Exception {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        FeedPreferences preferences = media.getItem().getFeed().getPreferences();
        preferences.setFeedSkipIntro(8);
        DBWriter.setFeedPreferences(preferences).get();

        prepare(media);
        awaitCurrentMedia(media);
        awaitReady();

        Awaitility.await("playback starts after the intro")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() == 8000);
    }

    @Test
    public void testConfiguredSkipEndingSkipsToTheNextEpisode() throws Exception {
        FeedMedia first = DBReader.getQueue().get(0).getMedia();
        FeedMedia second = DBReader.getQueue().get(1).getMedia();
        FeedPreferences preferences = first.getItem().getFeed().getPreferences();
        preferences.setFeedSkipEnding(20);
        DBWriter.setFeedPreferences(preferences).get();
        MessageEventRecorder recorder = new MessageEventRecorder();
        Media3TestUtils.runOnMain(recorder::register);

        try {
            play(first);
            awaitCurrentMedia(first);
            awaitCurrentMedia(second);

            String expected = context.getResources().getQuantityString(
                    R.plurals.pref_feed_skip_ending_snackbar, 20, 20);
            assertTrue("Expected the skip ending message, got " + recorder.getMessages(),
                    recorder.received(expected));
            assertTrue("Episode whose ending was skipped is marked as played",
                    DBReader.getFeedItem(first.getItem().getId()).isPlayed());
        } finally {
            Media3TestUtils.runOnMain(recorder::unregister);
        }
    }

    @Test
    public void testSkippingAnEpisodeStartsTheNextOneAndKeepsItInTheQueue() {
        setSmartMarkAsPlayedSecs(0);
        setSkipKeepsEpisode(true);
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia first = queue.get(0).getMedia();
        FeedMedia second = queue.get(1).getMedia();

        play(first);
        awaitCurrentMedia(first);
        awaitPositionAtLeast(300);
        Media3TestUtils.runOnMain(controller()::seekToNextMediaItem);

        awaitCurrentMedia(second);
        assertNotEquals(first.getId(), PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertTrue("Skipped episode stays in the queue",
                DBReader.getQueueIDList().contains(first.getItem().getId()));
        assertFalse("Skipped episode is not marked as played",
                DBReader.getFeedItem(first.getItem().getId()).isPlayed());
    }

    @Test
    public void testSkippingAnEpisodeRemovesItFromTheQueueWhenConfigured() {
        setSmartMarkAsPlayedSecs(0);
        setSkipKeepsEpisode(false);
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia first = queue.get(0).getMedia();
        FeedMedia second = queue.get(1).getMedia();

        play(first);
        awaitCurrentMedia(first);
        awaitPositionAtLeast(300);
        Media3TestUtils.runOnMain(controller()::seekToNextMediaItem);

        awaitCurrentMedia(second);
        Awaitility.await("skipped episode removed from the queue")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> !DBReader.getQueueIDList().contains(first.getItem().getId()));
    }
}
