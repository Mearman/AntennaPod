package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.playback.Playable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ExoPlayerWrapperTest {
    private static final String STREAM_URL = "https://example.com/episode.mp3";

    private Context context;
    private ExoPlayerWrapper wrapper;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        wrapper = new ExoPlayerWrapper(context) {
            @Override
            ExoPlayer newExoPlayer(DefaultTrackSelector selector, LoadControl loadControl) {
                return new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
                        .setTrackSelector(selector)
                        .setLoadControl(loadControl)
                        .setClock(new FakeClock(true))
                        .build();
            }
        };
    }

    @After
    public void tearDown() {
        wrapper.release();
    }

    private String missingFilePath() {
        return context.getCacheDir().getAbsolutePath() + "/missing-episode.mp3";
    }

    private void prepareUntilFailed() throws Exception {
        wrapper.prepare();
        TestPlayerRunHelper.runUntilError(wrapper.getExoPlayer());
    }

    @Test
    public void aFreshPlayerHasNoMediaAndPlaysAtNormalSpeed() {
        assertEquals(Playable.INVALID_TIME, wrapper.getDuration());
        assertEquals(0, wrapper.getCurrentPosition());
        assertEquals(1.0f, wrapper.getCurrentSpeedMultiplier(), 0.001f);
        assertFalse(wrapper.isPlaying());
        assertFalse(wrapper.getCurrentSkipSilence());
    }

    @Test
    public void playbackParametersAreKeptAndAppliedOnStart() {
        wrapper.setPlaybackParams(1.75f, true);

        assertEquals(1.75f, wrapper.getCurrentSpeedMultiplier(), 0.001f);
        assertTrue(wrapper.getCurrentSkipSilence());

        wrapper.start();

        assertEquals(1.75f, wrapper.getExoPlayer().getPlaybackParameters().speed, 0.001f);
    }

    @Test
    public void changingTheSpeedKeepsThePitch() {
        wrapper.setPlaybackParams(2.0f, false);
        wrapper.setPlaybackParams(0.5f, false);

        assertEquals(0.5f, wrapper.getCurrentSpeedMultiplier(), 0.001f);
        assertEquals(1.0f, wrapper.getExoPlayer().getPlaybackParameters().pitch, 0.001f);
        assertFalse(wrapper.getCurrentSkipSilence());
    }

    @Test
    public void startAndPauseToggleThePlayWhenReadyFlag() {
        wrapper.start();
        assertTrue(wrapper.isPlaying());

        wrapper.pause();
        assertFalse(wrapper.isPlaying());
    }

    @Test
    public void volumeBelowOneIsPassedToThePlayerUnchanged() {
        wrapper.setVolume(0.25f, 0.25f);

        assertEquals(0.25f, wrapper.getExoPlayer().getVolume(), 0.001f);
    }

    @Test
    public void volumeAboveOneIsCappedBecauseTheBoostIsAppliedSeparately() {
        wrapper.setVolume(2.5f, 2.5f);

        assertEquals(1.0f, wrapper.getExoPlayer().getVolume(), 0.001f);
    }

    @Test
    public void theAudioStreamTypeChangesOnlyTheContentType() {
        AudioAttributes before = wrapper.getExoPlayer().getAudioAttributes();

        wrapper.setAudioStreamType(C.AUDIO_CONTENT_TYPE_SPEECH);

        AudioAttributes after = wrapper.getExoPlayer().getAudioAttributes();
        assertEquals(C.AUDIO_CONTENT_TYPE_SPEECH, after.contentType);
        assertEquals(before.usage, after.usage);
        assertEquals(before.flags, after.flags);
    }

    @Test
    public void seekingReportsSeekCompletionToTheListener() {
        AtomicBoolean seekCompleted = new AtomicBoolean(false);
        wrapper.setOnSeekCompleteListener(() -> seekCompleted.set(true));

        wrapper.seekTo(5000);

        assertTrue(seekCompleted.get());
    }

    @Test
    public void thereAreNoAudioTracksBeforeMediaIsPrepared() {
        assertTrue(wrapper.getAudioTracks().isEmpty());
        assertEquals(-1, wrapper.getSelectedAudioTrack());
    }

    @Test
    public void selectingAnAudioTrackWithoutPreparedMediaIsIgnored() {
        wrapper.setAudioTrack(0);

        assertEquals(-1, wrapper.getSelectedAudioTrack());
    }

    @Test
    public void thereIsNoVideoSizeWithoutAVideoTrack() {
        assertEquals(0, wrapper.getVideoWidth());
        assertEquals(0, wrapper.getVideoHeight());
    }

    @Test
    public void resettingDiscardsThePlaybackStateButKeepsTheWrapperUsable() {
        wrapper.setVolume(0.25f, 0.25f);
        wrapper.start();

        wrapper.reset();

        assertFalse(wrapper.isPlaying());
        assertEquals(1.0f, wrapper.getExoPlayer().getVolume(), 0.001f);
        assertEquals(Playable.INVALID_TIME, wrapper.getDuration());
    }

    @Test
    public void stoppingReturnsThePlayerToIdle() {
        wrapper.setDataSource(missingFilePath());
        wrapper.prepare();

        wrapper.stop();

        assertEquals(Player.STATE_IDLE, wrapper.getExoPlayer().getPlaybackState());
    }

    @Test
    public void preparingAnUnreadableFileReportsTheFailureToTheErrorListener() throws Exception {
        AtomicReference<String> reported = new AtomicReference<>();
        AtomicBoolean called = new AtomicBoolean(false);
        wrapper.setOnErrorListener(message -> {
            reported.set(message);
            called.set(true);
        });
        wrapper.setDataSource(missingFilePath());

        prepareUntilFailed();

        assertTrue(called.get());
        assertNotNull(reported.get());
    }

    @Test
    public void bufferingIsReportedWhileTheMediaIsBeingLoaded() throws Exception {
        List<Integer> updates = new ArrayList<>();
        wrapper.setOnBufferingUpdateListener(updates::add);
        wrapper.setDataSource(missingFilePath());

        prepareUntilFailed();

        assertTrue(updates.contains(ExoPlayerWrapper.BUFFERING_STARTED));
    }

    @Test
    public void credentialsAreAcceptedForAnyDataSource() throws Exception {
        AtomicBoolean called = new AtomicBoolean(false);
        wrapper.setOnErrorListener(message -> called.set(true));
        wrapper.setDataSource(missingFilePath(), "user", "secret");

        prepareUntilFailed();

        assertTrue(called.get());
    }

    @Test
    public void thePlayerUsesTheDataSourceThatWasConfiguredLast() throws Exception {
        AtomicBoolean called = new AtomicBoolean(false);
        wrapper.setOnErrorListener(message -> called.set(true));
        wrapper.setDataSource(STREAM_URL);
        wrapper.setDataSource(missingFilePath());

        prepareUntilFailed();

        assertTrue(called.get());
    }

    @Test
    public void anErrorWithoutAListenerLeavesThePlayerIdle() throws Exception {
        wrapper.setDataSource(missingFilePath());

        prepareUntilFailed();

        assertEquals(Player.STATE_IDLE, wrapper.getExoPlayer().getPlaybackState());
    }

    @Test
    public void releasingStopsTheBufferingUpdates() throws Exception {
        List<Integer> updates = new ArrayList<>();
        wrapper.setOnBufferingUpdateListener(updates::add);
        wrapper.setDataSource(missingFilePath());
        prepareUntilFailed();
        int seen = updates.size();

        wrapper.release();

        assertEquals(seen, updates.size());
    }
}
