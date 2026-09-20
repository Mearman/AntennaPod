package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.media.AudioManager;
import androidx.core.util.Consumer;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.event.PlayerErrorEvent;
import de.danoeh.antennapod.event.playback.BufferUpdateEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class LocalPSMPListenerTest {
    private static final String STREAM_URL = "https://example.com/episode.mp3";

    private Context context;
    private ExoPlayerWrapper player;
    private PlaybackServiceMediaPlayer.PSMPCallback callback;
    private LocalPSMP psmp;
    private BufferEventCollector collector;

    public static class BufferEventCollector {
        private final List<BufferUpdateEvent> events = new ArrayList<>();

        @Subscribe
        public void onBufferUpdate(BufferUpdateEvent event) {
            events.add(event);
        }
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        player = mock(ExoPlayerWrapper.class);
        when(player.getDuration()).thenReturn(600000);
        callback = mock(PlaybackServiceMediaPlayer.PSMPCallback.class);
        when(callback.shouldContinueToNextEpisode()).thenReturn(true);
        psmp = new LocalPSMP(context, callback) {
            @Override
            ExoPlayerWrapper newExoPlayerWrapper() {
                return player;
            }
        };
        collector = new BufferEventCollector();
        EventBus.getDefault().register(collector);
        PlaybackService.isRunning = true;
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(collector);
        EventBus.getDefault().removeAllStickyEvents();
        PlaybackService.isRunning = false;
    }

    private FeedMedia audioMedia(long id) {
        Feed feed = new Feed("https://example.com/feed.xml", null, "Feed");
        feed.setPreferences(new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, null, null));
        FeedItem item = new FeedItem(id, "Episode", "guid", "https://example.com", new Date(),
                FeedItem.PLAYED, feed);
        FeedMedia media = new FeedMedia(id, item, 600000, 0, 1024, "audio/mpeg",
                null, STREAM_URL, 0, null, 0, 0);
        item.setMedia(media);
        return media;
    }

    private void play(boolean startWhenPrepared) {
        psmp.playMediaObject(audioMedia(1), true, startWhenPrepared, true);
    }

    private Consumer<Integer> bufferingListener() {
        ArgumentCaptor<Consumer<Integer>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(player).setOnBufferingUpdateListener(captor.capture());
        return captor.getValue();
    }

    private Runnable completionListener() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(player).setOnCompletionListener(captor.capture());
        return captor.getValue();
    }

    private Runnable seekCompleteListener() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(player).setOnSeekCompleteListener(captor.capture());
        return captor.getValue();
    }

    private Consumer<String> errorListener() {
        ArgumentCaptor<Consumer<String>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(player).setOnErrorListener(captor.capture());
        return captor.getValue();
    }

    private AudioManager.OnAudioFocusChangeListener focusListener() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return Shadows.shadowOf(audioManager).getLastAudioFocusRequest().listener;
    }

    @Test
    public void theStartOfBufferingIsAnnouncedOnTheEventBus() {
        play(false);

        bufferingListener().accept(ExoPlayerWrapper.BUFFERING_STARTED);

        assertEquals(1, collector.events.size());
        assertTrue(collector.events.get(0).hasStarted());
    }

    @Test
    public void theEndOfBufferingIsAnnouncedOnTheEventBus() {
        play(false);

        bufferingListener().accept(ExoPlayerWrapper.BUFFERING_ENDED);

        assertTrue(collector.events.get(0).hasEnded());
    }

    @Test
    public void aBufferedPercentageIsAnnouncedAsAFraction() {
        play(false);

        bufferingListener().accept(50);

        assertEquals(0.5f, collector.events.get(0).getProgress(), 0.001f);
    }

    @Test
    public void reachingTheEndOfAnEpisodeFinishesPlayback() {
        play(true);

        completionListener().run();

        verify(callback).episodeFinishedPlayback();
        verify(callback).onPlaybackEnded(null, true);
    }

    @Test
    public void aPlayerErrorIsPublishedAsAStickyEvent() {
        play(false);

        errorListener().accept("Connection reset");

        PlayerErrorEvent event = EventBus.getDefault().getStickyEvent(PlayerErrorEvent.class);
        assertNotNull(event);
        assertEquals("Connection reset", event.getMessage());
    }

    @Test
    public void completingASeekRestoresTheStatusFromBeforeTheSeek() {
        play(false);
        Runnable seekComplete = seekCompleteListener();
        when(player.getCurrentPosition()).thenReturn(42000);

        psmp.seekTo(42000);
        seekComplete.run();

        assertEquals(PlayerStatus.PREPARED, psmp.getPlayerStatus());
    }

    @Test
    public void completingASeekWhilePlayingReportsTheNewPosition() {
        play(true);
        when(player.getCurrentPosition()).thenReturn(42000);

        seekCompleteListener().run();

        verify(callback).onPlaybackStart(any(), eq(42000));
    }

    @Test
    public void listenersAreDetachedBeforeThePlayerIsReleased() {
        play(true);

        psmp.shutdown();

        verify(player, times(2)).setOnCompletionListener(any());
        verify(player, times(2)).setOnErrorListener(any());
        verify(player).release();
    }

    @Test
    public void aTransientFocusLossPausesThePlayerWithoutTellingTheService() {
        play(true);

        focusListener().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        verify(player).pause();
        verify(callback, never()).shouldStop();
        assertEquals(PlayerStatus.PLAYING, psmp.getPlayerStatus());
    }

    @Test
    public void aTransientFocusLossThatIsNeverReturnedPausesForReal() {
        play(true);
        focusListener().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(PlayerStatus.PAUSED, psmp.getPlayerStatus());
    }

    @Test
    public void regainingFocusAfterATransientLossResumesTheSamePlayer() {
        play(true);
        focusListener().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        focusListener().onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        verify(player, times(2)).start();
        assertEquals(PlayerStatus.PLAYING, psmp.getPlayerStatus());
    }

    @Test
    public void focusChangesAreIgnoredWhenTheServiceIsNotRunning() {
        play(true);
        PlaybackService.isRunning = false;

        focusListener().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);

        verify(player, never()).pause();
        verify(callback, never()).shouldStop();
    }
}
