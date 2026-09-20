package de.danoeh.antennapod.event.playback;

import de.danoeh.antennapod.model.playback.TimerValue;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PlaybackEventDeliveryTest {
    private final List<Object> received = new ArrayList<>();

    @Subscribe
    public void onSleepTimerUpdated(SleepTimerUpdatedEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onBufferUpdate(BufferUpdateEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onPlaybackPosition(PlaybackPositionEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onPlaybackService(PlaybackServiceEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onSpeedChanged(SpeedChangedEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onPlaybackHistory(PlaybackHistoryEvent event) {
        received.add(event);
    }

    @Before
    public void setUp() {
        EventBus.getDefault().removeAllStickyEvents();
        EventBus.getDefault().register(this);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(this);
        EventBus.getDefault().removeAllStickyEvents();
    }

    @Test
    public void aJustEnabledSleepTimerReportsItsDurationAndFlagsItselfAsJustEnabled() {
        EventBus.getDefault().post(SleepTimerUpdatedEvent.justEnabled(new TimerValue(5, 900000)));

        SleepTimerUpdatedEvent event = (SleepTimerUpdatedEvent) received.get(0);
        assertTrue(event.wasJustEnabled());
        assertFalse(event.isOver());
        assertFalse(event.isCancelled());
        assertEquals(900000, event.getMillisTimeLeft());
        assertEquals(5, event.getDisplayTimeLeft());
    }

    @Test
    public void anUpdatedSleepTimerIsNeitherJustEnabledNorCancelled() {
        EventBus.getDefault().post(SleepTimerUpdatedEvent.updated(new TimerValue(3, 180000)));

        SleepTimerUpdatedEvent event = (SleepTimerUpdatedEvent) received.get(0);
        assertFalse(event.wasJustEnabled());
        assertFalse(event.isOver());
        assertFalse(event.isCancelled());
        assertEquals(180000, event.getMillisTimeLeft());
    }

    @Test
    public void anUpdateWithNegativeRemainingTimeIsClampedToZeroAndReportedAsOver() {
        EventBus.getDefault().post(SleepTimerUpdatedEvent.updated(new TimerValue(-2, -5000)));

        SleepTimerUpdatedEvent event = (SleepTimerUpdatedEvent) received.get(0);
        assertTrue(event.isOver());
        assertFalse(event.wasJustEnabled());
        assertEquals(0, event.getMillisTimeLeft());
        assertEquals(0, event.getDisplayTimeLeft());
    }

    @Test
    public void aCancelledSleepTimerIsCancelledButNotOverOrJustEnabled() {
        EventBus.getDefault().post(SleepTimerUpdatedEvent.cancelled());

        SleepTimerUpdatedEvent event = (SleepTimerUpdatedEvent) received.get(0);
        assertTrue(event.isCancelled());
        assertFalse(event.isOver());
        assertFalse(event.wasJustEnabled());
    }

    @Test
    public void aStickySleepTimerUpdateIsHandedToSubscribersThatRegisterLater() {
        EventBus.getDefault().postSticky(SleepTimerUpdatedEvent.updated(new TimerValue(1, 60000)));

        StickySleepTimerRecorder late = new StickySleepTimerRecorder();
        EventBus.getDefault().register(late);
        EventBus.getDefault().unregister(late);

        assertEquals(60000, late.event.getMillisTimeLeft());
        assertEquals(60000, EventBus.getDefault()
                .getStickyEvent(SleepTimerUpdatedEvent.class).getMillisTimeLeft());
    }

    @Test
    public void removingTheStickySleepTimerUpdateStopsLateSubscribersFromSeeingIt() {
        EventBus.getDefault().postSticky(SleepTimerUpdatedEvent.updated(new TimerValue(1, 60000)));
        EventBus.getDefault().removeStickyEvent(SleepTimerUpdatedEvent.class);

        StickySleepTimerRecorder late = new StickySleepTimerRecorder();
        EventBus.getDefault().register(late);
        EventBus.getDefault().unregister(late);

        assertNull(late.event);
        assertNull(EventBus.getDefault().getStickyEvent(SleepTimerUpdatedEvent.class));
    }

    public static class StickySleepTimerRecorder {
        private SleepTimerUpdatedEvent event;

        @Subscribe(sticky = true)
        public void onSleepTimerUpdated(SleepTimerUpdatedEvent sleepTimerUpdatedEvent) {
            this.event = sleepTimerUpdatedEvent;
        }
    }

    @Test
    public void bufferUpdatesDistinguishStartEndAndProgress() {
        EventBus.getDefault().post(BufferUpdateEvent.started());
        EventBus.getDefault().post(BufferUpdateEvent.progressUpdate(0.25f));
        EventBus.getDefault().post(BufferUpdateEvent.ended());

        BufferUpdateEvent started = (BufferUpdateEvent) received.get(0);
        BufferUpdateEvent progress = (BufferUpdateEvent) received.get(1);
        BufferUpdateEvent ended = (BufferUpdateEvent) received.get(2);
        assertTrue(started.hasStarted());
        assertFalse(started.hasEnded());
        assertFalse(progress.hasStarted());
        assertFalse(progress.hasEnded());
        assertEquals(0.25f, progress.getProgress(), 0.0001f);
        assertTrue(ended.hasEnded());
        assertFalse(ended.hasStarted());
    }

    @Test
    public void playbackPositionEventCarriesPositionAndDurationToSubscribers() {
        EventBus.getDefault().post(new PlaybackPositionEvent(12000, 300000));

        PlaybackPositionEvent event = (PlaybackPositionEvent) received.get(0);
        assertEquals(12000, event.getPosition());
        assertEquals(300000, event.getDuration());
    }

    @Test
    public void playbackServiceEventDistinguishesStartupFromShutdown() {
        EventBus.getDefault().post(new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED));
        EventBus.getDefault().post(new PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN));

        assertEquals(PlaybackServiceEvent.Action.SERVICE_STARTED,
                ((PlaybackServiceEvent) received.get(0)).action);
        assertEquals(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN,
                ((PlaybackServiceEvent) received.get(1)).action);
    }

    @Test
    public void speedChangedEventCarriesTheNewSpeed() {
        EventBus.getDefault().post(new SpeedChangedEvent(1.75f));

        assertEquals(1.75f, ((SpeedChangedEvent) received.get(0)).getNewSpeed(), 0.0001f);
    }

    @Test
    public void playbackHistoryEventIsDeliveredAndIdentifiesItselfByName() {
        EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated());

        assertEquals("PlaybackHistoryEvent", received.get(0).toString());
    }
}
