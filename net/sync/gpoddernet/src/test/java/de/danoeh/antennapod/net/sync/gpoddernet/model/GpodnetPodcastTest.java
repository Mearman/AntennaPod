package de.danoeh.antennapod.net.sync.gpoddernet.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GpodnetPodcastTest {
    @Test
    public void keepsAllValuesItWasCreatedWith() {
        GpodnetPodcast podcast = new GpodnetPodcast("http://feed.example/rss", "Title", "Description", 42,
                "http://logo.example/logo.png", "http://site.example", "http://mygpo.example/podcast", "Author");

        assertEquals("http://feed.example/rss", podcast.getUrl());
        assertEquals("Title", podcast.getTitle());
        assertEquals("Description", podcast.getDescription());
        assertEquals(42, podcast.getSubscribers());
        assertEquals("http://logo.example/logo.png", podcast.getLogoUrl());
        assertEquals("http://site.example", podcast.getWebsite());
        assertEquals("http://mygpo.example/podcast", podcast.getMygpoLink());
        assertEquals("Author", podcast.getAuthor());
    }

    @Test
    public void optionalValuesMayBeAbsent() {
        GpodnetPodcast podcast = new GpodnetPodcast("http://feed.example/rss", "Title", "Description", 0,
                null, null, null, null);

        assertNull(podcast.getLogoUrl());
        assertNull(podcast.getWebsite());
        assertNull(podcast.getMygpoLink());
        assertNull(podcast.getAuthor());
    }

    @Test
    public void descriptionContainsUrlTitleAndSubscriberCount() {
        GpodnetPodcast podcast = new GpodnetPodcast("http://feed.example/rss", "Title", "Description", 42,
                null, null, null, null);

        String description = podcast.toString();

        assertTrue(description.contains("http://feed.example/rss"));
        assertTrue(description.contains("Title"));
        assertTrue(description.contains("subscribers=42"));
    }
}
