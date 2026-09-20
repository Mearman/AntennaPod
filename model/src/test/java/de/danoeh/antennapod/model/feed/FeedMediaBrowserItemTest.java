package de.danoeh.antennapod.model.feed;

import android.support.v4.media.MediaBrowserCompat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedMediaBrowserItemTest {
    private FeedItem item;
    private FeedMedia media;

    @Before
    public void setUp() {
        item = new FeedItem(3, "Episode title", "guid", "link", new Date(), FeedItem.UNPLAYED, FeedMother.anyFeed());
        media = new FeedMedia(12, item, 0, 0, 0, "audio/mpeg", null, "http://example.com/episode.mp3", 0, null, 0, 0);
        item.setMedia(media);
    }

    @Test
    public void mediaItem_isPlayableAndDescribesEpisode() {
        MediaBrowserCompat.MediaItem mediaItem = media.getMediaItem();
        assertTrue(mediaItem.isPlayable());
        assertEquals("12", mediaItem.getMediaId());
        assertEquals("Episode title", mediaItem.getDescription().getTitle().toString());
        assertEquals("title", mediaItem.getDescription().getSubtitle().toString());
        assertEquals("title", mediaItem.getDescription().getDescription().toString());
    }

    @Test
    public void mediaItem_prefersItemImageAsIcon() {
        item.setImageUrl("http://example.com/item.png");
        assertEquals("http://example.com/item.png", media.getMediaItem().getDescription().getIconUri().toString());
    }

    @Test
    public void mediaItem_fallsBackToFeedImageAsIcon() {
        assertEquals(FeedMother.IMAGE_URL, media.getMediaItem().getDescription().getIconUri().toString());
    }

    @Test
    public void mediaItem_withoutAnyImageHasNoIcon() {
        item.getFeed().setImageUrl(null);
        assertNull(media.getMediaItem().getDescription().getIconUri());
    }

    @Test
    public void mediaItem_embeddedPicturesAreNotExposedAsIcon() {
        media.setDownloaded(true, 1000);
        media.setLocalFileUrl("/storage/episode.mp3");
        media.setHasEmbeddedPicture(true);
        item.getFeed().setImageUrl(null);
        assertNull(media.getMediaItem().getDescription().getIconUri());
    }
}
