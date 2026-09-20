package de.danoeh.antennapod.playback.base;

import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.util.Pair;
import android.view.SurfaceHolder;
import androidx.annotation.NonNull;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.Playable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAudioManager;
import org.robolectric.shadows.ShadowWifiManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceMediaPlayerTest {
    private RecordingCallback callback;
    private FakeMediaPlayer player;
    private Playable playable;

    @Before
    public void setUp() {
        callback = new RecordingCallback();
        player = new FakeMediaPlayer(RuntimeEnvironment.getApplication(), callback);
        playable = new FeedMedia(1, null, 0, 0, 0, "audio/mpeg", null, "https://example.com/a.mp3", 0, null, 0, 0);
    }

    @Test
    public void newPlayerIsStopped() {
        assertEquals(PlayerStatus.STOPPED, player.getPlayerStatus());
    }

    @Test
    public void changingStatusStoresOldAndNewStatusAndPlayable() {
        player.changeStatus(PlayerStatus.PREPARED, playable, Playable.INVALID_TIME);
        PlaybackServiceMediaPlayer.PSMPInfo info = player.getPSMPInfo();
        assertEquals(PlayerStatus.STOPPED, info.getOldPlayerStatus());
        assertEquals(PlayerStatus.PREPARED, info.getPlayerStatus());
        assertSame(playable, info.getPlayable());
    }

    @Test
    public void everyStatusChangeIsReportedToCallbackWithMatchingInfo() {
        player.changeStatus(PlayerStatus.PREPARED, playable, Playable.INVALID_TIME);
        assertEquals(1, callback.infos.size());
        assertEquals(PlayerStatus.STOPPED, callback.infos.get(0).getOldPlayerStatus());
        assertEquals(PlayerStatus.PREPARED, callback.infos.get(0).getPlayerStatus());
        assertSame(playable, callback.infos.get(0).getPlayable());
    }

    @Test
    public void sameStatusIsReportedAgain() {
        player.changeStatus(PlayerStatus.PAUSED, playable, Playable.INVALID_TIME);
        player.changeStatus(PlayerStatus.PAUSED, playable, Playable.INVALID_TIME);
        assertEquals(2, callback.infos.size());
        assertEquals(PlayerStatus.PAUSED, callback.infos.get(1).getOldPlayerStatus());
    }

    @Test
    public void enteringPlayingReportsPlaybackStartWithPosition() {
        player.changeStatus(PlayerStatus.PREPARED, playable, Playable.INVALID_TIME);
        player.changeStatus(PlayerStatus.PLAYING, playable, 1234);
        assertEquals(1, callback.startedPositions.size());
        assertEquals(1234, (int) callback.startedPositions.get(0));
        assertTrue(callback.pausedPositions.isEmpty());
    }

    @Test
    public void leavingPlayingReportsPlaybackPauseWithPosition() {
        player.changeStatus(PlayerStatus.PLAYING, playable, 0);
        player.changeStatus(PlayerStatus.PAUSED, playable, 5678);
        assertEquals(1, callback.pausedPositions.size());
        assertEquals(5678, (int) callback.pausedPositions.get(0));
        assertEquals(1, callback.startedPositions.size());
    }

    @Test
    public void statusChangesBetweenNonPlayingStatesReportNeitherStartNorPause() {
        player.changeStatus(PlayerStatus.PREPARING, playable, Playable.INVALID_TIME);
        player.changeStatus(PlayerStatus.PREPARED, playable, Playable.INVALID_TIME);
        assertTrue(callback.startedPositions.isEmpty());
        assertTrue(callback.pausedPositions.isEmpty());
    }

    @Test
    public void indeterminateStatusReportsNoPlaybackPause() {
        player.changeStatus(PlayerStatus.PLAYING, playable, 0);
        player.changeStatus(PlayerStatus.INDETERMINATE, playable, 100);
        assertTrue(callback.pausedPositions.isEmpty());
    }

    @Test
    public void statusChangeWithoutMediaReportsNoPlaybackEvents() {
        player.changeStatus(PlayerStatus.PLAYING, null, 0);
        player.changeStatus(PlayerStatus.PAUSED, null, 0);
        assertTrue(callback.startedPositions.isEmpty());
        assertTrue(callback.pausedPositions.isEmpty());
        assertEquals(2, callback.infos.size());
        assertNull(player.getPSMPInfo().getPlayable());
    }

    @Test
    public void statusChangeWithoutPositionUsesInvalidTime() {
        player.changeStatus(PlayerStatus.PLAYING, playable);
        assertEquals(Playable.INVALID_TIME, (int) callback.startedPositions.get(0));
    }

    @Test
    public void skipInFirstSecondOfPlaybackIsIgnored() {
        player.position = 999;
        player.skip();
        assertTrue(player.endPlaybackCalls.isEmpty());
    }

    @Test
    public void skipAfterFirstSecondEndsPlaybackAndContinuesToNextItem() {
        player.position = 1000;
        player.skip();
        assertEquals(1, player.endPlaybackCalls.size());
        assertEquals(List.of(false, true, true, true), player.endPlaybackCalls.get(0));
    }

    @Test
    public void stoppingPlaybackEndsItWithoutContinuing() {
        player.stopPlayback(true);
        player.stopPlayback(false);
        assertEquals(List.of(false, false, false, true), player.endPlaybackCalls.get(0));
        assertEquals(List.of(false, false, false, false), player.endPlaybackCalls.get(1));
    }

    @Test
    public void wifiLockIsHeldOnlyWhileRequiredAndReleased() {
        player.lockWifi = true;
        player.acquireLock();
        WifiManager wifiManager = (WifiManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.WIFI_SERVICE);
        ShadowWifiManager shadowWifiManager = shadowOf(wifiManager);
        assertTrue(shadowWifiManager.getActiveLockCount() > 0);
        player.releaseLock();
        assertEquals(0, shadowWifiManager.getActiveLockCount());
    }

    @Test
    public void wifiLockIsNotAcquiredWhenNotRequired() {
        player.lockWifi = false;
        player.acquireLock();
        WifiManager wifiManager = (WifiManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.WIFI_SERVICE);
        assertEquals(0, shadowOf(wifiManager).getActiveLockCount());
    }

    @Test
    public void releasingWithoutAcquiringDoesNothing() {
        player.releaseLock();
        WifiManager wifiManager = (WifiManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.WIFI_SERVICE);
        assertEquals(0, shadowOf(wifiManager).getActiveLockCount());
    }

    @Test
    public void audioChannelIsNotInUseInNormalModeWithoutMusic() {
        assertFalse(player.isAudioChannelInUse());
    }

    @Test
    public void audioChannelIsInUseDuringCall() {
        AudioManager audioManager = (AudioManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        assertTrue(player.isAudioChannelInUse());
    }

    @Test
    public void audioChannelIsInUseWhileMusicIsActive() {
        AudioManager audioManager = (AudioManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.AUDIO_SERVICE);
        ShadowAudioManager shadowAudioManager = shadowOf(audioManager);
        shadowAudioManager.setIsMusicActive(true);
        assertTrue(player.isAudioChannelInUse());
    }

    private static class RecordingCallback implements PlaybackServiceMediaPlayer.PSMPCallback {
        final List<PlaybackServiceMediaPlayer.PSMPInfo> infos = new ArrayList<>();
        final List<Integer> startedPositions = new ArrayList<>();
        final List<Integer> pausedPositions = new ArrayList<>();

        @Override
        public void statusChanged(PlaybackServiceMediaPlayer.PSMPInfo newInfo) {
            infos.add(newInfo);
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
            startedPositions.add(position);
        }

        @Override
        public void onPlaybackPause(Playable playable, int position) {
            pausedPositions.add(position);
        }

        @Override
        public Playable getNextInQueue(Playable currentMedia) {
            return null;
        }

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

    private static class FakeMediaPlayer extends PlaybackServiceMediaPlayer {
        final List<List<Boolean>> endPlaybackCalls = new ArrayList<>();
        int position;
        boolean lockWifi;
        private Playable current;

        FakeMediaPlayer(Context context, PSMPCallback callback) {
            super(context, callback);
        }

        void changeStatus(PlayerStatus status, Playable media, int newPosition) {
            setPlayerStatus(status, media, newPosition);
        }

        void changeStatus(PlayerStatus status, Playable media) {
            setPlayerStatus(status, media);
        }

        void acquireLock() {
            acquireWifiLockIfNecessary();
        }

        void releaseLock() {
            releaseWifiLockIfNecessary();
        }

        @Override
        protected void endPlayback(boolean hasEnded, boolean wasSkipped, boolean shouldContinue,
                                   boolean toStoppedState) {
            endPlaybackCalls.add(List.of(hasEnded, wasSkipped, shouldContinue, toStoppedState));
        }

        @Override
        protected boolean shouldLockWifi() {
            return lockWifi;
        }

        @Override
        public int getPosition() {
            return position;
        }

        @Override
        public Playable getPlayable() {
            return current;
        }

        @Override
        protected void setPlayable(Playable playable) {
            current = playable;
        }

        @Override
        public void playMediaObject(@NonNull Playable playable, boolean stream, boolean startWhenPrepared,
                                    boolean prepareImmediately) {
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
        public boolean isCasting() {
            return false;
        }
    }
}
