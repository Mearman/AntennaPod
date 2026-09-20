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

@RunWith(RobolectricTestRunner.class)
public class MediaNamespaceTest {
    private Feed feed;

    @Before
    public void parseFeed() throws Exception {
        feed = FeedParserTestHelper.runFeedParser(
                FeedParserTestHelper.getFeedFile("feed-rss-testMediaNamespace.xml"));
    }

    private FeedItem item(int index) {
        return feed.getItems().get(index);
    }

    @Test
    public void firstFeedThumbnailIsFeedImage() {
        assertEquals("http://example.com/feed-thumb.png", feed.getImageUrl());
    }

    @Test
    public void typedContentProvidesMediaWithSizeAndDuration() {
        assertEquals("http://example.com/a.mp3", item(0).getMedia().getDownloadUrl());
        assertEquals("audio/mpeg", item(0).getMedia().getMimeType());
        assertEquals(2048, item(0).getMedia().getSize());
        assertEquals(90000, item(0).getMedia().getDuration());
    }

    @Test
    public void thumbnailInItemIsItemImage() {
        assertEquals("http://example.com/item-thumb.png", item(0).getImageUrl());
    }

    @Test
    public void mediaDescriptionIsUsedAsItemDescription() {
        assertEquals("Short", item(0).getDescription());
    }

    @Test
    public void longerMediaDescriptionIsKept() {
        assertEquals("A much longer description text", item(1).getDescription());
    }

    @Test
    public void audioMediumUsesGenericAudioTypeAndIgnoresUnparsableNumbers() {
        FeedItem item = item(1);
        assertEquals("http://example.com/b.bin", item.getMedia().getDownloadUrl());
        assertEquals("audio/*", item.getMedia().getMimeType());
        assertEquals(0, item.getMedia().getSize());
        assertEquals(0, item.getMedia().getDuration());
    }

    @Test
    public void defaultContentReplacesEarlierVideoMedia() {
        FeedItem item = item(2);
        assertEquals("http://example.com/default.mp4", item.getMedia().getDownloadUrl());
        assertEquals("video/*", item.getMedia().getMimeType());
    }

    @Test
    public void imageMediumProvidesItemImageInsteadOfMedia() {
        FeedItem item = item(3);
        assertFalse(item.hasMedia());
        assertEquals("http://example.com/img.jpg", item.getImageUrl());
    }

    @Test
    public void imageMediumWithAudioTypeIsStillMedia() {
        FeedItem item = item(4);
        assertEquals("http://example.com/c.mp3", item.getMedia().getDownloadUrl());
        assertEquals("audio/mpeg", item.getMedia().getMimeType());
        assertNull(item.getImageUrl());
    }

    @Test
    public void imageTypeWithoutMediumProvidesItemImage() {
        FeedItem item = item(5);
        assertFalse(item.hasMedia());
        assertEquals("http://example.com/typed.png", item.getImageUrl());
    }

    @Test
    public void unknownTypeFallsBackToGenericAudioForFirstContentOnly() {
        FeedItem item = item(6);
        assertEquals("http://example.com/x", item.getMedia().getDownloadUrl());
        assertEquals("audio/*", item.getMedia().getMimeType());
    }

    @Test
    public void contentWithoutUrlProvidesNoMedia() {
        assertFalse(item(7).hasMedia());
    }
}
