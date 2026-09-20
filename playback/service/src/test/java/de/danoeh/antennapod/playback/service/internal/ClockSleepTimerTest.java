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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ClockSleepTimerTest {
    private ClockSleepTimer timer;
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
        timer = new ClockSleepTimer(context);
    }

    @After
    public void tearDown() {
        timer.stop();
        EventBus.getDefault().unregister(this);
        EventBus.getDefault().removeAllStickyEvents();
    }

    @Test
    public void timerIsInactiveBeforeItIsStarted() {
        assertFalse(timer.isActive());
        assertEquals(0, timer.getTimeLeft().getMillisValue());
    }

    @Test
    public void startingTheTimerMakesItActiveAndAnnouncesTheFullWaitingTime() {
        timer.start(600000);

        assertTrue(timer.isActive());
        assertEquals(600000, timer.getTimeLeft().getMillisValue());
        assertEquals(1, events.stream().filter(SleepTimerUpdatedEvent::wasJustEnabled).count());
        SleepTimerUpdatedEvent sticky = EventBus.getDefault().getStickyEvent(SleepTimerUpdatedEvent.class);
        assertEquals(600000, sticky.getMillisTimeLeft());
    }

    @Test
    public void stoppingTheTimerClearsTheRemainingTimeAndPostsACancellation() {
        timer.start(600000);
        events.clear();

        timer.stop();

        assertFalse(timer.isActive());
        assertEquals(0, timer.getTimeLeft().getMillisValue());
        assertTrue(EventBus.getDefault().getStickyEvent(SleepTimerUpdatedEvent.class).isCancelled());
    }

    @Test
    public void positionUpdatesBeforeTheTimerStartsAreIgnored() {
        timer.updateRemainingTime(500000);

        timer.playbackPositionUpdate(new PlaybackPositionEvent(1000, 300000));

        assertEquals(500000, timer.getTimeLeft().getMillisValue());
        assertTrue(events.isEmpty());
    }

    @Test
    public void positionUpdateStopsTheTimerOnceNoTimeIsLeft() {
        timer.start(600000);
        timer.updateRemainingTime(0);
        events.clear();

        timer.playbackPositionUpdate(new PlaybackPositionEvent(1000, 300000));

        assertFalse(timer.isActive());
        assertTrue(EventBus.getDefault().getStickyEvent(SleepTimerUpdatedEvent.class).isCancelled());
    }

    @Test
    public void positionUpdateCountsElapsedTimeDownAndPublishesTheRemainder() {
        timer.start(600000);
        events.clear();

        timer.playbackPositionUpdate(new PlaybackPositionEvent(1000, 300000));

        assertTrue(timer.isActive());
        assertTrue(timer.getTimeLeft().getMillisValue() <= 600000);
        assertTrue(timer.getTimeLeft().getMillisValue() > 590000);
        assertEquals(1, events.size());
        assertEquals(timer.getTimeLeft().getMillisValue(), events.get(0).getMillisTimeLeft());
    }

    @Test
    public void resetRestoresTheWaitingTimeTheTimerWasStartedWith() {
        timer.start(600000);
        timer.updateRemainingTime(1000);
        events.clear();

        timer.reset();

        assertEquals(600000, timer.getTimeLeft().getMillisValue());
        assertTrue(events.get(0).isCancelled());
        assertTrue(events.get(1).wasJustEnabled());
    }

    @Test
    public void theTimerEndsTheEpisodeOnlyWhenItRunsOutBeforeTheEpisodeDoes() {
        timer.start(600000);

        assertTrue(timer.isEndingThisEpisode(900000));
        assertFalse(timer.isEndingThisEpisode(300000));
    }

    @Test
    public void playbackContinuesToTheNextEpisodeWhileTimeIsLeft() {
        timer.start(600000);
        assertTrue(timer.shouldContinueToNextEpisode());

        timer.updateRemainingTime(0);
        assertFalse(timer.shouldContinueToNextEpisode());
    }

    @Test
    public void finishingAnEpisodeDoesNotChangeARemainingDuration() {
        timer.start(600000);
        timer.updateRemainingTime(123000);

        timer.episodeFinishedPlayback();

        assertEquals(123000, timer.getTimeLeft().getMillisValue());
    }

    @Test
    public void expiringTimerVibratesOnlyOnceWhenVibrationIsEnabled() {
        SleepTimerPreferences.setVibrate(true);
        VibrationCountingTimer vibrating = new VibrationCountingTimer(RuntimeEnvironment.getApplication());
        vibrating.start(600000);
        vibrating.updateRemainingTime(SleepTimer.NOTIFICATION_THRESHOLD - 1000);

        vibrating.notifyAboutExpiry();
        vibrating.notifyAboutExpiry();

        assertEquals(1, vibrating.vibrations);
        vibrating.stop();
    }

    private static class VibrationCountingTimer extends ClockSleepTimer {
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
