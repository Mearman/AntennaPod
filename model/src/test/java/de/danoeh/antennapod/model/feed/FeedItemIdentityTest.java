package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FeedItemIdentityTest {

    private static FeedItem itemWith(long id, String title, String identifier, String link) {
        return new FeedItem(id, title, identifier, link, new Date(), FeedItem.UNPLAYED, FeedMother.anyFeed());
    }

    @Test
    public void identifyingValue_prefersItemIdentifier() {
        assertEquals("guid", itemWith(1, "title", "guid", "link").getIdentifyingValue());
    }

    @Test
    public void identifyingValue_emptyIdentifierFallsBackToTitle() {
        assertEquals("title", itemWith(1, "title", "", "link").getIdentifyingValue());
        assertEquals("title", itemWith(1, "title", null, "link").getIdentifyingValue());
    }

    @Test
    public void identifyingValue_withoutIdentifierAndTitleUsesMediaDownloadUrl() {
        FeedItem item = itemWith(1, "", null, "link");
        item.setMedia(FeedMediaMother.anyFeedMedia());
        assertEquals("http://example.com/episode", item.getIdentifyingValue());
    }

    @Test
    public void identifyingValue_lastResortIsLink() {
        assertEquals("link", itemWith(1, null, null, "link").getIdentifyingValue());
    }

    @Test
    public void identifyingValue_mediaWithoutDownloadUrlFallsBackToLink() {
        FeedItem item = itemWith(1, null, null, "link");
        item.setMedia(new FeedMedia(null, null, 0, "audio/mp3"));
        assertEquals("link", item.getIdentifyingValue());
    }

    @Test
    public void setId_propagatesToMedia() {
        FeedItem item = itemWith(1, "title", "guid", "link");
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        item.setMedia(media);
        item.setId(42);
        assertEquals(42, item.getId());
        assertEquals(42, media.getItemId());
    }

    @Test
    public void equals_comparesIdOnly() {
        FeedItem first = itemWith(7, "one", "guid-1", "link-1");
        FeedItem second = itemWith(7, "two", "guid-2", "link-2");
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, itemWith(8, "one", "guid-1", "link-1"));
        assertNotEquals(first, null);
        assertNotEquals(first, "item");
    }

    @Test
    public void toString_containsIdTitleFeedIdAndPubDate() {
        FeedItem item = new FeedItem(3, "Episode", "guid", "link", new Date(0), FeedItem.UNPLAYED,
                FeedMother.anyFeed());
        item.setFeedId(9);
        String text = item.toString();
        assertTrue(text.contains("id=3"));
        assertTrue(text.contains("title=Episode"));
        assertTrue(text.contains("feedId=9"));
        assertTrue(text.contains("pubDate=" + new Date(0)));
    }

    @Test
    public void databaseConstructor_keepsValues() {
        Date pubDate = new Date(5000);
        FeedItem item = new FeedItem(4, "Title", "http://example.com/link", pubDate, "http://example.com/pay", 12,
                true, "http://example.com/image", FeedItem.PLAYED, "guid", false, "http://example.com/chapters",
                "text/vtt", "http://example.com/transcript", "http://example.com/social");
        assertEquals(4, item.getId());
        assertEquals("Title", item.getTitle());
        assertEquals("http://example.com/link", item.getLink());
        assertEquals(pubDate, item.getPubDate());
        assertEquals("http://example.com/pay", item.getPaymentLink());
        assertEquals(12, item.getFeedId());
        assertTrue(item.hasChapters());
        assertEquals("http://example.com/image", item.getImageUrl());
        assertEquals(FeedItem.PLAYED, item.getPlayState());
        assertEquals("guid", item.getItemIdentifier());
        assertFalse(item.isAutoDownloadEnabled());
        assertEquals("http://example.com/chapters", item.getPodcastIndexChapterUrl());
        assertEquals("text/vtt", item.getTranscriptType());
        assertEquals("http://example.com/transcript", item.getTranscriptUrl());
        assertEquals("http://example.com/social", item.getSocialInteractUrl());
    }

    @Test
    public void databaseConstructor_withoutTranscriptUrlIgnoresTranscriptType() {
        FeedItem item = new FeedItem(4, "Title", "link", new Date(), null, 12, false, null, FeedItem.NEW, "guid",
                true, null, "text/vtt", null, null);
        assertNull(item.getTranscriptType());
        assertNull(item.getTranscriptUrl());
    }
}
