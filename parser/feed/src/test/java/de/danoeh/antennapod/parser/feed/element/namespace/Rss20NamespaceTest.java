package de.danoeh.antennapod.parser.feed.element.namespace;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class Rss20NamespaceTest {

    private Feed parse(String fileName) throws Exception {
        return FeedParserTestHelper.runFeedParser(FeedParserTestHelper.getFeedFile(fileName));
    }

    @Test
    public void feedTitleAndDescriptionAreUnescapedFromHtml() throws Exception {
        Feed feed = parse("feed-rss-testHtmlFeedDescription.xml");
        assertEquals("Tom & Jerry", feed.getTitle());
        assertEquals("Bold text & more", feed.getDescription());
    }

    @Test
    public void languageIsLowerCased() throws Exception {
        Feed feed = parse("feed-rss-testHtmlFeedDescription.xml");
        assertEquals("en-us", feed.getLanguage());
    }

    @Test
    public void itunesImagePreferredOverRssImageAndEmptyItunesImageIsIgnored() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        assertEquals("http://example.com/itunes-image.png", feed.getImageUrl());
    }

    @Test
    public void emptyGuidLeavesItemIdentifierUnset() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        assertNull(feed.getItems().get(0).getItemIdentifier());
        assertEquals("guid-two", feed.getItems().get(1).getItemIdentifier());
    }

    @Test
    public void unparsableEnclosureLengthResultsInZeroSize() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        FeedItem item = feed.getItems().get(0);
        assertEquals("http://example.com/a.mp3", item.getMedia().getDownloadUrl());
        assertEquals(0, item.getMedia().getSize());
    }

    @Test
    public void itunesDurationIsAppliedToMediaAtItemEnd() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        assertEquals(90000, feed.getItems().get(0).getMedia().getDuration());
    }

    @Test
    public void unparsableItunesDurationLeavesDurationUnset() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        assertEquals(0, feed.getItems().get(1).getMedia().getDuration());
    }

    @Test
    public void itemWithoutTitleUsesDescriptionAsTitle() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        FeedItem item = feed.getItems().get(1);
        assertEquals("Only a description", item.getTitle());
        assertEquals("Only a description", item.getDescription());
    }

    @Test
    public void secondEnclosureDoesNotReplaceFirstMedia() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        FeedItem item = feed.getItems().get(1);
        assertEquals("http://example.com/b.mp3", item.getMedia().getDownloadUrl());
        assertEquals(20000, item.getMedia().getSize());
    }

    @Test
    public void imageEnclosureIsNotMediaAndDurationIsDiscarded() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        assertFalse(feed.getItems().get(2).hasMedia());
    }

    @Test
    public void enclosureWithoutUrlIsIgnored() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        assertFalse(feed.getItems().get(3).hasMedia());
    }

    @Test
    public void suspiciouslySmallEnclosureLengthIsTreatedAsUnknown() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        FeedItem item = feed.getItems().get(4);
        assertTrue(item.hasMedia());
        assertEquals(0, item.getMedia().getSize());
    }

    @Test
    public void futurePubDateIsDiscardedButLinkIsKept() throws Exception {
        Feed feed = parse("feed-rss-testItemFallbacksAndEnclosures.xml");
        FeedItem item = feed.getItems().get(5);
        assertNull(item.getPubDate());
        assertEquals("http://example.com/future", item.getLink());
    }
}
