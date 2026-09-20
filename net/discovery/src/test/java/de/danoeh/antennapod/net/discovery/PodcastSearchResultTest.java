package de.danoeh.antennapod.net.discovery;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PodcastSearchResultTest {

    @Test
    public void testDummyHasEmptyFieldsInsteadOfNulls() {
        PodcastSearchResult dummy = PodcastSearchResult.dummy();
        assertEquals("", dummy.title);
        assertEquals("", dummy.imageUrl);
        assertEquals("", dummy.feedUrl);
        assertEquals("", dummy.author);
    }
}
