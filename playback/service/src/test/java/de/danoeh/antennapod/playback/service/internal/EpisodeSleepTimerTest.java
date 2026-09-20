package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class EpisodeSleepTimerTest {
    private EpisodeSleepTimer timer;
    private final List<SleepTimerUpdatedEvent> events = new ArrayList<>();

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onSleepTimerUpdated(SleepTimerUpdatedEvent event) {
        events.add(event);
    }

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SleepTimerPreferences.init(context);
        SleepTimerPreferences.setVibrate(false);
        SleepTimerPreferences.setShakeToReset(false);
        EventBus.getDefault().removeAllStickyEvents();
        events.clear();
        EventBus.getDefault().register(this);
        timer = new EpisodeSleepTimer(context);
    }

    @After
    public void tearDown() {
        timer.stop();
        EventBus.getDefault().unregister(this);
        EventBus.getDefault().removeAllStickyEvents();
    }

    @Test
    public void theRemainingEpisodeCountIsReportedAsDaysSoItNeverLooksLikeItIsAboutToExpire() {
        timer.start(3);

        assertEquals(3, timer.getTimeLeft().getDisplayValue());
        assertEquals(TimeUnit.DAYS.toMillis(3), timer.getTimeLeft().getMillisValue());
    }

    @Test
    public void onlyTheFinalEpisodeIsTreatedAsEndingThisEpisode() {
        timer.start(2);
        assertFalse(timer.isEndingThisEpisode(1000));

        timer.updateRemainingTime(1);
        assertTrue(timer.isEndingThisEpisode(1000));
    }

    @Test
    public void finishingAnEpisodeConsumesOneOfTheRemainingEpisodes() {
        timer.start(3);

        timer.episodeFinishedPlayback();

        assertEquals(2, timer.getTimeLeft().getDisplayValue());
    }

    @Test
    public void playbackStopsAfterTheLastEpisodeHasFinished() {
        timer.start(1);
        timer.episodeFinishedPlayback();

        assertFalse(timer.shouldContinueToNextEpisode());
        assertFalse(timer.isActive());
        assertTrue(EventBus.getDefault().getStickyEvent(SleepTimerUpdatedEvent.class).isCancelled());
    }

    @Test
    public void playbackContinuesWhileEpisodesRemain() {
        timer.start(2);
        timer.episodeFinishedPlayback();

        assertTrue(timer.shouldContinueToNextEpisode());
        assertTrue(timer.isActive());
    }

    @Test
    public void positionUpdatesDuringTheLastEpisodeReportTheTimeLeftInThatEpisode() {
        timer.start(1);
        events.clear();

        timer.playbackPositionUpdate(new PlaybackPositionEvent(100000, 300000));

        assertEquals(1, events.size());
        assertEquals(1, events.get(0).getDisplayTimeLeft());
        assertEquals(200000, events.get(0).getMillisTimeLeft());
    }

    @Test
    public void positionUpdatesWithMoreEpisodesLeftKeepReportingTheEpisodeCount() {
        timer.start(4);
        events.clear();

        timer.playbackPositionUpdate(new PlaybackPositionEvent(100000, 300000));

        assertEquals(1, events.size());
        assertEquals(4, events.get(0).getDisplayTimeLeft());
        assertEquals(TimeUnit.DAYS.toMillis(4), events.get(0).getMillisTimeLeft());
    }

    @Test
    public void theEndOfTheLastEpisodeTriggersTheExpiryNotification() {
        SleepTimerPreferences.setVibrate(true);
        VibrationCountingTimer vibrating = new VibrationCountingTimer(RuntimeEnvironment.getApplication());
        vibrating.start(1);

        vibrating.playbackPositionUpdate(new PlaybackPositionEvent(295000, 300000));

        assertEquals(1, vibrating.vibrations);
        vibrating.stop();
    }

    @Test
    public void thereIsNoExpiryNotificationWhileTheLastEpisodeIsStillFarFromDone() {
        SleepTimerPreferences.setVibrate(true);
        VibrationCountingTimer vibrating = new VibrationCountingTimer(RuntimeEnvironment.getApplication());
        vibrating.start(1);

        vibrating.playbackPositionUpdate(new PlaybackPositionEvent(100000, 300000));

        assertEquals(0, vibrating.vibrations);
        vibrating.stop();
    }

    private static class VibrationCountingTimer extends EpisodeSleepTimer {
        private int vibrations = 0;

        VibrationCountingTimer(Context context) {
            super(context);
        }

        @Override
        protected void vibrate() {
            vibrations++;
        }
    }
}
