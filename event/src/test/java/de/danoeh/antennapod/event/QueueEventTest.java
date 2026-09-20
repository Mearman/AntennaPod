package de.danoeh.antennapod.event;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class QueueEventTest {
    private final FeedItem item = new FeedItem();
    private final List<FeedItem> items = Arrays.asList(new FeedItem(), new FeedItem());

    @Test
    public void addedEventCarriesItemAndInsertPosition() {
        QueueEvent event = QueueEvent.added(item, 3);
        assertEquals(QueueEvent.Action.ADDED, event.action);
        assertSame(item, event.item);
        assertEquals(3, event.position);
        assertNull(event.items);
    }

    @Test
    public void setQueueEventCarriesWholeQueueWithoutPosition() {
        QueueEvent event = QueueEvent.setQueue(items);
        assertEquals(QueueEvent.Action.SET_QUEUE, event.action);
        assertSame(items, event.items);
        assertNull(event.item);
        assertEquals(-1, event.position);
    }

    @Test
    public void removedEventsCarryTheRemovedItemAndDifferInAction() {
        QueueEvent removed = QueueEvent.removed(item);
        QueueEvent irreversible = QueueEvent.irreversibleRemoved(item);
        assertEquals(QueueEvent.Action.REMOVED, removed.action);
        assertEquals(QueueEvent.Action.IRREVERSIBLE_REMOVED, irreversible.action);
        assertSame(item, removed.item);
        assertSame(item, irreversible.item);
        assertEquals(-1, removed.position);
    }

    @Test
    public void clearedEventCarriesNoPayload() {
        QueueEvent event = QueueEvent.cleared();
        assertEquals(QueueEvent.Action.CLEARED, event.action);
        assertNull(event.item);
        assertNull(event.items);
        assertEquals(-1, event.position);
    }

    @Test
    public void sortedEventCarriesSortedQueue() {
        QueueEvent event = QueueEvent.sorted(items);
        assertEquals(QueueEvent.Action.SORTED, event.action);
        assertSame(items, event.items);
        assertNull(event.item);
    }

    @Test
    public void movedEventCarriesItemAndNewPosition() {
        QueueEvent event = QueueEvent.moved(item, 5);
        assertEquals(QueueEvent.Action.MOVED, event.action);
        assertSame(item, event.item);
        assertEquals(5, event.position);
        assertNull(event.items);
    }
}
