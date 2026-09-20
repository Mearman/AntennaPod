package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FeedCounterTest {

    @Test
    public void fromOrdinal_knownIds_returnMatchingCounter() {
        for (FeedCounter counter : FeedCounter.values()) {
            assertEquals(counter, FeedCounter.fromOrdinal(counter.id));
        }
    }

    @Test
    public void fromOrdinal_unknownId_fallsBackToShowNone() {
        assertEquals(FeedCounter.SHOW_NONE, FeedCounter.fromOrdinal(0));
        assertEquals(FeedCounter.SHOW_NONE, FeedCounter.fromOrdinal(99));
    }
}
