package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class FeedMediaEqualityTest {

    private static FeedMedia mediaWithId(long id, String url) {
        FeedMedia media = new FeedMedia(null, url, 0, "audio/mpeg");
        media.setId(id);
        return media;
    }

    @Test
    public void equals_comparesDatabaseIdOnly() {
        FeedMedia first = mediaWithId(5, "http://example.com/a");
        FeedMedia second = mediaWithId(5, "http://example.com/b");
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, mediaWithId(6, "http://example.com/a"));
    }

    @Test
    public void equals_differentTypeOrNull_isFalse() {
        FeedMedia media = mediaWithId(5, "http://example.com/a");
        assertNotEquals(media, null);
        assertNotEquals(media, "media");
    }
}
