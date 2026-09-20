package de.danoeh.antennapod.net.discovery;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PodcastSearchResultTest {

    @Test
    public void testConstructorKeepsAllFields() {
        PodcastSearchResult result = new PodcastSearchResult("Title", "https://img.example/a.jpg",
                "https://feeds.example/a.xml", "Author");
        assertEquals("Title", result.title);
        assertEquals("https://img.example/a.jpg", result.imageUrl);
        assertEquals("https://feeds.example/a.xml", result.feedUrl);
        assertEquals("Author", result.author);
    }

    @Test
    public void testOptionalFieldsCanBeNull() {
        PodcastSearchResult result = new PodcastSearchResult("Title", null, null, null);
        assertEquals("Title", result.title);
        assertNull(result.imageUrl);
        assertNull(result.feedUrl);
        assertNull(result.author);
    }

    @Test
    public void testDummyHasEmptyFieldsInsteadOfNulls() {
        PodcastSearchResult dummy = PodcastSearchResult.dummy();
        assertEquals("", dummy.title);
        assertEquals("", dummy.imageUrl);
        assertEquals("", dummy.feedUrl);
        assertEquals("", dummy.author);
    }
}
