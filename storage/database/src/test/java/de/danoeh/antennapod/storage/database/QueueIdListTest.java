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
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class QueueIdListTest extends DatabaseTestBase {
    private static final int QUEUED_EPISODES = 12;

    private final List<FeedItem> queued = new ArrayList<>();

    @Before
    public void fillQueue() {
        Feed feed = storeFeed("feed");
        for (int i = 0; i < QUEUED_EPISODES; i++) {
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
    public void queueIdListFollowsQueuePositionsRatherThanEpisodeIds() {
        await(DBWriter.clearQueue());

        await(DBWriter.addQueueItem(context, queued.get(5), queued.get(2), queued.get(9)));

        assertEquals(LongList.of(idAt(5), idAt(2), idAt(9)), DBReader.getQueueIDList());
    }

    @Test
    public void queueIdListSupportsLookupOfStoredEpisodes() {
        LongList ids = DBReader.getQueueIDList();

        assertTrue(ids.contains(idAt(3)));
        assertEquals(3, ids.indexOf(idAt(3)));
        assertFalse(ids.contains(idAt(3) + 1000));
        assertEquals(-1, ids.indexOf(idAt(3) + 1000));
        assertEquals(idAt(5), ids.get(5));
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
    public void queueIdListIsEmptyForEmptyQueue() {
        await(DBWriter.clearQueue());

        assertEquals(0, DBReader.getQueueIDList().size());
    }
}
