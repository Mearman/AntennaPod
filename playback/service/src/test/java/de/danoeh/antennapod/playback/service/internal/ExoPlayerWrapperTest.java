package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.FakeTimeline;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.playback.Playable;
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ExoPlayerWrapperTest {
    private Context context;
    private ExoPlayerWrapper wrapper;
    private TestScheduler scheduler;
    private Locale defaultLocale;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        scheduler = new TestScheduler();
        RxJavaPlugins.setComputationSchedulerHandler(ignored -> scheduler);
        RxAndroidPlugins.setMainThreadSchedulerHandler(ignored -> scheduler);
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
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
        Locale.setDefault(defaultLocale);
    }

    private String missingFilePath() {
        return missingFilePath("missing-episode.mp3");
    }

    private String missingFilePath(String fileName) {
        return context.getCacheDir().getAbsolutePath() + "/" + fileName;
    }

    private static Format audioTrack(String language) {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setLanguage(language)
                .build();
    }

    private void prepareFakeTracks(Format... formats) throws Exception {
        prepareFakeTracks(new FakeTimeline(), formats);
    }

    private void prepareFakeTracks(FakeTimeline timeline, Format... formats) throws Exception {
        wrapper.getExoPlayer().setMediaSource(new FakeMediaSource(timeline, formats));
        wrapper.getExoPlayer().prepare();
        TestPlayerRunHelper.runUntilPlaybackState(wrapper.getExoPlayer(), Player.STATE_READY);
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
    public void stoppingReturnsThePlayerToIdle() throws Exception {
        prepareFakeTracks(audioTrack("en"));
        assertEquals(Player.STATE_READY, wrapper.getExoPlayer().getPlaybackState());

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
    public void thePlayerUsesTheDataSourceThatWasConfiguredLast() {
        wrapper.setDataSource(missingFilePath("first-episode.mp3"));
        wrapper.setDataSource(missingFilePath("second-episode.mp3"));

        wrapper.prepare();

        MediaItem prepared = wrapper.getExoPlayer().getMediaItemAt(0);
        assertEquals(Uri.parse(missingFilePath("second-episode.mp3")), prepared.localConfiguration.uri);
    }

    @Test
    public void aFailureWithoutAnErrorListenerStillStopsThePlayer() throws Exception {
        prepareFakeTracks(audioTrack("en"));
        assertEquals(Player.STATE_READY, wrapper.getExoPlayer().getPlaybackState());
        wrapper.setDataSource(missingFilePath());

        prepareUntilFailed();

        assertNotNull(wrapper.getExoPlayer().getPlayerError());
        assertEquals(Player.STATE_IDLE, wrapper.getExoPlayer().getPlaybackState());
    }

    @Test
    public void everyAudioTrackOfThePreparedMediaIsListedByName() throws Exception {
        prepareFakeTracks(audioTrack("en"), audioTrack("de"));

        assertEquals(Arrays.asList("English", "German"), wrapper.getAudioTracks());
    }

    @Test
    public void theFirstAudioTrackIsSelectedByDefault() throws Exception {
        prepareFakeTracks(audioTrack("en"), audioTrack("de"));

        assertEquals(0, wrapper.getSelectedAudioTrack());
    }

    @Test
    public void selectingAnotherAudioTrackChangesTheSelection() throws Exception {
        prepareFakeTracks(audioTrack("en"), audioTrack("de"));

        wrapper.setAudioTrack(1);
        TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(wrapper.getExoPlayer());

        assertEquals(1, wrapper.getSelectedAudioTrack());
    }

    @Test
    public void theDurationOfPreparedMediaIsReported() throws Exception {
        FakeTimeline timeline = new FakeTimeline();

        prepareFakeTracks(timeline, audioTrack("en"));

        assertEquals(timeline.getWindow(0, new Timeline.Window()).getDurationMs(), wrapper.getDuration());
    }

    @Test
    public void theBufferedPercentageIsReportedPeriodically() throws Exception {
        List<Integer> updates = new ArrayList<>();
        wrapper.setOnBufferingUpdateListener(updates::add);
        prepareFakeTracks(audioTrack("en"));
        updates.clear();

        scheduler.advanceTimeBy(2, TimeUnit.SECONDS);

        assertEquals(1, updates.size());
        assertEquals(Integer.valueOf(wrapper.getExoPlayer().getBufferedPercentage()), updates.get(0));
    }

    @Test
    public void reachingTheEndOfTheMediaRunsTheCompletionListener() throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);
        wrapper.setOnCompletionListener(() -> completed.set(true));
        prepareFakeTracks(audioTrack("en"));

        wrapper.start();
        TestPlayerRunHelper.runUntilPlaybackState(wrapper.getExoPlayer(), Player.STATE_ENDED);

        assertTrue(completed.get());
    }

    @Test
    public void theSizeOfPreparedVideoMediaIsReported() throws Exception {
        Format video = new Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_H264)
                .setWidth(1920)
                .setHeight(1080)
                .build();

        prepareFakeTracks(video);

        assertEquals(1920, wrapper.getVideoWidth());
        assertEquals(1080, wrapper.getVideoHeight());
    }

    @Test
    public void releasingStopsTheBufferingUpdates() throws Exception {
        List<Integer> updates = new ArrayList<>();
        wrapper.setOnBufferingUpdateListener(updates::add);
        prepareFakeTracks(audioTrack("en"));
        updates.clear();

        wrapper.release();
        scheduler.advanceTimeBy(2, TimeUnit.SECONDS);

        assertTrue(updates.isEmpty());
    }
}
