package de.danoeh.antennapod.playback.base;

import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.util.Pair;
import android.view.SurfaceHolder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceMediaPlayerTest {
    private Context context;
    private RecordingCallback callback;
    private TestMediaPlayer player;
    private FeedMedia media;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackBaseTestDatabase.setUp(context);
        long feedId = PlaybackBaseTestDatabase.storeFeed(context, "http://example.com/feed", "Feed", 1).getId();
        media = PlaybackBaseTestDatabase.storedMedia(feedId, "id-0");
        callback = new RecordingCallback();
        player = new TestMediaPlayer(context, callback);
    }

    @After
    public void tearDown() {
        PlaybackBaseTestDatabase.tearDown();
    }

    @Test
    public void aFreshPlayerIsStoppedAndHoldsNoMedia() {
        assertEquals(PlayerStatus.STOPPED, player.getPlayerStatus());
        assertNull(player.getPlayable());
        assertNull(player.getPSMPInfo().getOldPlayerStatus());
        assertEquals(PlayerStatus.STOPPED, player.getPSMPInfo().getPlayerStatus());
    }

    @Test
    public void startingPlaybackNotifiesTheCallbackOnceWithTheStoredMediaAndPosition() {
        player.enterStatus(PlayerStatus.PLAYING, media, 7000);

        assertEquals(Collections.singletonList("start:" + media.getId() + ":7000"), callback.playbackCalls);
        assertEquals(1, callback.statusChanges.size());
        assertEquals(PlayerStatus.PLAYING, callback.statusChanges.get(0).getPlayerStatus());
        assertEquals(PlayerStatus.STOPPED, callback.statusChanges.get(0).getOldPlayerStatus());
        assertSame(media, callback.statusChanges.get(0).getPlayable());
    }

    @Test
    public void pausingAfterPlayingNotifiesTheCallbackOfThePauseWithItsPosition() {
        player.enterStatus(PlayerStatus.PLAYING, media, 0);
        callback.playbackCalls.clear();

        player.enterStatus(PlayerStatus.PAUSED, media, 12000);

        assertEquals(Collections.singletonList("pause:" + media.getId() + ":12000"), callback.playbackCalls);
        assertEquals(PlayerStatus.PLAYING, player.getPSMPInfo().getOldPlayerStatus());
        assertEquals(PlayerStatus.PAUSED, player.getPSMPInfo().getPlayerStatus());
    }

    @Test
    public void movingBetweenTwoNonPlayingStatesNotifiesNeitherStartNorPause() {
        player.enterStatus(PlayerStatus.PREPARING, media, 0);
        player.enterStatus(PlayerStatus.PREPARED, media, 0);

        assertTrue(callback.playbackCalls.isEmpty());
        assertEquals(2, callback.statusChanges.size());
    }

    @Test
    public void anIndeterminateStateIsReportedButNeverCountsAsStartOrPause() {
        player.enterStatus(PlayerStatus.PLAYING, media, 0);
        callback.playbackCalls.clear();

        player.enterStatus(PlayerStatus.INDETERMINATE, media, 3000);

        assertTrue(callback.playbackCalls.isEmpty());
        assertEquals(PlayerStatus.INDETERMINATE, player.getPlayerStatus());
    }

    @Test
    public void aStatusChangeWithoutMediaIsStillReportedButTriggersNoPlaybackCallback() {
        player.enterStatus(PlayerStatus.PLAYING, media, 0);
        callback.playbackCalls.clear();

        player.enterStatus(PlayerStatus.STOPPED, null, 0);

        assertTrue(callback.playbackCalls.isEmpty());
        assertNull(player.getPlayable());
        assertEquals(PlayerStatus.STOPPED, callback.statusChanges.get(
                callback.statusChanges.size() - 1).getPlayerStatus());
    }

    @Test
    public void resumingFromPausedReportsAStartAgain() {
        player.enterStatus(PlayerStatus.PLAYING, media, 0);
        player.enterStatus(PlayerStatus.PAUSED, media, 5000);
        callback.playbackCalls.clear();

        player.enterStatus(PlayerStatus.PLAYING, media, 5000);

        assertEquals(Collections.singletonList("start:" + media.getId() + ":5000"), callback.playbackCalls);
    }

    @Test
    public void skippingInsideTheFirstSecondOfPlaybackIsIgnored() {
        player.position = 999;

        player.skip();

        assertTrue(player.endPlaybackCalls.isEmpty());
    }

    @Test
    public void skippingAfterTheFirstSecondEndsPlaybackAsAUserSkip() {
        player.position = 1000;

        player.skip();

        assertEquals(Collections.singletonList("false,true,true,true"), player.endPlaybackCalls);
    }

    @Test
    public void stoppingPlaybackEndsItWithoutContinuingToTheNextEpisode() {
        player.stopPlayback(true);
        player.stopPlayback(false);

        assertEquals(Arrays.asList("false,false,false,true", "false,false,false,false"), player.endPlaybackCalls);
    }

    @Test
    public void aWifiLockIsTakenAndReleasedWhenTheMediaPlayerAsksForOne() {
        player.shouldLockWifi = true;

        player.takeWifiLock();
        assertEquals(1, activeWifiLocks());

        player.freeWifiLock();

        assertEquals(0, activeWifiLocks());
    }

    @Test
    public void noWifiLockIsTakenWhenTheMediaPlayerDoesNotAskForOne() {
        player.shouldLockWifi = false;

        player.takeWifiLock();
        player.freeWifiLock();

        assertEquals(0, activeWifiLocks());
    }

    private int activeWifiLocks() {
        return Shadows.shadowOf((WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE)).getActiveLockCount();
    }

    @Test
    public void anIdleAudioSystemIsNotReportedAsInUse() {
        audioManager().setMode(AudioManager.MODE_NORMAL);
        Shadows.shadowOf(audioManager()).setIsMusicActive(false);

        assertFalse(player.isAudioChannelInUse());
    }

    @Test
    public void anotherAppPlayingMusicMeansTheAudioChannelIsInUse() {
        audioManager().setMode(AudioManager.MODE_NORMAL);
        Shadows.shadowOf(audioManager()).setIsMusicActive(true);

        assertTrue(player.isAudioChannelInUse());
    }

    @Test
    public void anOngoingCallMeansTheAudioChannelIsInUse() {
        Shadows.shadowOf(audioManager()).setIsMusicActive(false);
        audioManager().setMode(AudioManager.MODE_IN_CALL);

        assertTrue(player.isAudioChannelInUse());
    }

    private AudioManager audioManager() {
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Test
    public void playingOutranksEveryStatusThatPrecedesItAndIndeterminateOutranksNothing() {
        assertTrue(PlayerStatus.PLAYING.isAtLeast(PlayerStatus.PREPARED));
        assertTrue(PlayerStatus.PLAYING.isAtLeast(PlayerStatus.PLAYING));
        assertTrue(PlayerStatus.PREPARED.isAtLeast(PlayerStatus.INITIALIZED));
        assertFalse(PlayerStatus.PAUSED.isAtLeast(PlayerStatus.PLAYING));
        assertFalse(PlayerStatus.INDETERMINATE.isAtLeast(PlayerStatus.STOPPED));
        assertFalse(PlayerStatus.ERROR.isAtLeast(PlayerStatus.INDETERMINATE));
        assertTrue(PlayerStatus.ERROR.isAtLeast(null));
    }

    private static class RecordingCallback implements PlaybackServiceMediaPlayer.PSMPCallback {
        private final List<String> playbackCalls = new ArrayList<>();
        private final List<PlaybackServiceMediaPlayer.PSMPInfo> statusChanges = new ArrayList<>();

        @Override
        public void statusChanged(PlaybackServiceMediaPlayer.PSMPInfo newInfo) {
            statusChanges.add(newInfo);
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
            playbackCalls.add("start:" + ((FeedMedia) playable).getId() + ":" + position);
        }

        @Override
        public void onPlaybackPause(Playable playable, int position) {
            playbackCalls.add("pause:" + ((FeedMedia) playable).getId() + ":" + position);
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

    private static class TestMediaPlayer extends PlaybackServiceMediaPlayer {
        private final List<String> endPlaybackCalls = new ArrayList<>();
        private Playable playable;
        private int position;
        private boolean shouldLockWifi;

        TestMediaPlayer(@NonNull Context context, @NonNull PSMPCallback callback) {
            super(context, callback);
        }

        void enterStatus(PlayerStatus status, Playable newMedia, int position) {
            setPlayerStatus(status, newMedia, position);
        }

        void takeWifiLock() {
            acquireWifiLockIfNecessary();
        }

        void freeWifiLock() {
            releaseWifiLockIfNecessary();
        }

        @Override
        public void playMediaObject(@NonNull Playable playable, boolean stream,
                                    boolean startWhenPrepared, boolean prepareImmediately) {
        }

        @Override
        public void resume() {
        }

        @Override
        public void pause(boolean abandonFocus, boolean reinit) {
        }

        @Override
        public void prepare() {
        }

        @Override
        public void reinit() {
        }

        @Override
        public void seekTo(int t) {
        }

        @Override
        public void seekDelta(int d) {
        }

        @Override
        public int getDuration() {
            return 0;
        }

        @Override
        public int getPosition() {
            return position;
        }

        @Override
        public boolean isStartWhenPrepared() {
            return false;
        }

        @Override
        public void setStartWhenPrepared(boolean startWhenPrepared) {
        }

        @Override
        public void setPlaybackParams(float speed, boolean skipSilence) {
        }

        @Override
        public float getPlaybackSpeed() {
            return 1;
        }

        @Override
        public boolean getSkipSilence() {
            return false;
        }

        @Override
        public void setVolume(float volumeLeft, float volumeRight) {
        }

        @Override
        public MediaType getCurrentMediaType() {
            return MediaType.AUDIO;
        }

        @Override
        public boolean isStreaming() {
            return false;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void setVideoSurface(SurfaceHolder surface) {
        }

        @Override
        public void resetVideoSurface() {
        }

        @Override
        public Pair<Integer, Integer> getVideoSize() {
            return null;
        }

        @Override
        public Playable getPlayable() {
            return playable;
        }

        @Override
        protected void setPlayable(Playable playable) {
            this.playable = playable;
        }

        @Override
        public List<String> getAudioTracks() {
            return Collections.emptyList();
        }

        @Override
        public void setAudioTrack(int track) {
        }

        @Override
        public int getSelectedAudioTrack() {
            return 0;
        }

        @Override
        protected void endPlayback(boolean hasEnded, boolean wasSkipped,
                                   boolean shouldContinue, boolean toStoppedState) {
            endPlaybackCalls.add(hasEnded + "," + wasSkipped + "," + shouldContinue + "," + toStoppedState);
        }

        @Override
        protected boolean shouldLockWifi() {
            return shouldLockWifi;
        }

        @Override
        public boolean isCasting() {
            return false;
        }
    }
}
