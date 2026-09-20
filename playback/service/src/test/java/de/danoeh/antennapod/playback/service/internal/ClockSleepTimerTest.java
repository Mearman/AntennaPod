package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class ClockSleepTimerTest {
    private static final long FIFTEEN_MINUTES = 900000;
    private RecordingClockSleepTimer timer;
    private final List<SleepTimerUpdatedEvent> events = new ArrayList<>();

    @Subscribe
    public void onSleepTimerUpdated(SleepTimerUpdatedEvent event) {
        events.add(event);
    }

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        SleepTimerPreferences.setVibrate(false);
        SleepTimerPreferences.setShakeToReset(false);
        timer = new RecordingClockSleepTimer(context);
        EventBus.getDefault().register(this);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(this);
        PlaybackTestDatabase.tearDown();
    }

    private SleepTimerUpdatedEvent lastEvent() {
        return events.get(events.size() - 1);
    }

    private void tick(int position) {
        timer.playbackPositionUpdate(new PlaybackPositionEvent(position, 600000));
    }

    @Test
    public void aTimerThatWasNeverStartedIsNotActive() {
        assertFalse(timer.isActive());
        assertEquals(0, timer.getTimeLeft().getMillisValue());
    }

    @Test
    public void startingATimerAnnouncesItAndMakesItActive() {
        timer.start(FIFTEEN_MINUTES);

        assertTrue(timer.isActive());
        assertEquals(FIFTEEN_MINUTES, timer.getTimeLeft().getMillisValue());
        assertTrue(events.get(0).wasJustEnabled());
        assertEquals(FIFTEEN_MINUTES, events.get(0).getMillisTimeLeft());
        assertFalse(lastEvent().wasJustEnabled());
        assertEquals(FIFTEEN_MINUTES, lastEvent().getMillisTimeLeft());
        timer.stop();
    }

    @Test
    public void everyPlaybackTickTakesTheTimeSinceTheLastTickOffTheTimer() {
        timer.start(FIFTEEN_MINUTES);
        events.clear();

        tick(1000);

        long left = timer.getTimeLeft().getMillisValue();
        assertTrue(left <= FIFTEEN_MINUTES);
        assertTrue(left > FIFTEEN_MINUTES - 10000);
        assertEquals(left, lastEvent().getMillisTimeLeft());
        timer.stop();
    }

    @Test
    public void aTickArrivingWithoutAPrecedingOneIsIgnoredBecausePlaybackWasPausedInBetween() {
        timer.updateRemainingTime(FIFTEEN_MINUTES);
        events.clear();

        tick(1000);

        assertEquals(FIFTEEN_MINUTES, timer.getTimeLeft().getMillisValue());
        assertTrue(events.isEmpty());
    }

    @Test
    public void theTimerVibratesOnceWhenItIsAboutToExpireRatherThanOnEveryTick() {
        SleepTimerPreferences.setVibrate(true);
        timer.start(SleepTimer.NOTIFICATION_THRESHOLD - 1);

        tick(1000);
        tick(2000);

        assertEquals(1, timer.vibrations);
        timer.stop();
    }

    @Test
    public void theTimerStaysSilentWhenTheUserTurnedVibrationOff() {
        timer.start(SleepTimer.NOTIFICATION_THRESHOLD - 1);

        tick(1000);

        assertEquals(0, timer.vibrations);
        timer.stop();
    }

    @Test
    public void theTimerDoesNotWarnWhileThereIsPlentyOfTimeLeft() {
        SleepTimerPreferences.setVibrate(true);
        timer.start(FIFTEEN_MINUTES);

        tick(1000);

        assertEquals(0, timer.vibrations);
        timer.stop();
    }

    @Test
    public void theTimerStopsItselfOnceTheRemainingTimeRunsOut() {
        timer.start(FIFTEEN_MINUTES);
        timer.updateRemainingTime(0);

        tick(1000);

        assertFalse(timer.isActive());
        assertTrue(lastEvent().isCancelled());
    }

    @Test
    public void stoppingTheTimerCancelsItForEveryone() {
        timer.start(FIFTEEN_MINUTES);

        timer.stop();

        assertFalse(timer.isActive());
        assertTrue(lastEvent().isCancelled());
        assertTrue(EventBus.getDefault().getStickyEvent(SleepTimerUpdatedEvent.class).isCancelled());
    }

    @Test
    public void resettingTheTimerPutsBackTheDurationItWasStartedWith() {
        timer.start(FIFTEEN_MINUTES);
        timer.updateRemainingTime(1000);

        timer.reset();

        assertEquals(FIFTEEN_MINUTES, timer.getTimeLeft().getMillisValue());
        assertTrue(lastEvent().wasJustEnabled());
        assertEquals(FIFTEEN_MINUTES, lastEvent().getMillisTimeLeft());
        timer.stop();
    }

    @Test
    public void theTimerEndsTheCurrentEpisodeOnlyWhenLessRemainsOnItThanOnTheEpisode() {
        timer.start(300000);

        assertTrue(timer.isEndingThisEpisode(400000));
        assertFalse(timer.isEndingThisEpisode(100000));
        timer.stop();
    }

    @Test
    public void playbackContinuesToTheNextEpisodeWhileTimeIsLeftAndStopsWhenItIsNot() {
        timer.start(300000);
        assertTrue(timer.shouldContinueToNextEpisode());

        timer.updateRemainingTime(0);

        assertFalse(timer.shouldContinueToNextEpisode());
        timer.stop();
    }

    @Test
    public void finishingAnEpisodeLeavesAClockTimerUntouched() {
        timer.start(300000);

        timer.episodeFinishedPlayback();

        assertEquals(300000, timer.getTimeLeft().getMillisValue());
        timer.stop();
    }

    private static class RecordingClockSleepTimer extends ClockSleepTimer {
        private int vibrations;

        RecordingClockSleepTimer(Context context) {
            super(context);
        }

        @Override
        protected void vibrate() {
            vibrations++;
        }
    }
}
