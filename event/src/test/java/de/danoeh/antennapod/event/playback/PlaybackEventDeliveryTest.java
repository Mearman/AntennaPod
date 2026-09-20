package de.danoeh.antennapod.event.playback;

import android.content.Context;
import de.danoeh.antennapod.event.EventRecorder;
import de.danoeh.antennapod.event.EventTestDatabase;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.playback.TimerValue;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PlaybackEventDeliveryTest {
    private final EventRecorder recorder = new EventRecorder();
    private List<FeedItem> items;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        EventTestDatabase.setUp(context);
        Feed feed = EventTestDatabase.storeFeed(context, "http://example.com/feed", "Feed", 2);
        items = EventTestDatabase.itemsOf(feed);
        recorder.register();
    }

    @After
    public void tearDown() {
        recorder.unregister();
        EventTestDatabase.tearDown();
    }

    @Test
    public void finishingAnEpisodePublishesAPlaybackHistoryUpdateAndStoresWhenItWasPlayed()
            throws ExecutionException, InterruptedException {
        Date played = new Date(1700000000000L);

        DBWriter.addItemToPlaybackHistory(items.get(0).getMedia(), played).get();

        assertNotNull(recorder.single(PlaybackHistoryEvent.class));
        assertEquals(played, DBReader.getFeedMedia(items.get(0).getMedia().getId()).getLastPlayedTimeHistory());
    }

    @Test
    public void removingAnEpisodeFromTheHistoryPublishesAnUpdateAndForgetsWhenItWasPlayed()
            throws ExecutionException, InterruptedException {
        DBWriter.addItemToPlaybackHistory(items.get(0).getMedia(), new Date(1700000000000L)).get();
        recorder.clear();

        DBWriter.deleteFromPlaybackHistory(items.get(0)).get();

        assertNotNull(recorder.single(PlaybackHistoryEvent.class));
        assertNull(DBReader.getFeedMedia(items.get(0).getMedia().getId()).getLastPlayedTimeHistory());
    }

    @Test
    public void clearingTheHistoryPublishesAnUpdateAndForgetsEveryPlayedTime()
            throws ExecutionException, InterruptedException {
        DBWriter.addItemToPlaybackHistory(items.get(0).getMedia(), new Date(1700000000000L)).get();
        DBWriter.addItemToPlaybackHistory(items.get(1).getMedia(), new Date(1700000001000L)).get();
        recorder.clear();

        DBWriter.clearPlaybackHistory().get();

        assertNotNull(recorder.single(PlaybackHistoryEvent.class));
        assertNull(DBReader.getFeedMedia(items.get(0).getMedia().getId()).getLastPlayedTimeHistory());
        assertNull(DBReader.getFeedMedia(items.get(1).getMedia().getId()).getLastPlayedTimeHistory());
    }

    @Test
    public void aJustEnabledSleepTimerReportsItsDurationAndFlagsItselfAsJustEnabled() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.justEnabled(new TimerValue(5, 900000));

        assertTrue(event.wasJustEnabled());
        assertFalse(event.isOver());
        assertFalse(event.isCancelled());
        assertEquals(900000, event.getMillisTimeLeft());
        assertEquals(5, event.getDisplayTimeLeft());
    }

    @Test
    public void anUpdatedSleepTimerIsNeitherJustEnabledNorCancelled() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.updated(new TimerValue(3, 180000));

        assertFalse(event.wasJustEnabled());
        assertFalse(event.isOver());
        assertFalse(event.isCancelled());
        assertEquals(180000, event.getMillisTimeLeft());
    }

    @Test
    public void anUpdateWithNegativeRemainingTimeIsClampedToZeroAndReportedAsOver() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.updated(new TimerValue(-2, -5000));

        assertTrue(event.isOver());
        assertFalse(event.wasJustEnabled());
        assertEquals(0, event.getMillisTimeLeft());
        assertEquals(0, event.getDisplayTimeLeft());
    }

    @Test
    public void aCancelledSleepTimerIsCancelledButNotOverOrJustEnabled() {
        SleepTimerUpdatedEvent event = SleepTimerUpdatedEvent.cancelled();

        assertTrue(event.isCancelled());
        assertFalse(event.isOver());
        assertFalse(event.wasJustEnabled());
    }

    @Test
    public void bufferUpdatesDistinguishStartEndAndProgress() {
        BufferUpdateEvent started = BufferUpdateEvent.started();
        BufferUpdateEvent progress = BufferUpdateEvent.progressUpdate(0.25f);
        BufferUpdateEvent ended = BufferUpdateEvent.ended();

        assertTrue(started.hasStarted());
        assertFalse(started.hasEnded());
        assertFalse(progress.hasStarted());
        assertFalse(progress.hasEnded());
        assertEquals(0.25f, progress.getProgress(), 0.0001f);
        assertTrue(ended.hasEnded());
        assertFalse(ended.hasStarted());
    }
}
