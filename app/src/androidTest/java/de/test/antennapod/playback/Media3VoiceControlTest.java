package de.test.antennapod.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaController;
import androidx.test.filters.LargeTest;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import org.awaitility.Awaitility;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Starts playback through the search requests that a voice assistant sends to the media session, both for a named episode and for the open request to play something.
 */
@LargeTest
public class Media3VoiceControlTest extends Media3ServiceTest {

    @Test
    public void testVoiceSearchPlaysAMatchingEpisode() {
        String query = DBReader.getQueue().get(3).getTitle();
        List<FeedItem> matches = DBReader.searchFeedItems(0, query, FeedItemFilter.unfiltered());
        assertFalse("The query matches at least one episode", matches.isEmpty());
        List<Long> matchingMediaIds = new ArrayList<>();
        for (FeedItem match : matches) {
            if (match.getMedia() != null) {
                matchingMediaIds.add(match.getMedia().getId());
            }
        }

        playSearch(query);

        Awaitility.await("an episode matching the query is playing")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> matchingMediaIds.contains(
                        PlaybackPreferences.getCurrentlyPlayingFeedMediaId()));
        awaitPlaying();
    }

    @Test
    public void testVoiceSearchWithoutAMatchStartsNothing() {
        playSearch("no episode is called like this");

        Awaitility.await("nothing is loaded for a search without results")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller()::getMediaItemCount) == 0);
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING,
                PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
    }

    @Test
    public void testPlaySomethingResumesTheLastPlayedEpisode() throws Exception {
        FeedMedia media = DBReader.getQueue().get(2).getMedia();
        media.setPosition(1000);
        media.setLastPlayedTimeStatistics(System.currentTimeMillis());
        media.setLastPlayedTimeHistory(new Date());
        DBWriter.setFeedMediaPlaybackInformation(media).get();
        PlaybackPreferences.writeMediaPlaying(media);

        playSearch("");

        awaitCurrentMedia(media);
        awaitPlaying();
    }

    @Test
    public void testPlaySomethingFallsBackToAnEpisodeOfTheLibrary() {
        assertFalse("The library is not empty", DBReader.getQueue().isEmpty());

        playSearch("");

        Awaitility.await("some episode is playing")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
                        != PlaybackPreferences.NO_MEDIA_PLAYING);
        awaitPlaying();
    }

    private void playSearch(String query) {
        MediaController mediaController = controller();
        MediaItem item = new MediaItem.Builder()
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder()
                        .setSearchQuery(query)
                        .build())
                .build();
        Media3TestUtils.runOnMain(() -> {
            mediaController.setMediaItem(item);
            mediaController.prepare();
            mediaController.play();
        });
    }
}
