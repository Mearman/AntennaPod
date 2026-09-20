package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.view.SurfaceHolder;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.event.PlayerErrorEvent;
import de.danoeh.antennapod.event.playback.SpeedChangedEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer;
import de.danoeh.antennapod.playback.base.PlayerStatus;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAudioManager;
import org.robolectric.shadows.ShadowWifiManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class LocalPSMPTest {
    private static final String STREAM_URL = "https://example.com/episode.mp3";
    private static final String USERNAME = "user";
    private static final String PASSWORD = "secret";

    private Context context;
    private ExoPlayerWrapper player;
    private PlaybackServiceMediaPlayer.PSMPCallback callback;
    private LocalPSMP psmp;
    private SpeedEventCollector speedEvents;

    public static class SpeedEventCollector {
        private final List<SpeedChangedEvent> events = new ArrayList<>();

        @Subscribe
        public void onSpeedChanged(SpeedChangedEvent event) {
            events.add(event);
        }
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        player = mock(ExoPlayerWrapper.class);
        callback = mock(PlaybackServiceMediaPlayer.PSMPCallback.class);
        when(callback.shouldContinueToNextEpisode()).thenReturn(true);
        psmp = new LocalPSMP(context, callback) {
            @Override
            ExoPlayerWrapper newExoPlayerWrapper() {
                return player;
            }
        };
        speedEvents = new SpeedEventCollector();
        EventBus.getDefault().register(speedEvents);
        PlaybackService.isRunning = true;
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(speedEvents);
        PlaybackService.isRunning = false;
        EventBus.getDefault().removeAllStickyEvents();
    }

    private static FeedPreferences preferences(VolumeAdaptionSetting volumeAdaption) {
        return new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, volumeAdaption,
                FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, USERNAME, PASSWORD);
    }

    private static FeedMedia feedMedia(long id, String mimeType, String localFileUrl,
                                       int duration, int position, VolumeAdaptionSetting volumeAdaption) {
        Feed feed = new Feed("https://example.com/feed.xml", null, "Feed");
        feed.setPreferences(preferences(volumeAdaption));
        FeedItem item = new FeedItem(id, "Episode", "guid", "https://example.com", new Date(), FeedItem.PLAYED, feed);
        FeedMedia media = new FeedMedia(id, item, duration, position, 1024, mimeType,
                localFileUrl, STREAM_URL, 0, null, 0, 0);
        item.setMedia(media);
        return media;
    }

    private static FeedMedia audioMedia(long id) {
        return feedMedia(id, "audio/mpeg", null, 0, 0, VolumeAdaptionSetting.OFF);
    }

    private static String readableLocalFile() {
        return Objects.requireNonNull(
                LocalPSMPTest.class.getResource("/local-episode.mp3")).getPath();
    }

    private void playStreaming(Playable media, boolean startWhenPrepared, boolean prepareImmediately) {
        psmp.playMediaObject(media, true, startWhenPrepared, prepareImmediately);
    }

    @Test
    public void newPlayerIsStoppedAndHoldsNoMedia() {
        assertEquals(PlayerStatus.STOPPED, psmp.getPlayerStatus());
        assertNull(psmp.getPlayable());
        assertEquals(MediaType.UNKNOWN, psmp.getCurrentMediaType());
        assertFalse(psmp.isStreaming());
        assertFalse(psmp.isCasting());
    }

    @Test
    public void streamingFeedMediaPassesFeedCredentialsToThePlayer() {
        playStreaming(audioMedia(1), false, false);

        verify(player).setDataSource(STREAM_URL, USERNAME, PASSWORD);
        assertEquals(PlayerStatus.INITIALIZED, psmp.getPlayerStatus());
        assertTrue(psmp.isStreaming());
    }

    @Test
    public void streamingPlayableWithoutFeedUsesThePlainStreamUrl() {
        Playable playable = mock(Playable.class);
        when(playable.getIdentifier()).thenReturn("id");
        when(playable.getStreamUrl()).thenReturn(STREAM_URL);
        when(playable.getMediaType()).thenReturn(MediaType.AUDIO);

        playStreaming(playable, false, false);

        verify(player).setDataSource(STREAM_URL);
        verify(player, never()).setDataSource(any(), any(), any());
    }

    @Test
    public void localPlaybackReadsTheFileFromDisk() {
        FeedMedia media = feedMedia(1, "audio/mpeg", readableLocalFile(), 0, 0, VolumeAdaptionSetting.OFF);

        psmp.playMediaObject(media, false, false, false);

        verify(player).setDataSource(readableLocalFile());
        assertFalse(psmp.isStreaming());
        assertEquals(PlayerStatus.INITIALIZED, psmp.getPlayerStatus());
    }

    @Test
    public void unreadableLocalFileFailsIntoErrorStateAndReportsTheError() {
        FeedMedia media = feedMedia(1, "audio/mpeg", "/does/not/exist.mp3", 0, 0, VolumeAdaptionSetting.OFF);

        psmp.playMediaObject(media, false, false, false);

        assertEquals(PlayerStatus.ERROR, psmp.getPlayerStatus());
        assertNotNull(EventBus.getDefault().getStickyEvent(PlayerErrorEvent.class));
        verify(player, never()).prepare();
    }

    @Test
    public void preparingImmediatelyLeavesThePlayerPrepared() {
        playStreaming(audioMedia(1), false, true);

        verify(player).prepare();
        verify(player, never()).start();
        assertEquals(PlayerStatus.PREPARED, psmp.getPlayerStatus());
    }

    @Test
    public void startingWhenPreparedBeginsPlayback() {
        playStreaming(audioMedia(1), true, true);

        verify(player).start();
        assertEquals(PlayerStatus.PLAYING, psmp.getPlayerStatus());
    }

    @Test
    public void mediaTypeFollowsTheMimeTypeOfThePlayable() {
        playStreaming(feedMedia(1, "video/mp4", null, 0, 0, VolumeAdaptionSetting.OFF), false, false);

        assertEquals(MediaType.VIDEO, psmp.getCurrentMediaType());
    }

    @Test
    public void durationIsTakenFromThePlayerWhenTheMediaHasNone() {
        when(player.getDuration()).thenReturn(123456);
        FeedMedia media = audioMedia(1);

        playStreaming(media, false, true);

        assertEquals(123456, media.getDuration());
    }

    @Test
    public void durationOfTheMediaIsKeptWhenAlreadyKnown() {
        when(player.getDuration()).thenReturn(123456);
        FeedMedia media = feedMedia(1, "audio/mpeg", null, 42, 0, VolumeAdaptionSetting.OFF);

        playStreaming(media, false, true);

        assertEquals(42, media.getDuration());
    }

    @Test
    public void playingTheSameEpisodeAgainWhilePlayingIsIgnored() {
        playStreaming(audioMedia(1), true, true);

        playStreaming(audioMedia(1), true, true);

        verify(player, times(1)).setDataSource(STREAM_URL, USERNAME, PASSWORD);
        assertEquals(PlayerStatus.PLAYING, psmp.getPlayerStatus());
    }

    @Test
    public void switchingToAnotherEpisodeStopsTheCurrentOneAndReportsIt() {
        FeedMedia first = audioMedia(1);
        playStreaming(first, true, true);

        playStreaming(audioMedia(2), false, false);

        verify(player).stop();
        verify(callback).onPostPlayback(eq(first), eq(false), eq(false), eq(true));
        verify(player, times(2)).setDataSource(STREAM_URL, USERNAME, PASSWORD);
    }

    @Test
    public void resumeFromPreparedStartsPlayback() {
        playStreaming(audioMedia(1), false, true);

        psmp.resume();

        verify(player).start();
        assertEquals(PlayerStatus.PLAYING, psmp.getPlayerStatus());
    }

    @Test
    public void resumeIsIgnoredWhileStopped() {
        psmp.resume();

        verify(player, never()).start();
        assertEquals(PlayerStatus.STOPPED, psmp.getPlayerStatus());
    }

    @Test
    public void playbackDoesNotStartWhenAudioFocusIsRefused() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        ShadowAudioManager shadowAudioManager = Shadows.shadowOf(audioManager);
        playStreaming(audioMedia(1), false, true);
        shadowAudioManager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);

        psmp.resume();

        verify(player, never()).start();
        assertEquals(PlayerStatus.PREPARED, psmp.getPlayerStatus());
    }

    @Test
    public void resumingAnEpisodeWithProgressRewindsBeforeStarting() {
        FeedMedia media = feedMedia(1, "audio/mpeg", null, 600000, 300000, VolumeAdaptionSetting.OFF);
        media.setLastPlayedTimeStatistics(System.currentTimeMillis() - 2000);
        when(player.getDuration()).thenReturn(600000);
        playStreaming(media, false, false);
        psmp.prepare();

        psmp.resume();

        verify(player).seekTo(300000 - 800);
    }

    @Test
    public void pauseIsIgnoredWhenNothingIsPlaying() {
        playStreaming(audioMedia(1), false, true);

        psmp.pause(true, false);

        verify(player, never()).pause();
        assertEquals(PlayerStatus.PREPARED, psmp.getPlayerStatus());
    }

    @Test
    public void pausingAStreamWithReinitReloadsTheDataSource() {
        playStreaming(audioMedia(1), true, true);

        psmp.pause(true, true);

        verify(player, times(2)).setDataSource(STREAM_URL, USERNAME, PASSWORD);
    }

    @Test
    public void prepareIsIgnoredUnlessThePlayerIsInitialized() {
        psmp.prepare();

        verify(player, never()).prepare();
        assertEquals(PlayerStatus.STOPPED, psmp.getPlayerStatus());
    }

    @Test
    public void prepareMovesFromInitializedToPrepared() {
        playStreaming(audioMedia(1), false, false);

        psmp.prepare();

        verify(player).prepare();
        assertEquals(PlayerStatus.PREPARED, psmp.getPlayerStatus());
    }

    @Test
    public void seekingToANegativePositionClampsToTheStart() {
        when(player.getDuration()).thenReturn(600000);
        playStreaming(audioMedia(1), false, true);

        psmp.seekTo(-5000);

        verify(player).seekTo(0);
    }

    @Test
    public void seekingPastTheEndFinishesTheEpisode() {
        when(player.getDuration()).thenReturn(600000);
        playStreaming(audioMedia(1), false, true);

        psmp.seekTo(600000);

        verify(callback).episodeFinishedPlayback();
        verify(player, never()).seekTo(600000);
    }

    @Test
    public void seekingWhileInitializedStoresThePositionAndPrepares() {
        FeedMedia media = feedMedia(1, "audio/mpeg", null, 600000, 0, VolumeAdaptionSetting.OFF);
        playStreaming(media, true, false);

        psmp.seekTo(42000);

        assertEquals(42000, media.getPosition());
        verify(player).prepare();
        assertFalse(psmp.isStartWhenPrepared());
    }

    @Test
    public void seekingWhilePreparedStoresThePositionOnTheMedia() {
        when(player.getDuration()).thenReturn(600000);
        FeedMedia media = audioMedia(1);
        playStreaming(media, false, true);

        psmp.seekTo(42000);

        verify(player).seekTo(42000);
        assertEquals(42000, media.getPosition());
    }

    @Test
    public void seekDeltaIsRelativeToTheCurrentPosition() {
        when(player.getDuration()).thenReturn(600000);
        when(player.getCurrentPosition()).thenReturn(10000);
        playStreaming(audioMedia(1), false, true);

        psmp.seekDelta(5000);

        verify(player).seekTo(15000);
    }

    @Test
    public void durationFallsBackToTheMediaWhenThePlayerReportsNone() {
        when(player.getDuration()).thenReturn(0);
        FeedMedia media = feedMedia(1, "audio/mpeg", null, 4711, 0, VolumeAdaptionSetting.OFF);
        playStreaming(media, false, true);

        assertEquals(4711, psmp.getDuration());
    }

    @Test
    public void durationIsUnknownWhileThePlayerIsNotReady() {
        assertEquals(Playable.INVALID_TIME, psmp.getDuration());
    }

    @Test
    public void positionFallsBackToTheMediaWhenThePlayerReportsNone() {
        when(player.getCurrentPosition()).thenReturn(0);
        FeedMedia media = feedMedia(1, "audio/mpeg", null, 600000, 4711, VolumeAdaptionSetting.OFF);
        when(player.getDuration()).thenReturn(600000);
        playStreaming(media, false, true);

        assertEquals(4711, psmp.getPosition());
    }

    @Test
    public void playbackSpeedIsOnlyReadFromThePlayerWhileItIsReady() {
        when(player.getCurrentSpeedMultiplier()).thenReturn(2.5f);

        assertEquals(1.0f, psmp.getPlaybackSpeed(), 0.001f);

        playStreaming(audioMedia(1), false, true);

        assertEquals(2.5f, psmp.getPlaybackSpeed(), 0.001f);
    }

    @Test
    public void skipSilenceIsOnlyReadFromThePlayerWhileItIsReady() {
        when(player.getCurrentSkipSilence()).thenReturn(true);

        assertFalse(psmp.getSkipSilence());

        playStreaming(audioMedia(1), false, true);

        assertTrue(psmp.getSkipSilence());
    }

    @Test
    public void volumeIsScaledByTheFeedVolumeAdaption() {
        playStreaming(feedMedia(1, "audio/mpeg", null, 0, 0, VolumeAdaptionSetting.LIGHT_REDUCTION), false, false);

        psmp.setVolume(1.0f, 1.0f);

        verify(player).setVolume(0.5f, 0.5f);
    }

    @Test
    public void volumeIsPassedThroughForPlayablesWithoutAFeed() {
        Playable playable = mock(Playable.class);
        when(playable.getIdentifier()).thenReturn("id");
        when(playable.getStreamUrl()).thenReturn(STREAM_URL);
        when(playable.getMediaType()).thenReturn(MediaType.AUDIO);
        playStreaming(playable, false, false);

        psmp.setVolume(0.75f, 0.75f);

        verify(player).setVolume(0.75f, 0.75f);
    }

    @Test
    public void audioTracksAreOnlyAvailableOnceAPlayerExists() {
        List<String> tracks = Arrays.asList("English", "German");
        when(player.getAudioTracks()).thenReturn(tracks);
        when(player.getSelectedAudioTrack()).thenReturn(1);

        assertTrue(psmp.getAudioTracks().isEmpty());
        assertEquals(-1, psmp.getSelectedAudioTrack());

        playStreaming(audioMedia(1), false, false);
        psmp.setAudioTrack(1);

        verify(player).setAudioTrack(1);
        assertEquals(tracks, psmp.getAudioTracks());
        assertEquals(1, psmp.getSelectedAudioTrack());
    }

    @Test
    public void videoSizeIsUnknownForAudioMedia() {
        playStreaming(audioMedia(1), false, false);

        assertNull(psmp.getVideoSize());
    }

    @Test
    public void resettingTheVideoSurfaceDetachesItAndReloadsTheMedia() {
        playStreaming(feedMedia(1, "video/mp4", null, 0, 0, VolumeAdaptionSetting.OFF), false, false);

        psmp.resetVideoSurface();

        verify(player).setDisplay(null);
        verify(player, times(2)).setDataSource(STREAM_URL, USERNAME, PASSWORD);
    }

    @Test
    public void resettingTheVideoSurfaceIsIgnoredForAudioMedia() {
        playStreaming(audioMedia(1), false, false);

        psmp.resetVideoSurface();

        verify(player, never()).setDisplay(any());
    }

    @Test
    public void settingStartWhenPreparedMakesThePreparedPlayerStart() {
        playStreaming(audioMedia(1), false, false);
        psmp.setStartWhenPrepared(true);

        psmp.prepare();

        assertTrue(psmp.isStartWhenPrepared());
        verify(player).start();
        assertEquals(PlayerStatus.PLAYING, psmp.getPlayerStatus());
    }

    @Test
    public void shutdownReleasesThePlayerAndStopsPlayback() {
        when(player.isPlaying()).thenReturn(true);
        playStreaming(audioMedia(1), true, true);

        psmp.shutdown();

        verify(player).stop();
        verify(player).release();
        assertEquals(PlayerStatus.STOPPED, psmp.getPlayerStatus());
    }

    @Test
    public void skippingInTheFirstSecondOfPlaybackIsIgnored() {
        when(player.getCurrentPosition()).thenReturn(500);
        when(player.getDuration()).thenReturn(600000);
        playStreaming(audioMedia(1), true, true);

        psmp.skip();

        verify(callback, never()).episodeFinishedPlayback();
    }

    @Test
    public void skippingWithoutAFollowingEpisodeStopsPlayback() {
        when(player.getCurrentPosition()).thenReturn(30000);
        when(player.getDuration()).thenReturn(600000);
        playStreaming(audioMedia(1), true, true);

        psmp.skip();

        verify(callback).episodeFinishedPlayback();
        verify(callback).onPlaybackEnded(null, true);
        verify(player).reset();
    }

    @Test
    public void finishingAnEpisodeContinuesWithTheNextOneInTheQueue() {
        when(player.getCurrentPosition()).thenReturn(30000);
        when(player.getDuration()).thenReturn(600000);
        FeedMedia next = audioMedia(2);
        when(callback.getNextInQueue(any())).thenReturn(next);
        playStreaming(audioMedia(1), true, true);

        psmp.skip();

        verify(callback).onPlaybackEnded(MediaType.AUDIO, false);
        assertEquals(next, psmp.getPlayable());
    }

    @Test
    public void stoppingPlaybackKeepsTheCurrentPositionOnTheMedia() {
        when(player.getCurrentPosition()).thenReturn(30000);
        when(player.getDuration()).thenReturn(600000);
        FeedMedia media = audioMedia(1);
        playStreaming(media, true, true);

        psmp.stopPlayback(true);

        assertEquals(30000, media.getPosition());
        verify(player).reset();
    }

    @Test
    public void losingAudioFocusPausesPlaybackAndAsksTheServiceToStop() {
        playStreaming(audioMedia(1), true, true);
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioManager.OnAudioFocusChangeListener listener =
                Shadows.shadowOf(audioManager).getLastAudioFocusRequest().listener;

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);

        verify(player).pause();
        verify(callback).shouldStop();
        assertEquals(PlayerStatus.PAUSED, psmp.getPlayerStatus());
    }

    @Test
    public void audioFocusChangesAreIgnoredAfterShutdown() {
        playStreaming(audioMedia(1), true, true);
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioManager.OnAudioFocusChangeListener listener =
                Shadows.shadowOf(audioManager).getLastAudioFocusRequest().listener;
        psmp.shutdown();

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);

        verify(callback, never()).shouldStop();
    }

    @Test
    public void playerInfoReportsTheStatusAndMediaTogether() {
        FeedMedia media = audioMedia(1);
        playStreaming(media, false, true);

        PlaybackServiceMediaPlayer.PSMPInfo info = psmp.getPSMPInfo();

        assertEquals(PlayerStatus.PREPARED, info.getPlayerStatus());
        assertEquals(PlayerStatus.PREPARING, info.getOldPlayerStatus());
        assertEquals(media, info.getPlayable());
    }

    @Test
    public void reinitReloadsTheCurrentMediaWithoutPreparingIt() {
        playStreaming(audioMedia(1), false, true);

        psmp.reinit();

        verify(player, times(2)).setDataSource(STREAM_URL, USERNAME, PASSWORD);
        verify(player, times(1)).prepare();
        assertEquals(PlayerStatus.INITIALIZED, psmp.getPlayerStatus());
    }

    @Test
    public void settingPlaybackParametersAnnouncesTheNewSpeed() {
        playStreaming(audioMedia(1), false, false);
        speedEvents.events.clear();

        psmp.setPlaybackParams(1.75f, true);

        verify(player).setPlaybackParams(1.75f, true);
        assertEquals(1, speedEvents.events.size());
        assertEquals(1.75f, speedEvents.events.get(0).getNewSpeed(), 0.001f);
    }

    @Test
    public void setVideoSurfaceIsIgnoredWhileNoPlayerExists() {
        psmp.setVideoSurface(null);

        verify(player, never()).setDisplay(any());
    }

    @Test
    public void statusChangesAreReportedToTheCallback() {
        ArgumentCaptor<PlaybackServiceMediaPlayer.PSMPInfo> captor =
                ArgumentCaptor.forClass(PlaybackServiceMediaPlayer.PSMPInfo.class);
        FeedMedia media = audioMedia(1);

        playStreaming(media, false, false);

        verify(callback, times(2)).statusChanged(captor.capture());
        assertEquals(PlayerStatus.INITIALIZING, captor.getAllValues().get(0).getPlayerStatus());
        assertEquals(PlayerStatus.INITIALIZED, captor.getAllValues().get(1).getPlayerStatus());
        assertEquals(media, captor.getAllValues().get(1).getPlayable());
        verify(callback).onMediaChanged(false);
        verify(callback).ensureMediaInfoLoaded(media);
    }

    @Test
    public void resumeIsIgnoredWhileAlreadyPlaying() {
        playStreaming(audioMedia(1), true, true);

        psmp.resume();

        verify(player, times(1)).start();
        assertEquals(PlayerStatus.PLAYING, psmp.getPlayerStatus());
    }

    @Test
    public void seekingWithoutLoadedMediaEndsPlaybackInsteadOfSeeking() {
        psmp.seekTo(1000);

        verify(player, never()).seekTo(anyInt());
        verify(callback).onPlaybackEnded(null, true);
    }

    @Test
    public void pausingWithoutReinitKeepsTheLoadedDataSource() {
        playStreaming(audioMedia(1), true, true);

        psmp.pause(true, false);

        verify(player, times(1)).setDataSource(STREAM_URL, USERNAME, PASSWORD);
        verify(player, never()).reset();
    }

    @Test
    public void aWifiLockIsOnlyHeldWhileStreaming() {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        ShadowWifiManager shadowWifiManager = Shadows.shadowOf(wifiManager);
        FeedMedia local = feedMedia(1, "audio/mpeg", readableLocalFile(), 600000, 0, VolumeAdaptionSetting.OFF);
        when(player.getDuration()).thenReturn(600000);

        psmp.playMediaObject(local, false, true, true);

        assertFalse(psmp.isStreaming());
        assertEquals(0, shadowWifiManager.getActiveLockCount());

        playStreaming(audioMedia(2), true, true);

        assertTrue(psmp.isStreaming());
        assertEquals(1, shadowWifiManager.getActiveLockCount());
    }

    @Test
    public void startingPlaybackIsReportedToTheCallbackOnceWithoutAPosition() {
        when(player.getDuration()).thenReturn(600000);
        FeedMedia media = audioMedia(1);

        playStreaming(media, true, true);

        verify(callback, times(1)).onPlaybackStart(eq(media), eq(Playable.INVALID_TIME));
    }

    @Test
    public void playbackPauseIsReportedWithThePositionItStoppedAt() {
        when(player.getCurrentPosition()).thenReturn(12000);
        when(player.getDuration()).thenReturn(600000);
        FeedMedia media = audioMedia(1);
        playStreaming(media, true, true);

        psmp.pause(false, false);

        verify(player).pause();
        verify(callback).onPlaybackPause(eq(media), eq(12000));
        assertEquals(PlayerStatus.PAUSED, psmp.getPlayerStatus());
    }

    @Test
    public void playerIsNotStartedWhenPreparationIsDeferred() {
        playStreaming(audioMedia(1), true, false);

        verify(player, never()).prepare();
        verify(player, never()).start();
        assertTrue(psmp.isStartWhenPrepared());
    }

    @Test
    public void switchingEpisodesReleasesTheOldPlayerAndConfiguresTheNewOneForMusic() {
        playStreaming(audioMedia(1), false, false);

        playStreaming(audioMedia(2), false, false);

        verify(player).release();
        verify(player, times(2)).setAudioStreamType(AudioManager.STREAM_MUSIC);
    }

    @Test
    public void anEpisodeThatEndsOnItsOwnIsNotTreatedAsSkipped() {
        when(player.getCurrentPosition()).thenReturn(30000);
        when(player.getDuration()).thenReturn(600000);
        FeedMedia media = audioMedia(1);
        playStreaming(media, true, true);

        psmp.stopPlayback(true);

        verify(callback).onPostPlayback(eq(media), eq(false), eq(false), eq(false));
    }

    @Test
    public void notContinuingAfterAnEpisodeSkipsTheQueueLookup() {
        when(player.getCurrentPosition()).thenReturn(30000);
        when(player.getDuration()).thenReturn(600000);
        when(callback.shouldContinueToNextEpisode()).thenReturn(false);
        playStreaming(audioMedia(1), true, true);

        psmp.skip();

        verify(callback, never()).getNextInQueue(any());
        verify(callback).onPlaybackEnded(null, true);
    }

    @Test
    public void volumeAdaptionOfTheNewFeedAppliesAfterSwitchingEpisodes() {
        playStreaming(audioMedia(1), false, false);
        playStreaming(feedMedia(2, "audio/mpeg", null, 0, 0, VolumeAdaptionSetting.HEAVY_REDUCTION), false, false);

        psmp.setVolume(1.0f, 1.0f);

        verify(player).setVolume(0.2f, 0.2f);
    }

    @Test
    public void theVideoSizeIsMeasuredWhenVideoMediaIsPrepared() {
        when(player.getVideoWidth()).thenReturn(1280);
        when(player.getVideoHeight()).thenReturn(720);

        playStreaming(feedMedia(1, "video/mp4", null, 0, 0, VolumeAdaptionSetting.OFF), false, true);

        assertEquals(Integer.valueOf(1280), psmp.getVideoSize().first);
        assertEquals(Integer.valueOf(720), psmp.getVideoSize().second);
    }

    @Test
    public void reinitWithoutAnyMediaIsIgnored() {
        psmp.reinit();

        verify(player, never()).reset();
        assertEquals(PlayerStatus.STOPPED, psmp.getPlayerStatus());
    }

    @Test
    public void theVideoSurfaceIsHandedToThePlayer() {
        SurfaceHolder surface = mock(SurfaceHolder.class);
        playStreaming(feedMedia(1, "video/mp4", null, 0, 0, VolumeAdaptionSetting.OFF), false, false);

        psmp.setVideoSurface(surface);

        verify(player).setDisplay(surface);
    }

    @Test
    public void duckingLowersTheVolumeAndRegainingFocusRestoresIt() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, false).commit();
        playStreaming(audioMedia(1), true, true);
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioManager.OnAudioFocusChangeListener listener =
                Shadows.shadowOf(audioManager).getLastAudioFocusRequest().listener;

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        InOrder inOrder = inOrder(player);
        inOrder.verify(player).setVolume(0.25f, 0.25f);
        inOrder.verify(player).setVolume(1.0f, 1.0f);
        verify(player, never()).pause();
        assertEquals(PlayerStatus.PLAYING, psmp.getPlayerStatus());
    }

    @Test
    public void stoppingWithoutEnteringTheStoppedStateReportsAPauseInstead() {
        when(player.getCurrentPosition()).thenReturn(30000);
        when(player.getDuration()).thenReturn(600000);
        when(callback.shouldContinueToNextEpisode()).thenReturn(false);
        FeedMedia media = audioMedia(1);
        playStreaming(media, true, true);

        psmp.stopPlayback(false);

        verify(callback, never()).onPlaybackEnded(any(), anyBoolean());
        verify(callback).onPlaybackPause(eq(media), eq(30000));
    }

    @Test
    public void seekDeltaWithoutAKnownPositionDoesNotSeek() {
        Playable playable = mock(Playable.class);
        when(playable.getIdentifier()).thenReturn("id");
        when(playable.getStreamUrl()).thenReturn(STREAM_URL);
        when(playable.getMediaType()).thenReturn(MediaType.AUDIO);
        when(playable.getPosition()).thenReturn(Playable.INVALID_TIME);
        playStreaming(playable, false, false);

        psmp.seekDelta(5000);

        verify(player, never()).seekTo(anyInt());
    }

    @Test
    public void mediaWithoutDurationEndsPlaybackWhenResumingFromAStoredPosition() {
        FeedMedia media = feedMedia(1, "audio/mpeg", null, 0, 5000, VolumeAdaptionSetting.OFF);
        when(player.getDuration()).thenReturn(0);

        playStreaming(media, false, true);

        verify(callback).episodeFinishedPlayback();
    }
}
