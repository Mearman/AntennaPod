package de.danoeh.antennapod.storage.preferences;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.playback.RemoteMedia;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PlaybackPreferencesStoredValuesTest extends StoredPreferencesTestBase {
    private static final long FEED_ID = 7;
    private static final long MEDIA_ID = 42;

    private FeedMedia feedMedia(String mimeType) {
        Feed feed = new Feed("https://example.com/feed.xml", null, "Feed");
        feed.setId(FEED_ID);
        FeedItem item = new FeedItem();
        item.setFeed(feed);
        FeedMedia media = new FeedMedia(item, "https://example.com/episode.mp3", 5000000, mimeType);
        media.setId(MEDIA_ID);
        item.setMedia(media);
        return media;
    }

    @Test
    public void nothingIsPlayingByDefault() {
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingMediaType());
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertFalse(PlaybackPreferences.getCurrentEpisodeIsVideo());
        assertEquals(PlaybackPreferences.PLAYER_STATUS_OTHER, PlaybackPreferences.getCurrentPlayerStatus());
    }

    @Test
    public void playingFeedMediaIsRememberedByItsIdentifiers() {
        PlaybackPreferences.writeMediaPlaying(feedMedia("audio/mpeg"));

        assertEquals(FeedMedia.PLAYABLE_TYPE_FEEDMEDIA, PlaybackPreferences.getCurrentlyPlayingMediaType());
        assertEquals(MEDIA_ID, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertEquals(FEED_ID, stored.getLong("de.danoeh.antennapod.preferences.lastPlayedFeedId", -1));
        assertFalse(PlaybackPreferences.getCurrentEpisodeIsVideo());
    }

    @Test
    public void playingVideoIsRemembered() {
        PlaybackPreferences.writeMediaPlaying(feedMedia("video/mp4"));

        assertTrue(PlaybackPreferences.getCurrentEpisodeIsVideo());
    }

    @Test
    public void playingRemoteMediaHasNoFeedMediaIdentifier() {
        RemoteMedia remote = new RemoteMedia("https://example.com/remote.mp3", "guid", "https://example.com/feed.xml",
                "Feed", "Episode", "https://example.com/episode", "Author", "https://example.com/image.png",
                "https://example.com", "audio/mpeg", new Date(0), "notes");

        PlaybackPreferences.writeMediaPlaying(remote);

        assertEquals(RemoteMedia.PLAYABLE_TYPE_REMOTE_MEDIA, PlaybackPreferences.getCurrentlyPlayingMediaType());
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING,
                stored.getLong("de.danoeh.antennapod.preferences.lastPlayedFeedId", 0));
    }

    @Test
    public void writingNoMediaResetsWhatWasPlayingIncludingThePlayerStatus() {
        PlaybackPreferences.writeMediaPlaying(feedMedia("audio/mpeg"));
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING);

        PlaybackPreferences.writeMediaPlaying(null);

        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingMediaType());
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertEquals(PlaybackPreferences.PLAYER_STATUS_OTHER, PlaybackPreferences.getCurrentPlayerStatus());
    }

    @Test
    public void feedMediaIdIsHiddenOnceTheMediaTypeIsCleared() {
        PlaybackPreferences.writeMediaPlaying(feedMedia("audio/mpeg"));

        PlaybackPreferences.writeNoMediaPlaying();

        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING,
                stored.getLong("de.danoeh.antennapod.preferences.lastPlayedFeedMediaId", 0));
    }

    @Test
    public void playerStatusIsStored() {
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PAUSED);

        assertEquals(PlaybackPreferences.PLAYER_STATUS_PAUSED, PlaybackPreferences.getCurrentPlayerStatus());
    }

    @Test
    public void temporaryPlaybackSettingsOverrideTheFeedSettingsUntilCleared() {
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL, PlaybackPreferences.getCurrentlyPlayingTemporaryPlaybackSpeed(),
                0.0001f);
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence());

        PlaybackPreferences.setCurrentlyPlayingTemporaryPlaybackSpeed(1.8f);
        PlaybackPreferences.setCurrentlyPlayingTemporarySkipSilence(true);

        assertEquals(1.8f, PlaybackPreferences.getCurrentlyPlayingTemporaryPlaybackSpeed(), 0.0001f);
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE,
                PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence());

        PlaybackPreferences.setCurrentlyPlayingTemporarySkipSilence(false);
        assertEquals(FeedPreferences.SkipSilence.OFF, PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence());

        PlaybackPreferences.clearCurrentlyPlayingTemporaryPlaybackSettings();

        assertEquals(FeedPreferences.SPEED_USE_GLOBAL, PlaybackPreferences.getCurrentlyPlayingTemporaryPlaybackSpeed(),
                0.0001f);
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence());
    }
}
