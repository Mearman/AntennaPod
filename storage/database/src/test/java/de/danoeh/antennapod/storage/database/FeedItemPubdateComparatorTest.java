package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.FeedItem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FeedItemPubdateComparatorTest {
    private final FeedItemPubdateComparator comparator = new FeedItemPubdateComparator();

    @Test
    public void newerItemSortsBeforeOlderItem() {
        FeedItem older = itemWithDate(1000);
        FeedItem newer = itemWithDate(2000);
        assertTrue(comparator.compare(newer, older) < 0);
        assertTrue(comparator.compare(older, newer) > 0);
    }

    @Test
    public void itemsWithSameDateCompareAsEqual() {
        assertEquals(0, comparator.compare(itemWithDate(1000), itemWithDate(1000)));
    }

    @Test
    public void itemsWithoutDatesCompareAsEqual() {
        assertEquals(0, comparator.compare(itemWithDate(null), itemWithDate(null)));
    }

    @Test
    public void sortingPutsNewestFirst() {
        FeedItem oldest = itemWithDate(1000);
        FeedItem middle = itemWithDate(2000);
        FeedItem newest = itemWithDate(3000);
        List<FeedItem> items = new ArrayList<>(List.of(oldest, newest, middle));
        Collections.sort(items, comparator);
        assertSame(newest, items.get(0));
        assertSame(middle, items.get(1));
        assertSame(oldest, items.get(2));
    }

    private FeedItem itemWithDate(Integer millis) {
        FeedItem item = new FeedItem();
        item.setPubDate(millis == null ? null : new Date(millis));
        return item;
    }
}
