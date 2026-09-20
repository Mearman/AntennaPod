package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer;
import de.danoeh.antennapod.playback.base.PlayerStatus;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PlaybackVolumeUpdateTest {
    private static final String FEED_URL = "http://example.com/feed";
    private Context context;
    private Feed feed;
    private RecordingCallback callback;
    private LocalPSMP player;
    private PlaybackVolumeUpdater updater;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        feed = PlaybackTestDatabase.storeFeed(context, FEED_URL, "Main", 1);
        callback = new RecordingCallback();
        player = new LocalPSMP(context, callback);
        updater = new PlaybackVolumeUpdater();
    }

    @After
    public void tearDown() {
        player.shutdown();
        PlaybackTestDatabase.tearDown();
    }

    private FeedMedia downloadedMedia() throws IOException, ExecutionException, InterruptedException {
        File file = new File(context.getCacheDir(), "episode.mp3");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[]{0, 0, 0, 0});
        }
        FeedMedia media = PlaybackTestDatabase.storedItems(feed.getId()).get(0).getMedia();
        media.setLocalFileUrl(file.getAbsolutePath());
        media.setDownloaded(true, System.currentTimeMillis());
        DBWriter.setFeedMedia(media).get();
        return PlaybackTestDatabase.storedItems(feed.getId()).get(0).getMedia();
    }

    @Test
    public void changingTheVolumeSettingOfTheEpisodeBeingPlayedReappliesItWithoutStopping()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia();
        player.playMediaObject(media, false, true, true);
        callback.statuses.clear();

        updater.updateVolumeIfNecessary(player, feed.getId(), VolumeAdaptionSetting.HEAVY_REDUCTION);

        assertEquals(VolumeAdaptionSetting.HEAVY_REDUCTION,
                media.getItem().getFeed().getPreferences().getVolumeAdaptionSetting());
        assertTrue(callback.statuses.contains(PlayerStatus.PAUSED));
        assertEquals(PlayerStatus.PLAYING, callback.statuses.get(callback.statuses.size() - 1));
        assertEquals(PlayerStatus.PLAYING, player.getPlayerStatus());
    }

    @Test
    public void changingTheVolumeSettingOfAPausedEpisodeStoresItWithoutTouchingThePlayer()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia();
        player.playMediaObject(media, false, false, true);
        callback.statuses.clear();

        updater.updateVolumeIfNecessary(player, feed.getId(), VolumeAdaptionSetting.LIGHT_BOOST);

        assertEquals(VolumeAdaptionSetting.LIGHT_BOOST,
                media.getItem().getFeed().getPreferences().getVolumeAdaptionSetting());
        assertTrue(callback.statuses.isEmpty());
        assertEquals(PlayerStatus.PREPARED, player.getPlayerStatus());
    }

    @Test
    public void changingTheVolumeSettingOfAnotherFeedLeavesTheCurrentEpisodeAlone()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia();
        media.getItem().getFeed().getPreferences().setVolumeAdaptionSetting(VolumeAdaptionSetting.LIGHT_REDUCTION);
        player.playMediaObject(media, false, true, true);
        callback.statuses.clear();

        updater.updateVolumeIfNecessary(player, feed.getId() + 1, VolumeAdaptionSetting.HEAVY_BOOST);

        assertEquals(VolumeAdaptionSetting.LIGHT_REDUCTION,
                media.getItem().getFeed().getPreferences().getVolumeAdaptionSetting());
        assertTrue(callback.statuses.isEmpty());
        assertEquals(PlayerStatus.PLAYING, player.getPlayerStatus());
    }

    @Test
    public void changingTheVolumeSettingWhileNothingIsLoadedIsIgnored() {
        updater.updateVolumeIfNecessary(player, feed.getId(), VolumeAdaptionSetting.HEAVY_BOOST);

        assertTrue(callback.statuses.isEmpty());
        assertEquals(VolumeAdaptionSetting.OFF,
                PlaybackTestDatabase.storedItems(feed.getId()).get(0)
                        .getFeed().getPreferences().getVolumeAdaptionSetting());
        assertEquals(PlayerStatus.STOPPED, player.getPlayerStatus());
    }

    private static class RecordingCallback implements PlaybackServiceMediaPlayer.PSMPCallback {
        private final List<PlayerStatus> statuses = new ArrayList<>();

        @Override
        public void statusChanged(PlaybackServiceMediaPlayer.PSMPInfo newInfo) {
            statuses.add(newInfo.getPlayerStatus());
        }

        @Override
        public void shouldStop() {
        }

        @Override
        public void episodeFinishedPlayback() {
        }

        @Override
        public boolean shouldContinueToNextEpisode() {
            return false;
        }

        @Override
        public void onMediaChanged(boolean reloadUI) {
        }

        @Override
        public void onPostPlayback(@NonNull Playable media, boolean ended, boolean skipped, boolean playingNext) {
        }

        @Override
        public void onPlaybackStart(@NonNull Playable playable, int position) {
        }

        @Override
        public void onPlaybackPause(Playable playable, int position) {
        }

        @Override
        public Playable getNextInQueue(Playable currentMedia) {
            return null;
        }

        @Nullable
        @Override
        public Playable findMedia(@NonNull String url) {
            return null;
        }

        @Override
        public void onPlaybackEnded(MediaType mediaType, boolean stopPlaying) {
        }

        @Override
        public void ensureMediaInfoLoaded(@NonNull Playable media) {
        }
    }
}
