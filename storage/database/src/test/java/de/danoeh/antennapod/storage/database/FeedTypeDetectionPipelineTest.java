package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.parser.feed.FeedHandler;
import de.danoeh.antennapod.parser.feed.UnsupportedFeedtypeException;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class FeedTypeDetectionPipelineTest extends FeedPipelineTestBase {

    @Test
    public void websiteWithTitleIsRejectedWithItsTitleInTheMessage() {
        UnsupportedFeedtypeException exception = assertThrows(UnsupportedFeedtypeException.class, () -> parse(
                "<!DOCTYPE html><html><head><title>Login required</title></head><body>Sign in</body></html>"));

        assertEquals("html", exception.getRootElement());
        assertTrue(exception.getMessage().contains("Login required"));
        assertTrue(DBReader.getFeedList().isEmpty());
    }

    @Test
    public void websiteWithoutTitleIsRejectedAsHtml() {
        UnsupportedFeedtypeException exception = assertThrows(UnsupportedFeedtypeException.class,
                () -> parse("<html><body><p>Nothing to see</p></body></html>"));

        assertEquals("html", exception.getRootElement());
        assertEquals("Server returned html", exception.getMessage());
    }

    @Test
    public void unsupportedRssVersionIsRejected() {
        UnsupportedFeedtypeException exception = assertThrows(UnsupportedFeedtypeException.class,
                () -> parse("<rss version=\"3.0\"><channel><title>Future</title></channel></rss>"));

        assertEquals("Unsupported rss version", exception.getMessage());
        assertTrue(DBReader.getFeedList().isEmpty());
    }

    @Test
    public void rssWithoutVersionIsAssumedToBeRss2() throws Exception {
        Feed stored = parseAndStore("<rss><channel><title>Unversioned</title></channel></rss>");

        assertEquals(Feed.TYPE_RSS2, stored.getType());
        assertEquals("Unversioned", stored.getTitle());
    }

    @Test
    public void rss092IsAcceptedWithoutSettingAFeedType() throws Exception {
        Feed stored = parseAndStore("<rss version=\"0.92\"><channel><title>Legacy</title></channel></rss>");

        assertEquals("Legacy", stored.getTitle());
        assertNull(stored.getType());
    }

    @Test
    public void unknownRootElementIsRejected() {
        assertThrows(UnsupportedFeedtypeException.class,
                () -> parse("<?xml version=\"1.0\"?><opml version=\"2.0\"><body/></opml>"));
        assertTrue(DBReader.getFeedList().isEmpty());
    }

    @Test
    public void plainTextInsteadOfXmlIsRejected() {
        assertThrows(UnsupportedFeedtypeException.class, () -> parse("this is not a feed"));
    }

    @Test
    public void emptyDocumentIsRejected() {
        UnsupportedFeedtypeException exception = assertThrows(UnsupportedFeedtypeException.class, () -> parse(""));

        assertEquals("Unknown problem when trying to determine feed type", exception.getMessage());
    }

    @Test
    public void truncatedFeedFailsToParseAndStoresNothing() {
        assertThrows(SAXException.class, () -> parse("<rss version=\"2.0\"><channel><title>Cut off</title><item>"));

        assertTrue(DBReader.getFeedList().isEmpty());
    }

    @Test
    public void feedWithoutLocalFileIsRejected() {
        Feed feed = new Feed(FEED_URL, null);

        UnsupportedFeedtypeException exception = assertThrows(UnsupportedFeedtypeException.class,
                () -> new FeedHandler().parseFeed(feed));

        assertEquals("Unknown problem when trying to determine feed type", exception.getMessage());
    }

    @Test
    public void exceptionWithoutRootElementOrMessageDescribesUnknownType() {
        assertEquals("Unknown type", new UnsupportedFeedtypeException(null, null).getMessage());
        assertEquals("Server returned div", new UnsupportedFeedtypeException("div", null).getMessage());
        assertEquals("custom", new UnsupportedFeedtypeException("div", "custom").getMessage());
    }
}
