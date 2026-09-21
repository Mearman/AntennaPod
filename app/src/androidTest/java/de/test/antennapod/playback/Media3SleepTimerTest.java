package de.test.antennapod.playback;

import android.os.Bundle;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.test.filters.LargeTest;
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.playback.service.internal.MediaLibrarySessionCallback;
import de.danoeh.antennapod.playback.service.internal.SleepTimer;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.storage.preferences.SleepTimerType;
import org.awaitility.Awaitility;
import org.greenrobot.eventbus.EventBus;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@LargeTest
public class Media3SleepTimerTest extends Media3ServiceTest {
    private static final long ONE_MINUTE_MILLIS = TimeUnit.MINUTES.toMillis(1);

    @Override
    protected String mediaFileName() {
        return "30sec.mp3";
    }

    @Test
    public void testSleepTimerCommandStartsTheTimer() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        SleepTimerPreferences.setLastTimer("1");
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);
        awaitPlaying();

        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, null);

        Awaitility.await("sleep timer counting down")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> sleepTimerEvent() != null && !sleepTimerEvent().isCancelled()
                        && sleepTimerEvent().getMillisTimeLeft() > 0
                        && sleepTimerEvent().getMillisTimeLeft() < ONE_MINUTE_MILLIS);
        assertFalse(sleepTimerEvent().isOver());
    }

    @Test
    public void testSleepTimerCanBeExtended() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        SleepTimerPreferences.setLastTimer("1");
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);
        awaitPlaying();
        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, null);
        awaitTimerRunning();

        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_EXTEND_SLEEP_TIMER,
                MediaLibrarySessionCallback.createBundle(TimeUnit.MINUTES.toMillis(10)));

        Awaitility.await("sleep timer extended")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> sleepTimerEvent().getMillisTimeLeft() > ONE_MINUTE_MILLIS);
    }

    @Test
    public void testSleepTimerCanBeDisabled() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        SleepTimerPreferences.setLastTimer("1");
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);
        awaitPlaying();
        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, null);
        awaitTimerRunning();

        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_DISABLE_SLEEP_TIMER, null);

        Awaitility.await("sleep timer cancelled")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> sleepTimerEvent().isCancelled());
        assertTrue("Playback continues after cancelling the timer",
                Media3TestUtils.getOnMain(controller()::isPlaying));
    }

    @Test
    public void testExpiringSleepTimerPausesPlayback() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        SleepTimerPreferences.setLastTimer("1");
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);
        awaitPlaying();
        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, null);
        awaitTimerRunning();

        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_EXTEND_SLEEP_TIMER,
                MediaLibrarySessionCallback.createBundle(-ONE_MINUTE_MILLIS + 2000));

        Awaitility.await("playback paused by the sleep timer")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> !PlaybackService.isRunning);
        assertEquals(PlaybackPreferences.PLAYER_STATUS_PAUSED, PlaybackPreferences.getCurrentPlayerStatus());
        assertEquals(media.getId(), PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
    }

    @Test
    public void testSleepTimerStartsAutomaticallyInsideTheConfiguredTimeRange() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        SleepTimerPreferences.setLastTimer("1");
        SleepTimerPreferences.setAutoEnable(true);
        SleepTimerPreferences.setAutoEnableFrom(0);
        SleepTimerPreferences.setAutoEnableTo(24);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        awaitPlaying();

        awaitTimerRunning();
    }

    @Test
    public void testEpisodeSleepTimerStopsAfterTheCurrentEpisode() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.EPISODES);
        SleepTimerPreferences.setLastTimer("1");
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia media = queue.get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);
        awaitPlaying();
        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, null);
        Awaitility.await("episode sleep timer running")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> sleepTimerEvent() != null && sleepTimerEvent().getDisplayTimeLeft() == 1);

        skipToTheEnd();

        Awaitility.await("playback stopped after the episode")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
                        == PlaybackPreferences.NO_MEDIA_PLAYING);
        assertTrue("The finished episode is marked as played",
                DBReader.getFeedItem(media.getItem().getId()).isPlayed());
        assertEquals("The next episode is not started", 0,
                Media3TestUtils.getOnMain(controller()::getMediaItemCount).intValue());
    }

    @Test
    public void testEpisodeSleepTimerContinuesUntilTheConfiguredNumberOfEpisodes() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.EPISODES);
        SleepTimerPreferences.setLastTimer("2");
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia first = queue.get(0).getMedia();
        FeedMedia second = queue.get(1).getMedia();
        play(first);
        awaitCurrentMedia(first);
        awaitPlaying();
        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, null);
        Awaitility.await("episode sleep timer running")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> sleepTimerEvent() != null && sleepTimerEvent().getDisplayTimeLeft() == 2);

        skipToTheEnd();
        awaitCurrentMedia(second);
        awaitPlaying();
        skipToTheEnd();

        Awaitility.await("playback stopped after the second episode")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
                        == PlaybackPreferences.NO_MEDIA_PLAYING);
        assertTrue("Both episodes are marked as played",
                DBReader.getFeedItem(first.getItem().getId()).isPlayed()
                        && DBReader.getFeedItem(second.getItem().getId()).isPlayed());
    }

    @Test
    public void testExpiringSleepTimerFadesTheVolumeOut() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        SleepTimerPreferences.setLastTimer("1");
        SleepTimerPreferences.setVibrate(true);
        SleepTimerPreferences.setShakeToReset(true);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);
        awaitPlaying();
        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, null);
        awaitTimerRunning();
        assertEquals("Playback starts at full volume", 1.0f, volume(), 0.0f);

        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_EXTEND_SLEEP_TIMER,
                MediaLibrarySessionCallback.createBundle(SleepTimer.NOTIFICATION_THRESHOLD
                        - sleepTimerEvent().getMillisTimeLeft()));

        Awaitility.await("volume faded out while the timer is about to expire")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> volume() < 1.0f);
        assertTrue("Playback continues while the timer is about to expire",
                Media3TestUtils.getOnMain(controller()::isPlaying));

        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_DISABLE_SLEEP_TIMER, null);

        Awaitility.await("volume restored after cancelling the timer")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> volume() == 1.0f);
        assertTrue(sleepTimerEvent().isCancelled());
    }

    private void skipToTheEnd() {
        awaitReady();
        Media3TestUtils.runOnMain(() -> controller().seekTo(28000));
    }

    private float volume() {
        return Media3TestUtils.getOnMain(controller()::getVolume);
    }

    private void awaitTimerRunning() {
        Awaitility.await("sleep timer running")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> {
                    SleepTimerUpdatedEvent event = sleepTimerEvent();
                    return event != null && !event.isCancelled() && !event.isOver()
                            && event.getMillisTimeLeft() > 0;
                });
    }

    private SleepTimerUpdatedEvent sleepTimerEvent() {
        return EventBus.getDefault().getStickyEvent(SleepTimerUpdatedEvent.class);
    }

    private void sendCommand(SessionCommand command, Bundle args) {
        MediaController mediaController = controller();
        Bundle arguments = args == null ? Bundle.EMPTY : args;
        SessionResult result = Media3TestUtils.awaitFuture(Media3TestUtils.getOnMain(() ->
                mediaController.sendCustomCommand(command, arguments)));
        assertNotNull(result);
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode);
    }
}
