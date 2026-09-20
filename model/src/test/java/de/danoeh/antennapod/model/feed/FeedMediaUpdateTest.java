package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeedMediaUpdateTest {
    private FeedMedia media;

    @Before
    public void setUp() {
        media = new FeedMedia(null, "http://example.com/episode", 1000, "audio/mpeg");
        media.setDuration(60000);
    }

    private static FeedMedia otherMedia(String url, long size, String mimeType, int duration) {
        FeedMedia other = new FeedMedia(null, url, size, mimeType);
        other.setDuration(duration);
        return other;
    }

    @Test
    public void updateFromOther_alwaysTakesDownloadUrl() {
        media.updateFromOther(otherMedia("http://example.com/moved", 0, null, 0));
        assertEquals("http://example.com/moved", media.getDownloadUrl());
    }

    @Test
    public void updateFromOther_takesPositiveSizeOnly() {
        media.updateFromOther(otherMedia("http://example.com/episode", 0, null, 0));
        assertEquals(1000, media.getSize());
        media.updateFromOther(otherMedia("http://example.com/episode", -5, null, 0));
        assertEquals(1000, media.getSize());
        media.updateFromOther(otherMedia("http://example.com/episode", 2000, null, 0));
        assertEquals(2000, media.getSize());
    }

    @Test
    public void updateFromOther_doesNotOverwriteKnownDuration() {
        media.updateFromOther(otherMedia("http://example.com/episode", 0, null, 90000));
        assertEquals(60000, media.getDuration());
    }

    @Test
    public void updateFromOther_takesDurationWhenOwnDurationUnknown() {
        media.setDuration(0);
        media.updateFromOther(otherMedia("http://example.com/episode", 0, null, 90000));
        assertEquals(90000, media.getDuration());
    }

    @Test
    public void updateFromOther_takesMimeTypeOnlyWhenPresent() {
        media.updateFromOther(otherMedia("http://example.com/episode", 0, null, 0));
        assertEquals("audio/mpeg", media.getMimeType());
        media.updateFromOther(otherMedia("http://example.com/episode", 0, "audio/ogg", 0));
        assertEquals("audio/ogg", media.getMimeType());
    }

    @Test
    public void compareWithOther_identicalMedia_reportsNoDifference() {
        assertFalse(media.compareWithOther(otherMedia("http://example.com/episode", 1000, "audio/mpeg", 60000)));
    }

    @Test
    public void compareWithOther_differentDownloadUrl_reportsDifference() {
        assertTrue(media.compareWithOther(otherMedia("http://example.com/other", 1000, "audio/mpeg", 60000)));
    }

    @Test
    public void compareWithOther_differentMimeType_reportsDifference() {
        assertTrue(media.compareWithOther(otherMedia("http://example.com/episode", 1000, "audio/ogg", 60000)));
    }

    @Test
    public void compareWithOther_ownMimeTypeMissing_reportsDifference() {
        FeedMedia withoutMime = new FeedMedia(null, "http://example.com/episode", 1000, null);
        assertTrue(withoutMime.compareWithOther(otherMedia("http://example.com/episode", 1000, "audio/ogg", 0)));
    }

    @Test
    public void compareWithOther_otherMimeTypeMissing_isIgnored() {
        assertFalse(media.compareWithOther(otherMedia("http://example.com/episode", 1000, null, 60000)));
    }

    @Test
    public void compareWithOther_differentPositiveSize_reportsDifference() {
        assertTrue(media.compareWithOther(otherMedia("http://example.com/episode", 2000, "audio/mpeg", 60000)));
    }

    @Test
    public void compareWithOther_unknownOtherSize_isIgnored() {
        assertFalse(media.compareWithOther(otherMedia("http://example.com/episode", 0, "audio/mpeg", 60000)));
    }

    @Test
    public void compareWithOther_newDurationWhenOwnIsUnknown_reportsDifference() {
        media.setDuration(0);
        assertTrue(media.compareWithOther(otherMedia("http://example.com/episode", 1000, "audio/mpeg", 60000)));
    }

    @Test
    public void compareWithOther_differentDurationWhenOwnIsKnown_isIgnored() {
        assertFalse(media.compareWithOther(otherMedia("http://example.com/episode", 1000, "audio/mpeg", 90000)));
    }
}
