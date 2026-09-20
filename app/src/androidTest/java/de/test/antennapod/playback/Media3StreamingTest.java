package de.test.antennapod.playback;

import androidx.test.filters.LargeTest;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@LargeTest
public class Media3StreamingTest extends Media3ServiceTest {

    @Override
    protected String mediaFileName() {
        return "30sec.mp3";
    }

    @Override
    protected boolean downloadEpisodes() {
        return false;
    }

    @Before
    public void allowStreaming() {
        UserPreferences.setAllowMobileStreaming(true);
    }

    @Test
    public void testStreamedEpisodePlaysWithoutBeingDownloaded() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        assertFalse("The episode is not downloaded before the test", media.localFileAvailable());

        play(media);

        awaitCurrentMedia(media);
        awaitPlaying();
        awaitPositionAtLeast(1000);
        assertFalse("Streaming does not mark the episode as downloaded",
                DBReader.getFeedMedia(media.getId()).isDownloaded());
    }

    @Test
    public void testStreamedEpisodeStoresItsPositionAndDuration() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        long playedUntil = 6000;

        play(media);
        awaitCurrentMedia(media);
        awaitPositionAtLeast(playedUntil);
        pausePlayback();

        Awaitility.await("streamed position stored in the database")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> DBReader.getFeedMedia(media.getId()).getPosition() >= playedUntil);
        int storedPosition = DBReader.getFeedMedia(media.getId()).getPosition();
        long pausedAt = position();
        assertTrue("Stored position " + storedPosition + " is not beyond the position "
                        + pausedAt + " where playback was paused", storedPosition <= pausedAt);
        assertTrue("The duration of the streamed episode is known",
                DBReader.getFeedMedia(media.getId()).getDuration() > 0);
    }

    @Test
    public void testSeekingInAStreamedEpisodeWorks() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        awaitReady();
        Media3TestUtils.runOnMain(() -> controller().seekTo(20000));

        awaitPositionAtLeast(20000);
    }

    @Test
    public void testStreamedEpisodeContinuesWithTheNextQueueItem() {
        UserPreferences.setFollowQueue(true);
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia first = queue.get(0).getMedia();
        FeedMedia second = queue.get(1).getMedia();

        play(first);
        awaitCurrentMedia(first);
        awaitReady();
        Media3TestUtils.runOnMain(() -> controller().seekTo(29000));

        awaitCurrentMedia(second);
        assertTrue("Finished episode is marked as played",
                DBReader.getFeedItem(first.getItem().getId()).isPlayed());
        assertEquals(PlaybackPreferences.PLAYER_STATUS_PLAYING, PlaybackPreferences.getCurrentPlayerStatus());
    }
}
