package de.danoeh.antennapod.event;

import org.junit.Test;

import java.util.Arrays;

import de.danoeh.antennapod.model.feed.Feed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeedListUpdateEventTest {

    private static Feed feedWithId(long id) {
        Feed feed = new Feed("https://example.com/" + id, null);
        feed.setId(id);
        return feed;
    }

    @Test
    public void eventFromFeedListContainsEveryFeedOfTheList() {
        FeedListUpdateEvent event = new FeedListUpdateEvent(Arrays.asList(feedWithId(1), feedWithId(2)));
        assertTrue(event.contains(feedWithId(1)));
        assertTrue(event.contains(feedWithId(2)));
        assertFalse(event.contains(feedWithId(3)));
    }

    @Test
    public void eventFromSingleFeedContainsOnlyThatFeed() {
        FeedListUpdateEvent event = new FeedListUpdateEvent(feedWithId(4));
        assertTrue(event.contains(feedWithId(4)));
        assertFalse(event.contains(feedWithId(5)));
    }

    @Test
    public void eventFromFeedIdContainsFeedWithThatId() {
        FeedListUpdateEvent event = new FeedListUpdateEvent(6L);
        assertTrue(event.contains(feedWithId(6)));
        assertFalse(event.contains(feedWithId(7)));
    }

    @Test
    public void eventFromEmptyListContainsNoFeed() {
        assertFalse(new FeedListUpdateEvent(Arrays.<Feed>asList()).contains(feedWithId(0)));
    }
}
