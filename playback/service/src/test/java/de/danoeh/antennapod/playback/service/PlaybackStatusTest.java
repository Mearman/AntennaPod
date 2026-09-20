package de.danoeh.antennapod.playback.service;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Date;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PlaybackStatusTest {

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        PlaybackPreferences.init(context);
        PlaybackPreferences.writeNoMediaPlaying();
        PlaybackService.isRunning = false;
    }

    @After
    public void tearDown() {
        PlaybackPreferences.writeNoMediaPlaying();
        PlaybackService.isRunning = false;
    }

    @Test
    public void nothingIsPlayingWhileNoMediaIsRemembered() {
        assertFalse(PlaybackStatus.isPlaying(media(1)));
        assertFalse(PlaybackStatus.isCurrentlyPlaying(media(1)));
    }

    @Test
    public void theRememberedEpisodeIsTheOnlyOneReportedAsPlaying() {
        PlaybackPreferences.writeMediaPlaying(media(1));

        assertTrue(PlaybackStatus.isPlaying(media(1)));
        assertFalse(PlaybackStatus.isPlaying(media(2)));
    }

    @Test
    public void aMissingEpisodeIsNeverReportedAsPlaying() {
        PlaybackPreferences.writeMediaPlaying(media(1));

        assertFalse(PlaybackStatus.isPlaying(null));
    }

    @Test
    public void theRememberedEpisodeIsNotCurrentlyPlayingWhileTheServiceIsDown() {
        PlaybackPreferences.writeMediaPlaying(media(1));
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING);
        PlaybackService.isRunning = false;

        assertTrue(PlaybackStatus.isPlaying(media(1)));
        assertFalse(PlaybackStatus.isCurrentlyPlaying(media(1)));
    }

    @Test
    public void aPausedEpisodeIsNotCurrentlyPlayingEvenWithTheServiceRunning() {
        PlaybackPreferences.writeMediaPlaying(media(1));
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PAUSED);
        PlaybackService.isRunning = true;

        assertFalse(PlaybackStatus.isCurrentlyPlaying(media(1)));
    }

    @Test
    public void theRememberedEpisodeIsCurrentlyPlayingWhileTheRunningServiceReportsPlayback() {
        PlaybackPreferences.writeMediaPlaying(media(1));
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING);
        PlaybackService.isRunning = true;

        assertTrue(PlaybackStatus.isCurrentlyPlaying(media(1)));
    }

    private static FeedMedia media(long id) {
        Feed feed = new Feed(1, null, "Feed", "http://example.com", "d", null, null, null, null,
                "id", null, null, "http://example.com/feed.xml", 0);
        FeedItem item = new FeedItem(id, "Episode", "id" + id, "http://example.com", new Date(0),
                FeedItem.UNPLAYED, feed);
        FeedMedia media = new FeedMedia(id, item, 300000, 0, 1, "audio/mp3", null,
                "http://example.com/e.mp3", 0, null, 0, 0);
        item.setMedia(media);
        return media;
    }
}
