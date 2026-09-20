package de.danoeh.antennapod.event.playback;

import org.junit.Test;

import de.danoeh.antennapod.model.playback.TimerValue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackEventsTest {

    @Test
    public void startedBufferEventOnlyReportsStarted() {
        BufferUpdateEvent event = BufferUpdateEvent.started();
        assertTrue(event.hasStarted());
        assertFalse(event.hasEnded());
    }

    @Test
    public void endedBufferEventOnlyReportsEnded() {
        BufferUpdateEvent event = BufferUpdateEvent.ended();
        assertTrue(event.hasEnded());
        assertFalse(event.hasStarted());
    }

    @Test
    public void bufferProgressUpdateReportsNeitherStartedNorEnded() {
        BufferUpdateEvent event = BufferUpdateEvent.progressUpdate(0.5f);
        assertEquals(0.5f, event.getProgress(), 0f);
        assertFalse(event.hasStarted());
        assertFalse(event.hasEnded());
    }

    @Test
    public void positionEventKeepsPositionAndDurationApart() {
        PlaybackPositionEvent event = new PlaybackPositionEvent(1500, 60000);
        assertEquals(1500, event.getPosition());
        assertEquals(60000, event.getDuration());
    }

    @Test
    public void speedChangedEventCarriesNewSpeed() {
        assertEquals(1.5f, new SpeedChangedEvent(1.5f).getNewSpeed(), 0f);
    }

    @Test
    public void serviceEventCarriesAction() {
        assertEquals(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN,
                new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN).action);
    }

    @Test
    public void justEnabledTimerIsReportedAsEnabledWithRemainingTimes() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.justEnabled(new TimerValue(3, 180000));
        assertTrue(event.wasJustEnabled());
        assertFalse(event.isOver());
        assertFalse(event.isCancelled());
        assertEquals(180000, event.getMillisTimeLeft());
        assertEquals(3, event.getDisplayTimeLeft());
    }

    @Test
    public void updatedTimerReportsRemainingTimes() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.updated(new TimerValue(2, 120000));
        assertFalse(event.wasJustEnabled());
        assertFalse(event.isOver());
        assertEquals(120000, event.getMillisTimeLeft());
        assertEquals(2, event.getDisplayTimeLeft());
    }

    @Test
    public void updatedTimerWithoutTimeLeftIsOver() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.updated(new TimerValue(0, 0));
        assertTrue(event.isOver());
        assertFalse(event.wasJustEnabled());
    }

    @Test
    public void updatedTimerClampsNegativeValuesToZero() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.updated(new TimerValue(-5, -1000));
        assertTrue(event.isOver());
        assertEquals(0, event.getMillisTimeLeft());
        assertEquals(0, event.getDisplayTimeLeft());
    }

    @Test
    public void cancelledTimerIsOnlyReportedAsCancelled() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.cancelled();
        assertTrue(event.isCancelled());
        assertFalse(event.isOver());
        assertFalse(event.wasJustEnabled());
    }
}
