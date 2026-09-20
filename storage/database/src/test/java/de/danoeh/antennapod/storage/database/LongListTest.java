package de.danoeh.antennapod.storage.database;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LongListTest {
    private static LongList tenIds() {
        return LongList.of(100, 101, 102, 103, 104, 105, 106, 107, 108, 109);
    }

    @Test
    public void listRejectsAccessOutsideItsBounds() {
        LongList ids = tenIds();

        assertThrows(IndexOutOfBoundsException.class, () -> ids.get(ids.size()));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.set(ids.size(), 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.set(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.removeIndex(ids.size()));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.removeIndex(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.insert(ids.size() + 1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ids.insert(-1, 1));
        assertEquals(10, ids.size());
    }

    @Test
    public void removeAllDropsEveryGivenIdAndIgnoresUnknownOnes() {
        LongList ids = tenIds();

        ids.removeAll(new long[] {100, 101, 102, 5000});

        assertEquals(LongList.of(103, 104, 105, 106, 107, 108, 109), ids);
    }

    @Test
    public void removeAllAcceptsAnotherList() {
        LongList ids = tenIds();

        ids.removeAll(LongList.of(101, 105, 109));

        assertEquals(LongList.of(100, 102, 103, 104, 106, 107, 108), ids);
    }

    @Test
    public void listGrowsBeyondItsInitialCapacityWithoutLosingIds() {
        LongList ids = new LongList(1);

        for (int i = 0; i < 40; i++) {
            ids.add(100000L + i);
        }

        assertEquals(40, ids.size());
        for (int i = 0; i < 40; i++) {
            assertEquals(100000L + i, ids.get(i));
        }
    }

    @Test
    public void insertShiftsFollowingIdsAndSetReplacesInPlace() {
        LongList ids = LongList.of(100, 101, 102);

        ids.insert(1, 9999);
        assertEquals(LongList.of(100, 9999, 101, 102), ids);

        assertEquals(9999, ids.set(1, 8888));
        assertEquals(LongList.of(100, 8888, 101, 102), ids);

        ids.insert(ids.size(), 7777);
        assertEquals(LongList.of(100, 8888, 101, 102, 7777), ids);
    }

    @Test
    public void removeIndexShiftsFollowingIds() {
        LongList ids = tenIds();

        ids.removeIndex(0);
        ids.removeIndex(ids.size() - 1);
        ids.removeIndex(3);

        assertEquals(LongList.of(101, 102, 103, 105, 106, 107, 108), ids);
    }

    @Test
    public void removeReportsWhetherTheIdWasPresent() {
        LongList ids = LongList.of(100, 101);

        assertTrue(ids.remove(100));
        assertFalse(ids.remove(100));
        assertEquals(LongList.of(101), ids);
    }

    @Test
    public void clearedListIsEmptyAndReusable() {
        LongList ids = tenIds();

        ids.clear();

        assertEquals(0, ids.size());
        assertEquals(-1, ids.indexOf(100));
        ids.add(102);
        assertEquals(LongList.of(102), ids);
    }

    @Test
    public void listsWithSameIdsInSameOrderAreEqualAndHaveSameHash() {
        LongList first = tenIds();
        LongList second = tenIds();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void listsDifferingInAnyIdOrInOrderAreNotEqual() {
        LongList original = LongList.of(100, 101, 102);

        assertNotEquals(original, LongList.of(100, 101));
        assertNotEquals(original, LongList.of(100, 101, 103));
        assertNotEquals(original, LongList.of(102, 101, 100));
        assertNotEquals(original.hashCode(), LongList.of(102, 101, 100).hashCode());
        assertNotEquals(original, "not a list");
        assertFalse(original.equals(null));
    }

    @Test
    public void listDescribesItsIds() {
        assertEquals("LongList{100, 101}", LongList.of(100, 101).toString());
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
    public void lookupFindsPositionOfIds() {
        LongList ids = tenIds();

        assertTrue(ids.contains(103));
        assertEquals(3, ids.indexOf(103));
        assertFalse(ids.contains(5000));
        assertEquals(-1, ids.indexOf(5000));
    }
}
