package de.test.antennapod.playback;

import androidx.media3.session.MediaController;
import androidx.test.filters.LargeTest;
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
import org.awaitility.Awaitility;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Plays downloaded episodes through the Media3 playback service and verifies the effects on the playback preferences and on the database.
 */
@LargeTest
public class Media3PlaybackTest extends Media3ServiceTest {

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
        Media3TestUtils.runOnMain(controller()::pause);

        Awaitility.await("playback reported as paused")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentPlayerStatus()
                        == PlaybackPreferences.PLAYER_STATUS_PAUSED);
        assertFalse(PlaybackService.isRunning);
        Awaitility.await("position stored in the database")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> DBReader.getFeedMedia(media.getId()).getPosition() > 0);
        assertNotNull(DBReader.getFeedMedia(media.getId()).getLastPlayedTimeHistory());
        assertTrue(DBReader.getFeedMedia(media.getId()).getDuration() > 0);
    }

    @Test
    public void testPlayingAnEpisodeRemovesItsNewFlag() {
        FeedItem item = DBReader.getQueue().get(0);
        DBWriter.markItemsPlayed(FeedItem.NEW, false, Collections.singletonList(item));

        play(item.getMedia());
        awaitCurrentMedia(item.getMedia());

        Awaitility.await("new flag removed")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> !DBReader.getFeedItem(item.getId()).isNew());
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
        awaitPlaying();

        assertTrue("Finished episode is marked as played",
                DBReader.getFeedItem(first.getItem().getId()).isPlayed());
        assertFalse("Finished episode is removed from the queue",
                DBReader.getQueueIDList().contains(first.getItem().getId()));
    }

    @Test
    public void testContinuousPlaybackDisabledLoadsNextEpisodeWithoutPlaying() {
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
        assertFalse(Media3TestUtils.getOnMain(controller()::isPlaying));
    }

    @Test
    public void testFinishingTheLastQueueItemClearsCurrentlyPlaying() throws Exception {
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
        assertEquals(0, DBReader.getQueueIDList().size());
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
    public void testFinishedEpisodeIsAddedToThePlaybackHistory() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);

        Awaitility.await("episode added to the playback history")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> DBReader.getFeedMedia(media.getId()).getLastPlayedTimeHistory() != null
                        && DBReader.getFeedMedia(media.getId()).getLastPlayedTimeHistory().getTime() > 0);
    }

    @Test
    public void testMissingLocalFileReportsAPlaybackError() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        assertTrue("The downloaded file is deleted before playback",
                new File(media.getLocalFileUrl()).delete());
        PlaybackEventRecorder recorder = new PlaybackEventRecorder();
        Media3TestUtils.runOnMain(recorder::register);

        try {
            play(media);

            Awaitility.await("playback error reported to the user")
                    .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .until(() -> !recorder.getErrors().isEmpty());
            assertFalse("The error message is not empty",
                    recorder.getErrors().get(0).isEmpty());
            assertFalse(PlaybackService.isRunning);
        } finally {
            Media3TestUtils.runOnMain(recorder::unregister);
        }
    }

    @Test
    public void testUnknownMediaIdDoesNotStartPlayback() {
        MediaController mediaController = controller();
        Media3TestUtils.runOnMain(() -> {
            mediaController.setMediaItem(MediaItemAdapter.fromMediaIdStub(123456789L));
            mediaController.prepare();
            mediaController.play();
        });

        Awaitility.await("media item without database entry is rejected")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller()::getMediaItemCount) == 0);
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING,
                PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertFalse(PlaybackService.isRunning);
    }
}
