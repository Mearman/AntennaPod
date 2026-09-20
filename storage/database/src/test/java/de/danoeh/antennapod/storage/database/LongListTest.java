package de.danoeh.antennapod.storage.database;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LongListTest {

    @Test
    public void newListIsEmpty() {
        LongList list = new LongList();
        assertEquals(0, list.size());
        assertEquals(0, list.toArray().length);
    }

    @Test
    public void negativeInitialCapacityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LongList(-1));
    }

    @Test
    public void zeroInitialCapacityStillAcceptsValues() {
        LongList list = new LongList(0);
        list.add(7);
        assertEquals(1, list.size());
        assertEquals(7, list.get(0));
    }

    @Test
    public void ofKeepsValueOrder() {
        LongList list = LongList.of(3, 1, 2);
        assertArrayEquals(new long[] {3, 1, 2}, list.toArray());
    }

    @Test
    public void ofWithoutValuesIsEmpty() {
        assertEquals(0, LongList.of().size());
        assertEquals(0, LongList.of((long[]) null).size());
    }

    @Test
    public void addBeyondInitialCapacityKeepsAllValues() {
        LongList list = new LongList(1);
        for (long i = 0; i < 50; i++) {
            list.add(i * 10);
        }
        assertEquals(50, list.size());
        for (int i = 0; i < 50; i++) {
            assertEquals(i * 10L, list.get(i));
        }
    }

    @Test
    public void getRejectsIndexesOutsideTheList() {
        LongList list = LongList.of(1, 2);
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
    }

    @Test
    public void getDoesNotExposeUnusedCapacity() {
        LongList list = new LongList(10);
        list.add(1);
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1));
    }

    @Test
    public void setReplacesValueAndReturnsPreviousOne() {
        LongList list = LongList.of(5, 6, 7);
        assertEquals(6, list.set(1, 60));
        assertArrayEquals(new long[] {5, 60, 7}, list.toArray());
    }

    @Test
    public void setRejectsIndexesOutsideTheList() {
        LongList list = LongList.of(1);
        assertThrows(IndexOutOfBoundsException.class, () -> list.set(1, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> list.set(-1, 5));
        assertArrayEquals(new long[] {1}, list.toArray());
    }

    @Test
    public void insertShiftsFollowingValuesUp() {
        LongList list = LongList.of(1, 2, 4);
        list.insert(2, 3);
        assertArrayEquals(new long[] {1, 2, 3, 4}, list.toArray());
    }

    @Test
    public void insertAtFrontAndAtEnd() {
        LongList list = LongList.of(2);
        list.insert(0, 1);
        list.insert(2, 3);
        assertArrayEquals(new long[] {1, 2, 3}, list.toArray());
    }

    @Test
    public void insertGrowsFullList() {
        LongList list = LongList.of(1, 2);
        list.insert(1, 9);
        assertArrayEquals(new long[] {1, 9, 2}, list.toArray());
    }

    @Test
    public void insertRejectsIndexesBeyondSizeOrNegative() {
        LongList list = LongList.of(1, 2);
        assertThrows(IndexOutOfBoundsException.class, () -> list.insert(3, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> list.insert(-1, 5));
        assertEquals(2, list.size());
    }

    @Test
    public void removeDeletesOnlyFirstOccurrence() {
        LongList list = LongList.of(1, 2, 1, 3);
        assertTrue(list.remove(1));
        assertArrayEquals(new long[] {2, 1, 3}, list.toArray());
    }

    @Test
    public void removeReportsMissingValue() {
        LongList list = LongList.of(1, 2);
        assertFalse(list.remove(3));
        assertArrayEquals(new long[] {1, 2}, list.toArray());
    }

    @Test
    public void removeAllWithArrayRemovesEachValueOnce() {
        LongList list = LongList.of(1, 2, 3, 2);
        list.removeAll(new long[] {2, 3, 9});
        assertArrayEquals(new long[] {1, 2}, list.toArray());
    }

    @Test
    public void removeAllWithListRemovesEachValueOnce() {
        LongList list = LongList.of(1, 2, 3, 2);
        list.removeAll(LongList.of(2, 3, 9));
        assertArrayEquals(new long[] {1, 2}, list.toArray());
    }

    @Test
    public void removeIndexShiftsFollowingValuesDown() {
        LongList list = LongList.of(1, 2, 3);
        list.removeIndex(1);
        assertArrayEquals(new long[] {1, 3}, list.toArray());
        list.removeIndex(1);
        assertArrayEquals(new long[] {1}, list.toArray());
    }

    @Test
    public void removeIndexRejectsIndexesOutsideTheList() {
        LongList list = LongList.of(1, 2);
        assertThrows(IndexOutOfBoundsException.class, () -> list.removeIndex(2));
        assertThrows(IndexOutOfBoundsException.class, () -> list.removeIndex(-1));
        assertEquals(2, list.size());
    }

    @Test
    public void indexOfReturnsFirstMatchOrMinusOne() {
        LongList list = LongList.of(4, 5, 4);
        assertEquals(0, list.indexOf(4));
        assertEquals(1, list.indexOf(5));
        assertEquals(-1, list.indexOf(6));
    }

    @Test
    public void containsIgnoresUnusedCapacity() {
        LongList list = new LongList(10);
        list.add(1);
        assertTrue(list.contains(1));
        assertFalse(list.contains(0));
    }

    @Test
    public void clearEmptiesListAndAllowsReuse() {
        LongList list = LongList.of(1, 2, 3);
        list.clear();
        assertEquals(0, list.size());
        list.add(8);
        assertArrayEquals(new long[] {8}, list.toArray());
    }

    @Test
    public void toArrayReturnsIndependentCopy() {
        LongList list = LongList.of(1, 2);
        long[] copy = list.toArray();
        copy[0] = 100;
        assertEquals(1, list.get(0));
    }

    @Test
    public void equalityDependsOnValuesNotCapacity() {
        LongList small = LongList.of(1, 2, 3);
        LongList large = new LongList(100);
        large.add(1);
        large.add(2);
        large.add(3);
        assertEquals(small, large);
        assertEquals(small.hashCode(), large.hashCode());
    }

    @Test
    public void listsWithDifferentValuesOrLengthsAreNotEqual() {
        assertNotEquals(LongList.of(1, 2, 3), LongList.of(1, 2, 4));
        assertNotEquals(LongList.of(1, 2, 3), LongList.of(1, 2));
        assertNotEquals(LongList.of(1), "1");
    }

    @Test
    public void listEqualsItself() {
        LongList list = LongList.of(1);
        assertEquals(list, list);
    }

    @Test
    public void hashCodeUsesHighBitsOfValues() {
        assertNotEquals(LongList.of(0L).hashCode(), LongList.of(1L << 32).hashCode());
        assertNotEquals(LongList.of(1, 2).hashCode(), LongList.of(2, 1).hashCode());
    }

    @Test
    public void toStringListsValuesInOrder() {
        assertEquals("LongList{}", new LongList().toString());
        assertEquals("LongList{3, -1, 2}", LongList.of(3, -1, 2).toString());
    }
}
