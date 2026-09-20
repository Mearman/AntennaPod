package de.test.antennapod.playback;

import androidx.test.filters.LargeTest;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.playback.service.PlaybackStatus;
import de.danoeh.antennapod.storage.database.DBReader;
import org.awaitility.Awaitility;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@LargeTest
public class Media3PlaybackStarterTest extends Media3ServiceTest {

    @Override
    protected String mediaFileName() {
        return "30sec.mp3";
    }

    @Test
    public void testStarterPlaysTheRequestedEpisode() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        startPlayback(media);

        awaitCurrentMedia(media);
        awaitPlaying();
        assertTrue(PlaybackStatus.isCurrentlyPlaying(media));
        assertFalse("Another episode is not reported as playing",
                PlaybackStatus.isPlaying(DBReader.getQueue().get(1).getMedia()));
    }

    @Test
    public void testStarterResumesTheEpisodeThatIsAlreadyLoaded() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        startPlayback(media);
        awaitCurrentMedia(media);
        awaitReady();
        Media3TestUtils.runOnMain(() -> controller().seekTo(12000));
        awaitPositionAtLeast(12000);
        pausePlayback();

        startPlayback(media);

        awaitPlaying();
        assertTrue("Playback resumes where it was paused instead of starting over",
                position() >= 10000);
    }

    @Test
    public void testStarterSwitchesToAnotherEpisode() {
        FeedMedia first = DBReader.getQueue().get(0).getMedia();
        FeedMedia second = DBReader.getQueue().get(1).getMedia();
        startPlayback(first);
        awaitCurrentMedia(first);
        awaitPlaying();

        startPlayback(second);

        awaitCurrentMedia(second);
        awaitPlaying();
        assertFalse(PlaybackStatus.isPlaying(first));
    }

    private void startPlayback(FeedMedia media) {
        Media3TestUtils.runOnMain(() -> new PlaybackServiceStarter(context, media).start());
    }
}
