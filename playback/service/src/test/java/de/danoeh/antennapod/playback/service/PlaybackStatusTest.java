package de.danoeh.antennapod.playback.service;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.internal.PlaybackTestDatabase;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PlaybackStatusTest {
    private static final String FEED_URL = "http://example.com/feed";
    private Feed feed;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        feed = PlaybackTestDatabase.storeFeed(context, FEED_URL, "Main", 2);
        PlaybackService.isRunning = false;
    }

    @After
    public void tearDown() {
        PlaybackService.isRunning = false;
        PlaybackTestDatabase.tearDown();
    }

    private FeedMedia storedMedia(int index) {
        return PlaybackTestDatabase.storedItems(feed.getId()).get(index).getMedia();
    }

    @Test
    public void nothingIsPlayingWhileThePreferencesRememberNoEpisode() {
        PlaybackPreferences.writeNoMediaPlaying();

        assertFalse(PlaybackStatus.isPlaying(storedMedia(0)));
        assertFalse(PlaybackStatus.isCurrentlyPlaying(storedMedia(0)));
    }

    @Test
    public void theRememberedEpisodeIsTheOnlyOneReportedAsPlaying() {
        FeedMedia media = storedMedia(0);
        PlaybackPreferences.writeMediaPlaying(media);

        assertTrue(PlaybackStatus.isPlaying(media));
        assertFalse(PlaybackStatus.isPlaying(storedMedia(1)));
    }

    @Test
    public void noEpisodeIsPlayingWhenNoEpisodeIsGiven() {
        PlaybackPreferences.writeMediaPlaying(storedMedia(0));

        assertFalse(PlaybackStatus.isPlaying(null));
    }

    @Test
    public void theRememberedEpisodeIsOnlyCurrentlyPlayingWhileTheServiceRunsAndPlays() {
        FeedMedia media = storedMedia(0);
        PlaybackPreferences.writeMediaPlaying(media);
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING);

        assertFalse(PlaybackStatus.isCurrentlyPlaying(media));

        PlaybackService.isRunning = true;

        assertTrue(PlaybackStatus.isCurrentlyPlaying(media));
    }

    @Test
    public void aPausedEpisodeIsRememberedAsPlayingButNotAsCurrentlyPlaying() {
        FeedMedia media = storedMedia(0);
        PlaybackPreferences.writeMediaPlaying(media);
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PAUSED);
        PlaybackService.isRunning = true;

        assertTrue(PlaybackStatus.isPlaying(media));
        assertFalse(PlaybackStatus.isCurrentlyPlaying(media));
    }
}
