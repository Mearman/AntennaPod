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
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class EpisodeSleepTimerTest {
    private RecordingEpisodeSleepTimer timer;
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
        timer = new RecordingEpisodeSleepTimer(context);
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

    @Test
    public void anEpisodeTimerCountsEpisodesAndReportsADurationFarInTheFuture() {
        timer.start(3);

        assertTrue(timer.isActive());
        assertEquals(3, timer.getTimeLeft().getDisplayValue());
        assertEquals(TimeUnit.DAYS.toMillis(3), timer.getTimeLeft().getMillisValue());
        timer.stop();
    }

    @Test
    public void withSeveralEpisodesLeftEveryTickJustRepeatsTheEpisodeCount() {
        timer.start(3);
        events.clear();

        timer.playbackPositionUpdate(new PlaybackPositionEvent(1000, 600000));

        assertEquals(3, lastEvent().getDisplayTimeLeft());
        assertEquals(TimeUnit.DAYS.toMillis(3), lastEvent().getMillisTimeLeft());
        assertEquals(0, timer.vibrations);
        timer.stop();
    }

    @Test
    public void onTheLastEpisodeTheTickReportsHowMuchOfThatEpisodeIsLeft() {
        timer.start(1);
        events.clear();

        timer.playbackPositionUpdate(new PlaybackPositionEvent(100000, 600000));

        assertEquals(1, lastEvent().getDisplayTimeLeft());
        assertEquals(500000, lastEvent().getMillisTimeLeft());
        timer.stop();
    }

    @Test
    public void theUserIsWarnedWhenTheLastEpisodeIsAboutToFinish() {
        SleepTimerPreferences.setVibrate(true);
        timer.start(1);

        timer.playbackPositionUpdate(new PlaybackPositionEvent(595000, 600000));

        assertEquals(5000, lastEvent().getMillisTimeLeft());
        assertEquals(1, timer.vibrations);
        timer.stop();
    }

    @Test
    public void finishingAnEpisodeUsesUpOneOfTheRemainingEpisodes() {
        timer.start(3);

        timer.episodeFinishedPlayback();

        assertEquals(2, timer.getTimeLeft().getDisplayValue());
        timer.stop();
    }

    @Test
    public void playbackContinuesWhileEpisodesRemain() {
        timer.start(2);

        timer.episodeFinishedPlayback();

        assertTrue(timer.shouldContinueToNextEpisode());
        timer.stop();
    }

    @Test
    public void theTimerStopsPlaybackAndItselfOnceTheLastEpisodeIsDone() {
        timer.start(1);

        timer.episodeFinishedPlayback();

        assertFalse(timer.shouldContinueToNextEpisode());
        assertFalse(timer.isActive());
        assertTrue(lastEvent().isCancelled());
    }

    @Test
    public void onlyTheLastEpisodeCountsAsTheEpisodeTheTimerEndsOn() {
        timer.start(2);
        assertFalse(timer.isEndingThisEpisode(100000));

        timer.episodeFinishedPlayback();

        assertTrue(timer.isEndingThisEpisode(100000));
        timer.stop();
    }

    private static class RecordingEpisodeSleepTimer extends EpisodeSleepTimer {
        private int vibrations;

        RecordingEpisodeSleepTimer(Context context) {
            super(context);
        }

        @Override
        protected void vibrate() {
            vibrations++;
        }
    }
}
