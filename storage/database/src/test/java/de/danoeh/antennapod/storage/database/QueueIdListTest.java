package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class QueueIdListTest extends DatabaseTestBase {
    private static final int EPISODES_BEYOND_INITIAL_CAPACITY = 12;

    private final List<FeedItem> queued = new ArrayList<>();

    @Before
    public void fillQueue() {
        Feed feed = storeFeed("feed");
        for (int i = 0; i < EPISODES_BEYOND_INITIAL_CAPACITY; i++) {
            queued.add(storeItem(feed, "episode " + i));
        }
        await(DBWriter.addQueueItem(context, queued.toArray(new FeedItem[0])));
    }

    private long idAt(int position) {
        return queued.get(position).getId();
    }

    private long[] allIds() {
        long[] ids = new long[queued.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = idAt(i);
        }
        return ids;
    }

    @Test
    public void queueIdListHasOneEntryPerQueuedEpisodeInQueueOrder() {
        LongList ids = DBReader.getQueueIDList();

        assertEquals(queued.size(), ids.size());
        assertArrayEquals(allIds(), ids.toArray());
    }

    @Test
    public void queueIdListSupportsLookup() {
        LongList ids = DBReader.getQueueIDList();

        assertTrue(ids.contains(idAt(3)));
        assertEquals(3, ids.indexOf(idAt(3)));
        assertFalse(ids.contains(idAt(3) + 1000));
        assertEquals(-1, ids.indexOf(idAt(3) + 1000));
        assertEquals(idAt(5), ids.get(5));
    }

    @Test
    public void queueIdListRejectsAccessOutsideItsBounds() {
        LongList ids = DBReader.getQueueIDList();

        assertThrows(IndexOutOfBoundsException.class, () -> ids.get(ids.size()));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.set(ids.size(), 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.set(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.removeIndex(ids.size()));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.removeIndex(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.insert(ids.size() + 1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.insert(-1, 1));
    }

    @Test
    public void removingIdsFromListDoesNotTouchStoredQueue() {
        LongList ids = DBReader.getQueueIDList();

        assertTrue(ids.remove(idAt(0)));
        assertFalse(ids.remove(idAt(0)));

        assertEquals(queued.size() - 1, ids.size());
        assertEquals(idAt(1), ids.get(0));
        assertEquals(queued.size(), DBReader.getQueueIDList().size());
    }

    @Test
    public void reconcilingQueueRemovesEpisodesNoLongerWantedFromStoredQueue() {
        LongList ids = DBReader.getQueueIDList();
        LongList wanted = LongList.of(idAt(1), idAt(4), idAt(7));
        LongList toRemove = DBReader.getQueueIDList();
        toRemove.removeAll(wanted);

        await(DBWriter.removeQueueItem(context, false, toRemove.toArray()));

        assertEquals(queued.size() - wanted.size(), toRemove.size());
        assertEquals(wanted, DBReader.getQueueIDList());
        assertEquals(queued.size(), ids.size());
    }

    @Test
    public void removeAllAcceptsPlainArrays() {
        LongList ids = DBReader.getQueueIDList();

        ids.removeAll(new long[] {idAt(0), idAt(1), idAt(2), idAt(2) + 5000});

        assertEquals(queued.size() - 3, ids.size());
        assertEquals(idAt(3), ids.get(0));
    }

    @Test
    public void listGrowsBeyondItsInitialCapacity() {
        LongList ids = DBReader.getQueueIDList();
        int sizeBefore = ids.size();

        for (int i = 0; i < 40; i++) {
            ids.add(100000L + i);
        }

        assertEquals(sizeBefore + 40, ids.size());
        assertEquals(idAt(0), ids.get(0));
        assertEquals(100039L, ids.get(ids.size() - 1));
        assertTrue(ids.contains(100020L));
    }

    @Test
    public void insertShiftsFollowingIdsAndSetReplacesInPlace() {
        LongList ids = DBReader.getQueueIDList();

        ids.insert(1, 9999);
        assertEquals(idAt(0), ids.get(0));
        assertEquals(9999, ids.get(1));
        assertEquals(idAt(1), ids.get(2));
        assertEquals(queued.size() + 1, ids.size());

        assertEquals(9999, ids.set(1, 8888));
        assertEquals(8888, ids.get(1));

        ids.insert(ids.size(), 7777);
        assertEquals(7777, ids.get(ids.size() - 1));
    }

    @Test
    public void removeIndexShiftsFollowingIds() {
        LongList ids = DBReader.getQueueIDList();

        ids.removeIndex(0);

        assertEquals(queued.size() - 1, ids.size());
        assertEquals(idAt(1), ids.get(0));
        ids.removeIndex(ids.size() - 1);
        assertEquals(queued.size() - 2, ids.size());
        assertEquals(idAt(queued.size() - 2), ids.get(ids.size() - 1));
    }

    @Test
    public void clearedListIsEmptyAndReusable() {
        LongList ids = DBReader.getQueueIDList();

        ids.clear();

        assertEquals(0, ids.size());
        assertEquals(-1, ids.indexOf(idAt(0)));
        ids.add(idAt(2));
        assertEquals(1, ids.size());
        assertEquals(idAt(2), ids.get(0));
    }

    @Test
    public void listsWithSameIdsAreEqualAndHaveSameHash() {
        LongList first = DBReader.getQueueIDList();
        LongList second = LongList.of(allIds());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        second.remove(idAt(0));
        assertNotEquals(first, second);
        assertNotEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, null);
        assertNotEquals(first, "not a list");
        assertEquals(first, first);
    }

    @Test
    public void listDescribesItsIds() {
        LongList ids = LongList.of(idAt(0), idAt(1));

        assertEquals("LongList{" + idAt(0) + ", " + idAt(1) + "}", ids.toString());
        assertEquals("LongList{}", new LongList().toString());
    }

    @Test
    public void listCreatedWithoutIdsIsEmpty() {
        assertEquals(0, LongList.of().size());
        assertEquals(0, LongList.of((long[]) null).size());
        assertEquals(0, new LongList(0).size());
        assertThrows(IllegalArgumentException.class, () -> new LongList(-1));
    }

    @Test
    public void queueIdListIsEmptyForEmptyQueue() {
        await(DBWriter.clearQueue());

        assertEquals(0, DBReader.getQueueIDList().size());
    }
}
