package de.danoeh.antennapod.event;

import android.content.Context;
import androidx.core.util.Consumer;
import de.danoeh.antennapod.event.playback.PlaybackHistoryEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MessageAndLogEventsTest {

    @Test
    public void plainMessageHasNoAction() {
        MessageEvent event = new MessageEvent("Saved");
        assertEquals("Saved", event.message);
        assertNull(event.action);
        assertNull(event.actionText);
    }

    @Test
    public void messageWithActionKeepsActionAndItsLabel() {
        Consumer<Context> action = context -> { };
        MessageEvent event = new MessageEvent("Deleted", action, "Undo");
        assertEquals("Deleted", event.message);
        assertSame(action, event.action);
        assertEquals("Undo", event.actionText);
    }

    @Test
    public void feedEventDescribesActionAndFeed() {
        FeedEvent event = new FeedEvent(FeedEvent.Action.SORT_ORDER_CHANGED, 12);
        assertEquals(12, event.feedId);
        assertEquals("FeedEvent{action=SORT_ORDER_CHANGED, feedId=12}", event.toString());
    }

    @Test
    public void logEventsHaveDescriptiveNames() {
        assertEquals("DownloadLogEvent", DownloadLogEvent.listUpdated().toString());
        assertEquals("PlaybackHistoryEvent", PlaybackHistoryEvent.listUpdated().toString());
    }

    @Test
    public void playerErrorEventCarriesMessage() {
        assertEquals("Codec missing", new PlayerErrorEvent("Codec missing").getMessage());
    }

    @Test
    public void syncServiceEventCarriesMessageResource() {
        assertEquals(1234, new SyncServiceEvent(1234).getMessageResId());
    }

    @Test
    public void feedUpdateRunningEventCarriesRunningState() {
        assertTrue(new FeedUpdateRunningEvent(true).isFeedUpdateRunning);
        assertFalse(new FeedUpdateRunningEvent(false).isFeedUpdateRunning);
    }
}
