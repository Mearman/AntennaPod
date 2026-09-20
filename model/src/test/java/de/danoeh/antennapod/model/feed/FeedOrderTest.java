package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class FeedOrderTest {

    @Test
    public void fromOrdinal_knownIds_returnMatchingOrder() {
        for (FeedOrder order : FeedOrder.values()) {
            assertEquals(order, FeedOrder.fromOrdinal(order.id));
        }
    }

    @Test
    public void fromOrdinal_unknownId_fallsBackToMostRecentEpisode() {
        assertEquals(FeedOrder.MOST_RECENT_EPISODE, FeedOrder.fromOrdinal(-1));
        assertEquals(FeedOrder.MOST_RECENT_EPISODE, FeedOrder.fromOrdinal(42));
    }

    @Test
    public void ids_areUnique() {
        long distinct = Arrays.stream(FeedOrder.values()).mapToInt(o -> o.id).distinct().count();
        assertEquals(FeedOrder.values().length, distinct);
    }
}
