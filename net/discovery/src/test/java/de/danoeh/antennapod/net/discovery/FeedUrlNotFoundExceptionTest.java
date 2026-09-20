package de.danoeh.antennapod.net.discovery;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FeedUrlNotFoundExceptionTest {

    @Test
    public void testExposesArtistAndTrackName() {
        FeedUrlNotFoundException exception = new FeedUrlNotFoundException("Some Artist", "Some Track");
        assertEquals("Some Artist", exception.getArtistName());
        assertEquals("Some Track", exception.getTrackName());
    }

    @Test
    public void testMessageDescribesMissingFeedUrl() {
        assertEquals("Result does not specify a feed url", new FeedUrlNotFoundException("a", "b").getMessage());
    }

    @Test
    public void testIsIoExceptionSoNetworkErrorHandlingCoversIt() {
        assertTrue(new FeedUrlNotFoundException("a", "b") instanceof IOException);
    }
}
