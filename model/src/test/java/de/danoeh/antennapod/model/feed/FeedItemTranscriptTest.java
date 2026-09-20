package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FeedItemTranscriptTest {
    private FeedItem item;

    @Before
    public void setUp() {
        item = new FeedItem();
    }

    @Test
    public void withoutTranscript_hasNoTranscriptUrl() {
        assertFalse(item.hasTranscript());
        assertNull(item.getTranscriptUrl());
        assertNull(item.getTranscriptType());
    }

    @Test
    public void setTranscriptUrl_storesUrlAndCanonicalType() {
        item.setTranscriptUrl("application/x-subrip", "http://example.com/t.srt");
        assertTrue(item.hasTranscript());
        assertEquals("http://example.com/t.srt", item.getTranscriptUrl());
        assertEquals("application/srt", item.getTranscriptType());
    }

    @Test
    public void higherPriorityFormat_replacesLowerPriorityOne() {
        item.setTranscriptUrl("application/srt", "http://example.com/t.srt");
        item.setTranscriptUrl("text/vtt", "http://example.com/t.vtt");
        item.setTranscriptUrl("application/json", "http://example.com/t.json");
        assertEquals("http://example.com/t.json", item.getTranscriptUrl());
        assertEquals("application/json", item.getTranscriptType());
    }

    @Test
    public void lowerOrEqualPriorityFormat_isIgnored() {
        item.setTranscriptUrl("text/vtt", "http://example.com/t.vtt");
        item.setTranscriptUrl("application/srt", "http://example.com/t.srt");
        item.setTranscriptUrl("text/vtt", "http://example.com/other.vtt");
        assertEquals("http://example.com/t.vtt", item.getTranscriptUrl());
        assertEquals("text/vtt", item.getTranscriptType());
    }

    @Test
    public void unknownMimeType_isIgnored() {
        item.setTranscriptUrl("text/html", "http://example.com/t.html");
        assertFalse(item.hasTranscript());
    }

    @Test
    public void emptyTypeOrUrl_isIgnored() {
        item.setTranscriptUrl("", "http://example.com/t.vtt");
        item.setTranscriptUrl("text/vtt", "");
        item.setTranscriptUrl(null, "http://example.com/t.vtt");
        item.setTranscriptUrl("text/vtt", null);
        assertFalse(item.hasTranscript());
    }
}
