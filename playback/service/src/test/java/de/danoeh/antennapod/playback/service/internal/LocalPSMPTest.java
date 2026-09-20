package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.danoeh.antennapod.event.PlayerErrorEvent;
import de.danoeh.antennapod.event.playback.SpeedChangedEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer;
import de.danoeh.antennapod.playback.base.PlayerStatus;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class LocalPSMPTest {
    private static final String FEED_URL = "http://example.com/feed";
    private Context context;
    private Feed feed;
    private RecordingCallback callback;
    private LocalPSMP player;
    private final List<Object> events = new ArrayList<>();

    @Subscribe
    public void onSpeedChanged(SpeedChangedEvent event) {
        events.add(event);
    }

    @Subscribe(sticky = true)
    public void onPlayerError(PlayerErrorEvent event) {
        events.add(event);
    }

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        feed = PlaybackTestDatabase.storeFeed(context, FEED_URL, "Main", 2);
        callback = new RecordingCallback();
        player = new LocalPSMP(context, callback);
        EventBus.getDefault().register(this);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(this);
        player.shutdown();
        PlaybackTestDatabase.tearDown();
    }

    private FeedMedia storedMedia(int index) {
        return PlaybackTestDatabase.storedItem(feed.getId(), FEED_URL + "/id" + index).getMedia();
    }

    private FeedMedia downloadedMedia(int index) throws IOException, ExecutionException, InterruptedException {
        File file = new File(context.getCacheDir(), "episode" + index + ".mp3");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[]{0, 0, 0, 0});
        }
        FeedMedia media = storedMedia(index);
        media.setLocalFileUrl(file.getAbsolutePath());
        media.setDownloaded(true, System.currentTimeMillis());
        DBWriter.setFeedMedia(media).get();
        return storedMedia(index);
    }

    @Test
    public void aFreshPlayerIsStoppedWithNothingLoaded() {
        assertEquals(PlayerStatus.STOPPED, player.getPlayerStatus());
        assertNull(player.getPlayable());
        assertEquals(MediaType.UNKNOWN, player.getCurrentMediaType());
        assertFalse(player.isStreaming());
        assertFalse(player.isCasting());
        assertTrue(player.getAudioTracks().isEmpty());
        assertEquals(-1, player.getSelectedAudioTrack());
    }

    @Test
    public void loadingADownloadedEpisodeWithoutPreparingLeavesItInitialized()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);

        player.playMediaObject(media, false, false, false);

        assertEquals(PlayerStatus.INITIALIZED, player.getPlayerStatus());
        assertSame(media, player.getPlayable());
        assertEquals(MediaType.AUDIO, player.getCurrentMediaType());
        assertFalse(player.isStreaming());
        assertEquals(1, callback.mediaChanges);
        assertEquals(1, callback.infoLoads);
    }

    @Test
    public void preparingADownloadedEpisodeWithoutStartingLeavesItPaused()
            throws IOException, ExecutionException, InterruptedException {
        player.playMediaObject(downloadedMedia(0), false, false, true);

        assertEquals(PlayerStatus.PREPARED, player.getPlayerStatus());
        assertFalse(player.isStartWhenPrepared());
    }

    @Test
    public void streamingAnEpisodeMarksThePlayerAsStreaming() {
        player.playMediaObject(storedMedia(0), true, false, false);

        assertTrue(player.isStreaming());
        assertEquals(PlayerStatus.INITIALIZED, player.getPlayerStatus());
    }

    @Test
    public void anEpisodeThatIsNeitherDownloadedNorStreamedEndsInTheErrorState() {
        player.playMediaObject(storedMedia(0), false, false, true);

        assertEquals(PlayerStatus.ERROR, player.getPlayerStatus());
        assertNull(player.getPlayable());
        assertTrue(events.stream().anyMatch(event -> event instanceof PlayerErrorEvent));
    }

    @Test
    public void resumingAPreparedEpisodeStartsPlayingIt()
            throws IOException, ExecutionException, InterruptedException {
        player.playMediaObject(downloadedMedia(0), false, false, true);

        player.resume();

        assertEquals(PlayerStatus.PLAYING, player.getPlayerStatus());
    }

    @Test
    public void pausingAPlayingEpisodeStopsItWithoutForgettingIt()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);
        player.playMediaObject(media, false, true, true);

        player.pause(true, false);

        assertEquals(PlayerStatus.PAUSED, player.getPlayerStatus());
        assertSame(media, player.getPlayable());
    }

    @Test
    public void pausingAnEpisodeThatIsNotPlayingIsIgnored()
            throws IOException, ExecutionException, InterruptedException {
        player.playMediaObject(downloadedMedia(0), false, false, true);

        player.pause(true, false);

        assertEquals(PlayerStatus.PREPARED, player.getPlayerStatus());
    }

    @Test
    public void startingAnEpisodeThatIsAlreadyPlayingDoesNotRestartIt()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);
        player.playMediaObject(media, false, true, true);
        int changesBefore = callback.mediaChanges;

        player.playMediaObject(media, false, true, true);

        assertEquals(changesBefore, callback.mediaChanges);
        assertEquals(PlayerStatus.PLAYING, player.getPlayerStatus());
    }

    @Test
    public void switchingToAnotherEpisodeReportsThePreviousOneAsFinished()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia first = downloadedMedia(0);
        FeedMedia second = downloadedMedia(1);
        player.playMediaObject(first, false, true, true);

        player.playMediaObject(second, false, false, true);

        assertSame(second, player.getPlayable());
        assertEquals(1, callback.postPlaybacks.size());
        assertSame(first, callback.postPlaybacks.get(0));
    }

    @Test
    public void preparingAnEpisodeThatWasOnlyInitializedMovesItToPrepared()
            throws IOException, ExecutionException, InterruptedException {
        player.playMediaObject(downloadedMedia(0), false, false, false);

        player.prepare();

        assertEquals(PlayerStatus.PREPARED, player.getPlayerStatus());
    }

    @Test
    public void reinitialisingKeepsTheSameEpisodeLoadedButUnprepared()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);
        player.playMediaObject(media, false, false, true);

        player.reinit();

        assertEquals(PlayerStatus.INITIALIZED, player.getPlayerStatus());
        assertSame(media, player.getPlayable());
    }

    @Test
    public void reinitialisingWithoutAnyEpisodeLoadedIsIgnored() {
        player.reinit();

        assertEquals(PlayerStatus.STOPPED, player.getPlayerStatus());
    }

    @Test
    public void seekingWithinAPreparedEpisodeMovesItsStoredPosition()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);
        media.setDuration(600000);
        player.playMediaObject(media, false, false, true);

        player.seekTo(30000);

        assertEquals(30000, media.getPosition());
        assertEquals(PlayerStatus.PREPARED, player.getPlayerStatus());
    }

    @Test
    public void seekingToANegativeTimeIsTreatedAsSeekingToTheStart()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);
        media.setDuration(600000);
        player.playMediaObject(media, false, false, true);

        player.seekTo(-5000);

        assertEquals(0, media.getPosition());
    }

    @Test
    public void seekingPastTheEndOfTheEpisodeEndsPlaybackWithoutMovingOn()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);
        media.setDuration(600000);
        player.playMediaObject(media, false, false, true);

        player.seekTo(700000);

        assertEquals(1, callback.episodeFinishes);
        assertEquals(1, callback.playbackEndings.size());
        assertNull(callback.playbackEndings.get(0));
        assertSame(media, callback.postPlaybacks.get(0));
    }

    @Test
    public void seekingBeforeAnEpisodeIsPreparedStoresThePositionAndPreparesIt()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);
        media.setDuration(600000);
        player.playMediaObject(media, false, true, false);

        player.seekTo(45000);

        assertEquals(45000, media.getPosition());
        assertEquals(PlayerStatus.PREPARED, player.getPlayerStatus());
        assertFalse(player.isStartWhenPrepared());
    }

    @Test
    public void seekingByAnOffsetMovesRelativeToTheCurrentPosition()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);
        media.setDuration(600000);
        media.setPosition(20000);
        player.playMediaObject(media, false, false, true);

        player.seekDelta(10000);

        assertEquals(30000, media.getPosition());
    }

    @Test
    public void thePlaybackSpeedIsAnnouncedAndAppliedToThePlayer()
            throws IOException, ExecutionException, InterruptedException {
        player.playMediaObject(downloadedMedia(0), false, false, true);
        events.clear();

        player.setPlaybackParams(1.5f, true);

        assertEquals(1.5f, player.getPlaybackSpeed(), 0.0001f);
        assertTrue(player.getSkipSilence());
        assertEquals(1.5f, ((SpeedChangedEvent) events.get(0)).getNewSpeed(), 0.0001f);
    }

    @Test
    public void aStoppedPlayerAlwaysReportsNormalSpeedAndNoSilenceSkipping() {
        assertEquals(1.0f, player.getPlaybackSpeed(), 0.0001f);
        assertFalse(player.getSkipSilence());
    }

    @Test
    public void anAudioEpisodeHasNoVideoSize() throws IOException, ExecutionException, InterruptedException {
        player.playMediaObject(downloadedMedia(0), false, false, true);

        assertNull(player.getVideoSize());
    }

    @Test
    public void stoppingPlaybackReportsTheEpisodeAsFinishedAndPlaysNothingNext()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);
        player.playMediaObject(media, false, true, true);

        player.stopPlayback(true);

        assertEquals(1, callback.episodeFinishes);
        assertEquals(1, callback.playbackEndings.size());
        assertNull(callback.playbackEndings.get(0));
        assertSame(media, callback.postPlaybacks.get(0));
    }

    @Test
    public void whenTheQueueHasAnotherEpisodeItIsLoadedAfterTheCurrentOneEnds()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia first = downloadedMedia(0);
        FeedMedia second = downloadedMedia(1);
        first.setPosition(5000);
        callback.nextInQueue = second;
        callback.continueToNextEpisode = true;
        player.playMediaObject(first, false, true, true);

        player.skip();

        assertSame(second, player.getPlayable());
        assertEquals(1, callback.playbackEndings.size());
        assertEquals(MediaType.AUDIO, callback.playbackEndings.get(0));
    }

    @Test
    public void skippingInsideTheFirstSecondIsIgnored()
            throws IOException, ExecutionException, InterruptedException {
        FeedMedia media = downloadedMedia(0);
        media.setPosition(500);
        player.playMediaObject(media, false, true, true);
        callback.episodeFinishes = 0;

        player.skip();

        assertEquals(0, callback.episodeFinishes);
        assertSame(media, player.getPlayable());
    }

    @Test
    public void aShutDownPlayerHoldsNoPlayerAnyMore() throws IOException, ExecutionException, InterruptedException {
        player.playMediaObject(downloadedMedia(0), false, false, true);

        player.shutdown();

        assertEquals(PlayerStatus.STOPPED, player.getPlayerStatus());
        assertTrue(player.getAudioTracks().isEmpty());
        assertEquals(-1, player.getSelectedAudioTrack());
    }

    @Test
    public void thePositionOfAnEpisodeThatWasNeverPreparedComesFromTheDatabase() {
        FeedMedia media = storedMedia(0);
        media.setPosition(12345);
        player.playMediaObject(media, true, false, false);

        assertEquals(12345, player.getPosition());
        assertEquals(600000, player.getDuration());
    }

    private static class RecordingCallback implements PlaybackServiceMediaPlayer.PSMPCallback {
        private final List<Playable> postPlaybacks = new ArrayList<>();
        private final List<MediaType> playbackEndings = new ArrayList<>();
        private int mediaChanges;
        private int infoLoads;
        private int episodeFinishes;
        private boolean continueToNextEpisode;
        private Playable nextInQueue;

        @Override
        public void statusChanged(PlaybackServiceMediaPlayer.PSMPInfo newInfo) {
        }

        @Override
        public void shouldStop() {
        }

        @Override
        public void episodeFinishedPlayback() {
            episodeFinishes++;
        }

        @Override
        public boolean shouldContinueToNextEpisode() {
            return continueToNextEpisode;
        }

        @Override
        public void onMediaChanged(boolean reloadUI) {
            mediaChanges++;
        }

        @Override
        public void onPostPlayback(@NonNull Playable media, boolean ended, boolean skipped, boolean playingNext) {
            postPlaybacks.add(media);
        }

        @Override
        public void onPlaybackStart(@NonNull Playable playable, int position) {
        }

        @Override
        public void onPlaybackPause(Playable playable, int position) {
        }

        @Override
        public Playable getNextInQueue(Playable currentMedia) {
            return nextInQueue;
        }

        @Nullable
        @Override
        public Playable findMedia(@NonNull String url) {
            return null;
        }

        @Override
        public void onPlaybackEnded(MediaType mediaType, boolean stopPlaying) {
            playbackEndings.add(mediaType);
        }

        @Override
        public void ensureMediaInfoLoaded(@NonNull Playable media) {
            infoLoads++;
        }
    }
}
