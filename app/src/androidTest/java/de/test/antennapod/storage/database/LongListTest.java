package de.test.antennapod.storage.database;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.LongList;
import de.test.antennapod.service.download.DownloadTestFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LongListTest {
    private static final int EPISODES = 5;

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Feed feed;
    private LongList queueIds;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        feed = fixture.subscribe("Ids", EPISODES);
        for (int i = 0; i < EPISODES; i++) {
            DBWriter.addQueueItem(context, feed.getItemAtIndex(i)).get();
        }
        queueIds = DBReader.getQueueIDList();
    }

    @After
    public void tearDown() throws Exception {
        fixture.tearDown();
    }

    private long id(int index) {
        return feed.getItemAtIndex(index).getId();
    }

    @Test
    public void listContainsTheQueueInOrder() {
        assertEquals(EPISODES, queueIds.size());
        assertArrayEquals(new long[] {id(0), id(1), id(2), id(3), id(4)}, queueIds.toArray());
        assertEquals(2, queueIds.indexOf(id(2)));
        assertEquals(-1, queueIds.indexOf(-1));
        assertTrue(queueIds.contains(id(4)));
        assertFalse(queueIds.contains(-1));
    }

    @Test
    public void valuesCanBeRemovedByValueAndByIndex() {
        assertTrue(queueIds.remove(id(1)));
        assertFalse(queueIds.remove(id(1)));
        queueIds.removeIndex(0);

        assertArrayEquals(new long[] {id(2), id(3), id(4)}, queueIds.toArray());
    }

    @Test
    public void severalValuesCanBeRemovedAtOnce() {
        queueIds.removeAll(new long[] {id(0), id(4)});
        queueIds.removeAll(LongList.of(id(2)));

        assertArrayEquals(new long[] {id(1), id(3)}, queueIds.toArray());
    }

    @Test
    public void valuesCanBeInsertedAndReplaced() {
        queueIds.insert(1, 99);
        assertEquals(id(0), queueIds.get(0));
        assertEquals(99, queueIds.get(1));
        assertEquals(EPISODES + 1, queueIds.size());

        assertEquals(99, queueIds.set(1, 77));
        assertEquals(77, queueIds.get(1));
        queueIds.add(5);
        assertEquals(5, queueIds.get(queueIds.size() - 1));
    }

    @Test
    public void listsAreEqualWhenTheyHoldTheSameValues() {
        LongList copy = new LongList();
        for (int i = 0; i < EPISODES; i++) {
            copy.add(id(i));
        }

        assertEquals(queueIds, copy);
        assertEquals(queueIds.hashCode(), copy.hashCode());
        assertEquals(queueIds.toString(), copy.toString());
        copy.removeIndex(0);
        assertFalse(queueIds.equals(copy));
    }

    @Test
    public void listCanBeClearedAndGrows() {
        queueIds.clear();
        assertEquals(0, queueIds.size());

        LongList grown = new LongList(1);
        for (int i = 0; i < 100; i++) {
            grown.add(i);
        }
        assertEquals(100, grown.size());
        assertEquals(99, grown.get(99));
    }

    @Test
    public void readingOutsideTheListIsRejected() {
        assertThrows(IndexOutOfBoundsException.class, () -> queueIds.get(EPISODES));
        assertThrows(IndexOutOfBoundsException.class, () -> queueIds.get(-1));
    }

    @Test
    public void writingOutsideTheListIsRejected() {
        assertThrows(IndexOutOfBoundsException.class, () -> queueIds.set(EPISODES, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> queueIds.insert(EPISODES + 1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> queueIds.removeIndex(EPISODES));
        assertEquals(EPISODES, queueIds.size());
    }
}
