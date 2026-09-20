package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.playback.RemoteMedia;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PlaybackPreferencesTest {
    private static final long FEED_ID = 7;
    private static final long MEDIA_ID = 42;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        PlaybackPreferences.init(context);
    }

    private FeedMedia createFeedMedia(String mimeType) {
        Feed feed = new Feed("https://example.com/feed.xml", null, "Feed");
        feed.setId(FEED_ID);
        FeedItem item = new FeedItem(1, "Episode", "guid", "https://example.com/1", new Date(), FeedItem.NEW, feed);
        FeedMedia media = new FeedMedia(MEDIA_ID, item, 0, 0, 1000, mimeType, null,
                "https://example.com/1.mp3", 0, null, 0, 0);
        item.setMedia(media);
        return media;
    }

    @Test
    public void defaultsDescribeNothingPlaying() {
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingMediaType());
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertFalse(PlaybackPreferences.getCurrentEpisodeIsVideo());
        assertEquals(PlaybackPreferences.PLAYER_STATUS_OTHER, PlaybackPreferences.getCurrentPlayerStatus());
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL,
                PlaybackPreferences.getCurrentlyPlayingTemporaryPlaybackSpeed(), 0f);
        assertEquals(FeedPreferences.SkipSilence.GLOBAL,
                PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence());
    }

    @Test
    public void writingFeedMediaStoresItsTypeAndId() {
        PlaybackPreferences.writeMediaPlaying(createFeedMedia("audio/mpeg"));

        assertEquals(FeedMedia.PLAYABLE_TYPE_FEEDMEDIA, PlaybackPreferences.getCurrentlyPlayingMediaType());
        assertEquals(MEDIA_ID, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertFalse(PlaybackPreferences.getCurrentEpisodeIsVideo());
    }

    @Test
    public void writingVideoFeedMediaMarksEpisodeAsVideo() {
        PlaybackPreferences.writeMediaPlaying(createFeedMedia("video/mp4"));

        assertTrue(PlaybackPreferences.getCurrentEpisodeIsVideo());
    }

    @Test
    public void writingRemoteMediaKeepsItsTypeButHasNoFeedMediaId() {
        RemoteMedia remoteMedia = new RemoteMedia("https://example.com/1.mp3", "guid",
                "https://example.com/feed.xml", "Feed", "Episode", "https://example.com/1", "Author",
                null, "https://example.com", "audio/mpeg", new Date(), null);

        PlaybackPreferences.writeMediaPlaying(remoteMedia);

        assertEquals(RemoteMedia.PLAYABLE_TYPE_REMOTE_MEDIA, PlaybackPreferences.getCurrentlyPlayingMediaType());
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
    }

    @Test
    public void writingNullPlayableClearsPlayingMedia() {
        PlaybackPreferences.writeMediaPlaying(createFeedMedia("audio/mpeg"));
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING);

        PlaybackPreferences.writeMediaPlaying(null);

        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingMediaType());
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertEquals(PlaybackPreferences.PLAYER_STATUS_OTHER, PlaybackPreferences.getCurrentPlayerStatus());
    }

    @Test
    public void writeNoMediaPlayingResetsFeedMediaIdAndPlayerStatus() {
        PlaybackPreferences.writeMediaPlaying(createFeedMedia("audio/mpeg"));
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PAUSED);

        PlaybackPreferences.writeNoMediaPlaying();

        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertEquals(PlaybackPreferences.PLAYER_STATUS_OTHER, PlaybackPreferences.getCurrentPlayerStatus());
    }

    @Test
    public void playerStatusIsPersisted() {
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING);
        assertEquals(PlaybackPreferences.PLAYER_STATUS_PLAYING, PlaybackPreferences.getCurrentPlayerStatus());

        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PAUSED);
        assertEquals(PlaybackPreferences.PLAYER_STATUS_PAUSED, PlaybackPreferences.getCurrentPlayerStatus());
    }

    @Test
    public void temporaryPlaybackSpeedIsPersisted() {
        PlaybackPreferences.setCurrentlyPlayingTemporaryPlaybackSpeed(1.75f);

        assertEquals(1.75f, PlaybackPreferences.getCurrentlyPlayingTemporaryPlaybackSpeed(), 0f);
    }

    @Test
    public void temporarySkipSilenceMapsBooleanToAggressiveOrOff() {
        PlaybackPreferences.setCurrentlyPlayingTemporarySkipSilence(true);
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE,
                PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence());

        PlaybackPreferences.setCurrentlyPlayingTemporarySkipSilence(false);
        assertEquals(FeedPreferences.SkipSilence.OFF,
                PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence());
    }

    @Test
    public void clearingTemporarySettingsRestoresGlobalDefaults() {
        PlaybackPreferences.setCurrentlyPlayingTemporaryPlaybackSpeed(2.0f);
        PlaybackPreferences.setCurrentlyPlayingTemporarySkipSilence(true);

        PlaybackPreferences.clearCurrentlyPlayingTemporaryPlaybackSettings();

        assertEquals(FeedPreferences.SPEED_USE_GLOBAL,
                PlaybackPreferences.getCurrentlyPlayingTemporaryPlaybackSpeed(), 0f);
        assertEquals(FeedPreferences.SkipSilence.GLOBAL,
                PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence());
    }
}
