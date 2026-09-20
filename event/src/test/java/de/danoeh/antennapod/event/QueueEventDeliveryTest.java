package de.danoeh.antennapod.event;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.SortOrder;
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

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class QueueEventDeliveryTest {
    private Context context;
    private final EventRecorder recorder = new EventRecorder();
    private List<FeedItem> items;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        EventTestDatabase.setUp(context);
        Feed feed = EventTestDatabase.storeFeed(context, "http://example.com/feed", "Feed", 3);
        items = EventTestDatabase.itemsOf(feed);
        recorder.register();
    }

    @After
    public void tearDown() {
        recorder.unregister();
        EventTestDatabase.tearDown();
    }

    @Test
    public void addingToTheQueuePublishesOneAddedEventPerItemWithItsInsertPosition()
            throws ExecutionException, InterruptedException {
        DBWriter.addQueueItem(context, items.get(0), items.get(1)).get();

        List<QueueEvent> events = recorder.of(QueueEvent.class);
        assertEquals(2, events.size());
        assertEquals(QueueEvent.Action.ADDED, events.get(0).action);
        assertEquals(items.get(0).getId(), events.get(0).item.getId());
        assertEquals(0, events.get(0).position);
        assertEquals(QueueEvent.Action.ADDED, events.get(1).action);
        assertEquals(items.get(1).getId(), events.get(1).item.getId());
        assertEquals(1, events.get(1).position);
        assertEquals(2, DBReader.getQueue().size());
    }

    @Test
    public void addingAnItemThatIsAlreadyQueuedPublishesNoFurtherEvent()
            throws ExecutionException, InterruptedException {
        DBWriter.addQueueItem(context, items.get(0)).get();
        recorder.clear();

        DBWriter.addQueueItem(context, items.get(0)).get();

        assertTrue(recorder.of(QueueEvent.class).isEmpty());
        assertEquals(1, DBReader.getQueue().size());
    }

    @Test
    public void removingFromTheQueuePublishesRemovedEventCarryingTheStoredItem()
            throws ExecutionException, InterruptedException {
        DBWriter.addQueueItem(context, items.get(0), items.get(1)).get();
        recorder.clear();

        DBWriter.removeQueueItem(context, false, items.get(0)).get();

        QueueEvent event = recorder.single(QueueEvent.class);
        assertEquals(QueueEvent.Action.REMOVED, event.action);
        assertEquals(items.get(0).getId(), event.item.getId());
        assertEquals(-1, event.position);
        assertNull(event.items);
        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(1, queue.size());
        assertEquals(items.get(1).getId(), queue.get(0).getId());
    }

    @Test
    public void removingAnItemThatIsNotQueuedPublishesNoEvent()
            throws ExecutionException, InterruptedException {
        DBWriter.addQueueItem(context, items.get(0)).get();
        recorder.clear();

        DBWriter.removeQueueItem(context, false, items.get(2)).get();

        assertTrue(recorder.of(QueueEvent.class).isEmpty());
        assertEquals(1, DBReader.getQueue().size());
    }

    @Test
    public void clearingTheQueuePublishesAClearedEventWithoutAnyPayload()
            throws ExecutionException, InterruptedException {
        DBWriter.addQueueItem(context, items.get(0), items.get(1)).get();
        recorder.clear();

        DBWriter.clearQueue().get();

        QueueEvent event = recorder.single(QueueEvent.class);
        assertEquals(QueueEvent.Action.CLEARED, event.action);
        assertNull(event.item);
        assertNull(event.items);
        assertEquals(-1, event.position);
        assertTrue(DBReader.getQueue().isEmpty());
    }

    @Test
    public void movingAQueueItemPublishesMovedEventWithItsNewPosition()
            throws ExecutionException, InterruptedException {
        DBWriter.addQueueItem(context, items.get(0), items.get(1), items.get(2)).get();
        recorder.clear();

        DBWriter.moveQueueItem(0, 2, true).get();

        QueueEvent event = recorder.single(QueueEvent.class);
        assertEquals(QueueEvent.Action.MOVED, event.action);
        assertEquals(items.get(0).getId(), event.item.getId());
        assertEquals(2, event.position);
        assertEquals(items.get(0).getId(), DBReader.getQueue().get(2).getId());
    }

    @Test
    public void sortingTheQueuePublishesSortedEventCarryingTheWholeNewOrder()
            throws ExecutionException, InterruptedException {
        DBWriter.addQueueItem(context, items.get(0), items.get(1), items.get(2)).get();
        recorder.clear();

        DBWriter.reorderQueue(SortOrder.DATE_NEW_OLD, true).get();

        QueueEvent event = recorder.single(QueueEvent.class);
        assertEquals(QueueEvent.Action.SORTED, event.action);
        assertNull(event.item);
        assertEquals(3, event.items.size());
        assertTrue(event.items.get(0).getPubDate().after(event.items.get(1).getPubDate()));
        assertTrue(event.items.get(1).getPubDate().after(event.items.get(2).getPubDate()));
        List<FeedItem> queue = DBReader.getQueue();
        for (int i = 0; i < queue.size(); i++) {
            assertEquals(event.items.get(i).getId(), queue.get(i).getId());
        }
    }

    @Test
    public void sortingTheQueueWithoutBroadcastPublishesNoEventButStillReordersTheDatabase()
            throws ExecutionException, InterruptedException {
        DBWriter.addQueueItem(context, items.get(0), items.get(1), items.get(2)).get();
        recorder.clear();

        DBWriter.reorderQueue(SortOrder.DATE_NEW_OLD, false).get();

        assertTrue(recorder.of(QueueEvent.class).isEmpty());
        List<FeedItem> queue = DBReader.getQueue();
        assertTrue(queue.get(0).getPubDate().after(queue.get(1).getPubDate()));
        assertTrue(queue.get(1).getPubDate().after(queue.get(2).getPubDate()));
    }

    @Test
    public void irreversibleRemovedCarriesTheItemAndNoPosition() {
        QueueEvent event = QueueEvent.irreversibleRemoved(items.get(1));

        assertEquals(QueueEvent.Action.IRREVERSIBLE_REMOVED, event.action);
        assertEquals(items.get(1).getId(), event.item.getId());
        assertEquals(-1, event.position);
        assertNull(event.items);
    }

    @Test
    public void setQueueCarriesTheWholeListAndNoSingleItem() {
        QueueEvent event = QueueEvent.setQueue(items);

        assertEquals(QueueEvent.Action.SET_QUEUE, event.action);
        assertNull(event.item);
        assertEquals(3, event.items.size());
        assertEquals(-1, event.position);
    }

    @Test
    public void queuedItemsAreTaggedAsQueuedAndLoseTheTagWhenRemoved()
            throws ExecutionException, InterruptedException {
        DBWriter.addQueueItem(context, items.get(0)).get();
        QueueEvent added = recorder.single(QueueEvent.class);
        assertTrue(added.item.isTagged(FeedItem.TAG_QUEUE));
        recorder.clear();

        DBWriter.removeQueueItem(context, false, items.get(0)).get();

        assertFalse(recorder.single(QueueEvent.class).item.isTagged(FeedItem.TAG_QUEUE));
    }
}
