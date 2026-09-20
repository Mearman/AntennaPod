package de.danoeh.antennapod.playback.service;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent;
import de.danoeh.antennapod.event.playback.SpeedChangedEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.base.PlayerStatus;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class PlaybackControllerTest {
    private Activity activity;
    private RecordingController controller;
    private MockedStatic<DBReader> dbReader;
    private final List<Object> events = new ArrayList<>();

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onEvent(SpeedChangedEvent event) {
        events.add(event);
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onEvent(PlaybackPositionEvent event) {
        events.add(event);
    }

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        PlaybackPreferences.writeNoMediaPlaying();
        PlaybackService.isRunning = false;
        dbReader = mockStatic(DBReader.class);
        activity = mock(Activity.class);
        controller = new RecordingController(activity);
        events.clear();
        EventBus.getDefault().register(this);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(this);
        controller.release();
        dbReader.close();
        PlaybackService.isRunning = false;
        PlaybackPreferences.writeNoMediaPlaying();
    }

    @Test
    public void aControllerStartedWithoutARunningServiceShowsThePlayButton() {
        controller.init();

        assertEquals(1, controller.showPlayCalls.size());
        assertTrue(controller.showPlayCalls.get(0));
    }

    @Test
    public void aControllerStartedWithARunningMedia3ServiceLeavesTheButtonAlone() {
        PlaybackService.isRunning = true;

        controller.init();

        assertTrue(controller.showPlayCalls.isEmpty());
    }

    @Test
    public void theControllerReconnectsWhenTheServiceAnnouncesThatItStarted() {
        controller.onEventMainThread(new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED));

        assertEquals(1, controller.showPlayCalls.size());
    }

    @Test
    public void theControllerIgnoresTheServiceShuttingDown() {
        controller.onEventMainThread(new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN));

        assertTrue(controller.showPlayCalls.isEmpty());
    }

    @Test
    public void releasingTheControllerDropsTheEpisodeItWasShowing() {
        FeedMedia media = media(7, 0, MediaType.AUDIO);
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(media);
        controller.init();
        assertSame(media, controller.getMedia());

        controller.release();
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(null);

        assertNull(controller.getMedia());
    }

    @Test
    public void releasingAControllerUnbindsFromTheServiceAndStopsListeningForBroadcasts() {
        controller.release();

        verify(activity).unbindService(any(ServiceConnection.class));
        verify(activity, times(2)).unregisterReceiver(any(BroadcastReceiver.class));
    }

    @Test
    public void theCurrentlyPlayingEpisodeIsLookedUpOnceAndThenRemembered() {
        FeedMedia media = media(7, 0, MediaType.AUDIO);
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(media);

        assertSame(media, controller.getMedia());
        assertSame(media, controller.getMedia());

        dbReader.verify(() -> DBReader.getFeedMedia(anyLong()), times(1));
    }

    @Test
    public void withoutAnEpisodeThePositionAndDurationAreUnknown() {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(null);

        assertEquals(Playable.INVALID_TIME, controller.getPosition());
        assertEquals(Playable.INVALID_TIME, controller.getDuration());
    }

    @Test
    public void withoutAServiceThePositionAndDurationComeFromTheStoredEpisode() {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(media(7, 45000, MediaType.AUDIO));

        assertEquals(45000, controller.getPosition());
        assertEquals(300000, controller.getDuration());
    }

    @Test
    public void aControllerThatIsNotConnectedReportsPlaybackAsStopped() {
        assertEquals(PlayerStatus.STOPPED, controller.getStatus());
        assertFalse(controller.isStreaming());
        assertNull(controller.getVideoSize());
        assertEquals(-1, controller.getSelectedAudioTrack());
        assertTrue(controller.getAudioTracks().isEmpty());
    }

    @Test
    public void thereIsNoSleepTimerWithoutAConnectedService() {
        assertFalse(controller.sleepTimerActive());
        assertEquals(Playable.INVALID_TIME, controller.getSleepTimerTimeLeft().getMillisValue());
        assertEquals(Playable.INVALID_TIME, controller.getSleepTimerTimeLeft().getDisplayValue());
    }

    @Test
    public void seekingWithoutAServiceStoresThePositionAndAnnouncesIt() {
        FeedMedia media = media(7, 0, MediaType.AUDIO);
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(media);

        try (MockedStatic<DBWriter> writer = mockStatic(DBWriter.class)) {
            controller.seekTo(60000);

            writer.verify(() -> DBWriter.setFeedItem(media.getItem(), false));
        }

        assertEquals(60000, media.getPosition());
        assertEquals(1, events.size());
        assertEquals(60000, ((PlaybackPositionEvent) events.get(0)).getPosition());
        assertEquals(300000, ((PlaybackPositionEvent) events.get(0)).getDuration());
    }

    @Test
    public void seekingWithoutAnEpisodeAtAllChangesNothing() {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(null);

        try (MockedStatic<DBWriter> writer = mockStatic(DBWriter.class)) {
            controller.seekTo(60000);

            writer.verify(() -> DBWriter.setFeedItem(any(), anyBoolean()), never());
        }

        assertTrue(events.isEmpty());
    }

    @Test
    public void changingTheSpeedWithoutAServiceIsAnnouncedSoTheUiCanFollow() {
        controller.setPlaybackSpeed(1.75f);

        assertEquals(1, events.size());
        assertEquals(1.75f, ((SpeedChangedEvent) events.get(0)).getNewSpeed(), 0.001f);
    }

    @Test
    public void theSpeedOfTheFeedIsUsedWhenNoServiceCanBeAsked() {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(mediaWithFeedSpeed(2.5f));

        assertEquals(2.5f, controller.getCurrentPlaybackSpeedMultiplier(), 0.001f);
    }

    @Test
    public void aggressiveSkipSilenceOnTheFeedIsUsedWhenNoServiceCanBeAsked() {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong()))
                .thenReturn(mediaWithSkipSilence(FeedPreferences.SkipSilence.AGGRESSIVE));

        assertTrue(controller.getCurrentPlaybackSkipSilence());
    }

    @Test
    public void aFeedWithoutItsOwnSpeedFallsBackToTheGlobalSkipSilenceSetting() {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(mediaWithFeedSpeed(
                FeedPreferences.SPEED_USE_GLOBAL));

        assertFalse(controller.getCurrentPlaybackSkipSilence());
    }

    @Test
    public void skipSilenceTurnedOffOnTheFeedIsUsedWhenNoServiceCanBeAsked() {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong()))
                .thenReturn(mediaWithSkipSilence(FeedPreferences.SkipSilence.OFF));

        assertFalse(controller.getCurrentPlaybackSkipSilence());
    }

    @Test
    public void aVideoEpisodeIsPlayedLocallyWhileNothingIsCasting() {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(media(7, 0, MediaType.VIDEO));

        assertTrue(controller.isPlayingVideoLocally());
    }

    @Test
    public void anAudioEpisodeIsNotAVideoPlayedLocally() {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(media(7, 0, MediaType.AUDIO));

        assertFalse(controller.isPlayingVideoLocally());
    }

    @Test
    public void nothingIsBoundWhileTheServiceIsNotRunning() {
        PlaybackController.bindToService(activity, service -> { });

        verify(activity, never()).bindService(any(Intent.class), any(ServiceConnection.class), anyInt());
    }

    @Test
    public void bindingIsAttemptedOnceTheServiceIsRunning() {
        PlaybackService.isRunning = true;

        PlaybackController.bindToService(activity, service -> { });

        verify(activity).bindService(any(Intent.class), any(ServiceConnection.class), anyInt());
    }

    private static class RecordingController extends PlaybackController {
        private final List<Boolean> showPlayCalls = new ArrayList<>();

        RecordingController(Activity activity) {
            super(activity);
        }

        @Override
        public void loadMediaInfo() {
        }

        @Override
        protected void updatePlayButtonShowsPlay(boolean showPlay) {
            showPlayCalls.add(showPlay);
        }
    }

    private static FeedMedia media(long id, int position, MediaType type) {
        Feed feed = new Feed(1, null, "Feed", "http://example.com", "d", null, null, null, null,
                "id", null, null, "http://example.com/feed.xml", 0);
        FeedItem item = new FeedItem(id, "Episode", "id" + id, "http://example.com", new Date(0),
                FeedItem.UNPLAYED, feed);
        FeedMedia media = new FeedMedia(id, item, 300000, position, 1,
                type == MediaType.VIDEO ? "video/mp4" : "audio/mp3", null,
                "http://example.com/e.mp3", 0, null, 0, 0);
        item.setMedia(media);
        return media;
    }

    private static FeedMedia mediaWithFeedSpeed(float speed) {
        FeedMedia media = media(7, 0, MediaType.AUDIO);
        FeedPreferences preferences = preferences();
        preferences.setFeedPlaybackSpeed(speed);
        media.getItem().getFeed().setPreferences(preferences);
        return media;
    }

    private static FeedMedia mediaWithSkipSilence(FeedPreferences.SkipSilence skipSilence) {
        FeedMedia media = media(7, 0, MediaType.AUDIO);
        FeedPreferences preferences = preferences();
        preferences.setFeedPlaybackSpeed(1.0f);
        preferences.setFeedSkipSilence(skipSilence);
        media.getItem().getFeed().setPreferences(preferences);
        return media;
    }

    private static FeedPreferences preferences() {
        return new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, null, null);
    }
}
