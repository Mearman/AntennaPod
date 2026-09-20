package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeedAccessorsTest {
    private Feed feed;

    @Before
    public void setUp() {
        feed = FeedMother.anyFeed();
    }

    @Test
    public void setLastUpdateFailed_canBeSetAndCleared() {
        feed.setLastUpdateFailed(true);
        assertTrue(feed.hasLastUpdateFailed());
        feed.setLastUpdateFailed(false);
        assertFalse(feed.hasLastUpdateFailed());
    }

    @Test
    public void setPaged_canBeSetAndCleared() {
        feed.setPaged(true);
        assertTrue(feed.isPaged());
        feed.setPaged(false);
        assertFalse(feed.isPaged());
    }
}
