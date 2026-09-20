package de.danoeh.antennapod.parser.feed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UnsupportedFeedtypeExceptionTest {

    @Test
    public void explicitMessageIsPreferredOverRootElement() {
        UnsupportedFeedtypeException exception = new UnsupportedFeedtypeException("html", "Website title");
        assertEquals("Website title", exception.getMessage());
        assertEquals("html", exception.getRootElement());
    }

    @Test
    public void rootElementIsDescribedWhenThereIsNoMessage() {
        UnsupportedFeedtypeException exception = new UnsupportedFeedtypeException("opml", null);
        assertEquals("Server returned opml", exception.getMessage());
    }

    @Test
    public void messageOnlyConstructorHasNoRootElement() {
        UnsupportedFeedtypeException exception = new UnsupportedFeedtypeException("Unsupported rss version");
        assertEquals("Unsupported rss version", exception.getMessage());
        assertNull(exception.getRootElement());
    }

    @Test
    public void unknownTypeIsReportedWithoutMessageAndRootElement() {
        UnsupportedFeedtypeException exception = new UnsupportedFeedtypeException(null);
        assertEquals("Unknown type", exception.getMessage());
    }
}
