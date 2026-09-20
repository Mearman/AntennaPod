package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NavDrawerDataTest {

    @Test
    public void constructorKeepsEveryValueInItsOwnField() {
        List<Feed> feeds = Collections.singletonList(new Feed("url", null, "Feed"));
        List<NavDrawerData.TagItem> tags = Collections.singletonList(new NavDrawerData.TagItem("Tag"));
        Map<Long, Integer> counters = Collections.singletonMap(5L, 9);

        NavDrawerData data = new NavDrawerData(feeds, tags, 1, 2, 3, counters);

        assertSame(feeds, data.feeds);
        assertSame(tags, data.tags);
        assertEquals(1, data.queueSize);
        assertEquals(2, data.numNewItems);
        assertEquals(3, data.numDownloadedItems);
        assertSame(counters, data.feedCounters);
    }

    @Test
    public void tagItemIsClosedAndEmptyInitially() {
        NavDrawerData.TagItem tag = new NavDrawerData.TagItem("Tag");
        assertEquals("Tag", tag.getTitle());
        assertFalse(tag.isOpen());
        assertEquals(0, tag.getCounter());
        assertTrue(tag.getFeeds().isEmpty());
    }

    @Test
    public void tagItemCanBeOpenedAndClosed() {
        NavDrawerData.TagItem tag = new NavDrawerData.TagItem("Tag");
        tag.setOpen(true);
        assertTrue(tag.isOpen());
        tag.setOpen(false);
        assertFalse(tag.isOpen());
    }

    @Test
    public void addingFeedsAccumulatesTheirCountersAndKeepsOrder() {
        NavDrawerData.TagItem tag = new NavDrawerData.TagItem("Tag");
        Feed first = new Feed("first", null);
        Feed second = new Feed("second", null);
        tag.addFeed(first, 3);
        tag.addFeed(second, 4);
        assertEquals(7, tag.getCounter());
        assertEquals(List.of(first, second), tag.getFeeds());
    }

    @Test
    public void tagIdIsDerivedFromNameAndLeavesRoomForFeedIds() {
        NavDrawerData.TagItem tag = new NavDrawerData.TagItem("Tag");
        assertEquals(tag.getId(), new NavDrawerData.TagItem("Tag").getId());
        assertNotEquals(tag.getId(), new NavDrawerData.TagItem("Other").getId());
        assertEquals(Math.abs((long) "Tag".hashCode()) << 20, tag.getId());
        assertEquals(0, tag.getId() % (1 << 20));
    }
}
