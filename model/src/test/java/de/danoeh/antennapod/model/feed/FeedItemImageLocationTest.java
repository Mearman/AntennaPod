package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FeedItemImageLocationTest {
    private FeedItem item;

    @Before
    public void setUp() {
        item = new FeedItem(1, "Title", "guid", "link", new Date(), FeedItem.UNPLAYED, FeedMother.anyFeed());
    }

    @Test
    public void itemImageUrl_takesPrecedence() {
        item.setImageUrl("http://example.com/item.png");
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        media.setHasEmbeddedPicture(true);
        item.setMedia(media);
        assertEquals("http://example.com/item.png", item.getImageLocation());
    }

    @Test
    public void embeddedPicture_usedWhenItemHasNoImage() {
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        media.setDownloaded(true, 1000);
        media.setLocalFileUrl("/storage/episode.mp3");
        media.setHasEmbeddedPicture(true);
        item.setMedia(media);
        assertEquals(FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER + "/storage/episode.mp3", item.getImageLocation());
    }

    @Test
    public void feedImage_usedWhenNoItemImageAndNoEmbeddedPicture() {
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        media.setHasEmbeddedPicture(false);
        item.setMedia(media);
        assertEquals(FeedMother.IMAGE_URL, item.getImageLocation());
    }

    @Test
    public void feedImage_usedWhenItemHasNoMedia() {
        assertEquals(FeedMother.IMAGE_URL, item.getImageLocation());
    }

    @Test
    public void noImageAnywhere_returnsNull() {
        item.setFeed(null);
        assertNull(item.getImageLocation());
    }
}
