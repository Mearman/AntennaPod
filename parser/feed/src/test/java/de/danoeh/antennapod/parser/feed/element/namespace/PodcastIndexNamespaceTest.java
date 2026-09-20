package de.danoeh.antennapod.parser.feed.element.namespace;

import org.junit.Before;
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
public class PodcastIndexNamespaceTest {
    private Feed feed;

    @Before
    public void parseFeed() throws Exception {
        feed = FeedParserTestHelper.runFeedParser(
                FeedParserTestHelper.getFeedFile("feed-rss-testPodcastIndexItemTags.xml"));
    }

    @Test
    public void fundingTextIsStoredOnPaymentLink() {
        assertEquals(1, feed.getPaymentLinks().size());
        assertEquals("https://example.com/support", feed.getPaymentLinks().get(0).url);
        assertEquals("Support us", feed.getPaymentLinks().get(0).content);
    }

    @Test
    public void chaptersUrlIsStoredOnItem() {
        assertEquals("https://example.com/chapters.json", feed.getItems().get(0).getPodcastIndexChapterUrl());
    }

    @Test
    public void socialInteractUriIsStoredOnItem() {
        assertEquals("https://social.example.com/post/1", feed.getItems().get(0).getSocialInteractUrl());
    }

    @Test
    public void highestPriorityTranscriptFormatWins() {
        FeedItem item = feed.getItems().get(0);
        assertTrue(item.hasTranscript());
        assertEquals("https://example.com/t.vtt", item.getTranscriptUrl());
        assertEquals("text/vtt", item.getTranscriptType());
    }

    @Test
    public void emptyAttributesAreIgnored() {
        FeedItem item = feed.getItems().get(1);
        assertNull(item.getPodcastIndexChapterUrl());
        assertNull(item.getSocialInteractUrl());
        assertFalse(item.hasTranscript());
    }
}
