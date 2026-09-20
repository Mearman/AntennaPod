package de.danoeh.antennapod.model.feed;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
public class SortOrderTest {

    @Test
    public void codes_areUnique() {
        long distinct = Arrays.stream(SortOrder.values()).mapToInt(order -> order.code).distinct().count();
        assertEquals(SortOrder.values().length, distinct);
    }

    @Test
    public void parseWithDefault_knownName_returnsMatchingOrder() {
        assertEquals(SortOrder.DATE_NEW_OLD, SortOrder.parseWithDefault("DATE_NEW_OLD", SortOrder.RANDOM));
    }

    @Test
    public void parseWithDefault_unknownName_returnsDefault() {
        assertEquals(SortOrder.RANDOM, SortOrder.parseWithDefault("NOT_A_SORT_ORDER", SortOrder.RANDOM));
    }

    @Test
    public void fromCodeString_nullOrEmpty_returnsNull() {
        assertNull(SortOrder.fromCodeString(null));
        assertNull(SortOrder.fromCodeString(""));
    }

    @Test
    public void fromCodeString_knownCode_returnsMatchingOrder() {
        assertEquals(SortOrder.DATE_OLD_NEW, SortOrder.fromCodeString("1"));
        assertEquals(SortOrder.COMPLETION_DATE_NEW_OLD, SortOrder.fromCodeString("106"));
    }

    @Test
    public void fromCodeString_unknownCode_throws() {
        assertThrows(IllegalArgumentException.class, () -> SortOrder.fromCodeString("999"));
    }

    @Test
    public void fromCodeString_nonNumericCode_throws() {
        assertThrows(NumberFormatException.class, () -> SortOrder.fromCodeString("abc"));
    }

    @Test
    public void toCodeString_null_returnsNull() {
        assertNull(SortOrder.toCodeString(null));
    }

    @Test
    public void codeString_roundTripsForEveryOrder() {
        for (SortOrder order : SortOrder.values()) {
            assertEquals(order, SortOrder.fromCodeString(SortOrder.toCodeString(order)));
        }
    }
}
