package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StatisticsItemTest {

    @Test
    public void constructorAssignsEachPositionalArgumentToItsField() {
        Feed feed = new Feed("url", null, "Feed");

        StatisticsItem item = new StatisticsItem(feed, 1, 2, 3, 4, 5, 6, true);

        assertSame(feed, item.feed);
        assertEquals(1, item.time);
        assertEquals(2, item.timePlayed);
        assertEquals(3, item.episodes);
        assertEquals(4, item.episodesStarted);
        assertEquals(5, item.totalDownloadSize);
        assertEquals(6, item.episodesDownloadCount);
        assertTrue(item.hasRecentUnplayed);
    }

    @Test
    public void recentUnplayedFlagIsKeptWhenFalse() {
        StatisticsItem item = new StatisticsItem(new Feed("url", null), 0, 0, 0, 0, 0, 0, false);
        assertFalse(item.hasRecentUnplayed);
    }
}
