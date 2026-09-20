package de.danoeh.antennapod.parser.feed.element.namespace;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.Map;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.parser.feed.FeedHandlerResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AtomNamespaceTest {
    private FeedHandlerResult result;
    private Feed feed;

    @Before
    public void parseFeed() throws Exception {
        result = FeedParserTestHelper.runFeedHandler(
                FeedParserTestHelper.getFeedFile("feed-atom-testFeedLinksAndEntries.xml"));
        feed = result.feed;
    }

    @Test
    public void feedLanguageIsTakenFromXmlLangAttribute() {
        assertEquals("de", feed.getLanguage());
    }

    @Test
    public void htmlTypedTitleIsUnescaped() {
        assertEquals("Tom & Jerry", feed.getTitle());
    }

    @Test
    public void subtitleBecomesFeedDescription() {
        assertEquals("Feed subtitle", feed.getDescription());
    }

    @Test
    public void iconOverridesLogoAsFeedImage() {
        assertEquals("http://example.com/icon.png", feed.getImageUrl());
    }

    @Test
    public void feedAuthorsAreJoinedAndEntryAuthorsAreIgnored() {
        assertEquals("Alice, Bob", feed.getAuthor());
    }

    @Test
    public void htmlAlternateLinkReplacesUntypedFeedLink() {
        assertEquals("http://example.com/html", feed.getLink());
    }

    @Test
    public void alternateAndArchiveFeedLinksAreCollectedWithTitleOrUrlAsName() {
        Map<String, String> alternates = result.alternateFeedUrls;
        assertEquals(4, alternates.size());
        assertEquals("Atom Alt", alternates.get("http://example.com/alt.atom"));
        assertEquals("http://example.com/alt.rss", alternates.get("http://example.com/alt.rss"));
        assertEquals("Archive", alternates.get("http://example.com/archive.atom"));
        assertEquals("http://example.com/archive.rss", alternates.get("http://example.com/archive.rss"));
        assertFalse(alternates.containsKey("http://example.com/archive.html"));
    }

    @Test
    public void paymentLinkIsAddedToFeed() {
        assertEquals(1, feed.getPaymentLinks().size());
        assertEquals("http://example.com/pay", feed.getPaymentLinks().get(0).url);
    }

    @Test
    public void nextLinkMarksFeedAsPaged() {
        assertTrue(feed.isPaged());
        assertEquals("http://example.com/page2", feed.getNextPageLink());
    }

    @Test
    public void entryLongerContentReplacesSummaryAndIsUnescaped() {
        assertEquals("Longer content & more text", feed.getItems().get(0).getDescription());
    }

    @Test
    public void entryTitleIsUnescapedForHtmlType() {
        assertEquals("Tom & Jerry Returns", feed.getItems().get(0).getTitle());
    }

    @Test
    public void entryIdentifierAndLinkAreRead() {
        FeedItem item = feed.getItems().get(0);
        assertEquals("urn:entry:1", item.getItemIdentifier());
        assertEquals("http://example.com/entry/1", item.getLink());
        assertEquals("http://example.com/entry/pay", item.getPaymentLink());
    }

    @Test
    public void entryUpdatedDateIsUsedWhenNoPublishedDateExists() {
        assertEquals(new Date(60000), feed.getItems().get(0).getPubDate());
    }

    @Test
    public void entryPublishedDateWinsOverLaterUpdatedDate() {
        assertEquals(new Date(5 * 60000), feed.getItems().get(1).getPubDate());
    }

    @Test
    public void futureUpdatedDateIsDiscarded() {
        assertNull(feed.getItems().get(2).getPubDate());
    }

    @Test
    public void enclosureWithUnparsableLengthHasZeroSize() {
        FeedItem item = feed.getItems().get(0);
        assertEquals("http://example.com/one.mp3", item.getMedia().getDownloadUrl());
        assertEquals(0, item.getMedia().getSize());
        assertEquals("audio/mpeg", item.getMedia().getMimeType());
    }

    @Test
    public void itunesDurationIsAppliedToEntryMedia() {
        assertEquals(3723000, feed.getItems().get(0).getMedia().getDuration());
    }

    @Test
    public void imageEnclosureIsNotUsedAsMedia() {
        FeedItem item = feed.getItems().get(1);
        assertFalse(item.getMedia().getDownloadUrl().endsWith("cover.png"));
    }

    @Test
    public void enclosureWithUnknownTypeFallsBackToGenericAudio() {
        FeedItem item = feed.getItems().get(1);
        assertEquals("http://example.com/blob", item.getMedia().getDownloadUrl());
        assertEquals("audio/*", item.getMedia().getMimeType());
        assertEquals(4096, item.getMedia().getSize());
    }

    @Test
    public void alternateEntryLinkWithoutTypeIsItemLink() {
        assertEquals("http://example.com/entry/2", feed.getItems().get(1).getLink());
    }

    @Test
    public void entryWithoutEnclosureHasNoMedia() {
        assertFalse(feed.getItems().get(2).hasMedia());
    }
}
