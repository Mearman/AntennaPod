package de.danoeh.antennapod.storage.database.mapper;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedItemCursorTest {

    private CursorRow itemRow() {
        return new CursorRow()
                .with(PodDBAdapter.SELECT_KEY_ITEM_ID, 14L)
                .with(PodDBAdapter.KEY_TITLE, "Episode title")
                .with(PodDBAdapter.KEY_LINK, "https://example.com/episode")
                .with(PodDBAdapter.KEY_PUBDATE, 1_700_000_000_000L)
                .with(PodDBAdapter.KEY_PAYMENT_LINK, "https://example.com/donate")
                .with(PodDBAdapter.KEY_FEED, 3L)
                .with(PodDBAdapter.KEY_HAS_CHAPTERS, 1)
                .with(PodDBAdapter.KEY_READ, FeedItem.PLAYED)
                .with(PodDBAdapter.KEY_ITEM_IDENTIFIER, "guid-14")
                .with(PodDBAdapter.KEY_AUTO_DOWNLOAD_ENABLED, 1)
                .with(PodDBAdapter.KEY_IMAGE_URL, "https://example.com/cover.png")
                .with(PodDBAdapter.KEY_PODCASTINDEX_CHAPTER_URL, "https://example.com/chapters.json")
                .with(PodDBAdapter.KEY_SOCIAL_INTERACT_URL, "https://social.example/post")
                .with(PodDBAdapter.KEY_PODCASTINDEX_TRANSCRIPT_TYPE, "text/vtt")
                .with(PodDBAdapter.KEY_PODCASTINDEX_TRANSCRIPT_URL, "https://example.com/transcript.vtt")
                .with(PodDBAdapter.SELECT_KEY_IS_FAVORITE, 0)
                .with(PodDBAdapter.SELECT_KEY_IS_IN_QUEUE, 0)
                .with(PodDBAdapter.SELECT_KEY_MEDIA_ID, 55L)
                .with(PodDBAdapter.KEY_LAST_PLAYED_TIME_HISTORY, 0L)
                .with(PodDBAdapter.KEY_DURATION, 1_000)
                .with(PodDBAdapter.KEY_POSITION, 200)
                .with(PodDBAdapter.KEY_SIZE, 4_096L)
                .with(PodDBAdapter.KEY_MIME_TYPE, "audio/mpeg")
                .with(PodDBAdapter.KEY_FILE_URL, null)
                .with(PodDBAdapter.KEY_DOWNLOAD_URL, "https://example.com/episode.mp3")
                .with(PodDBAdapter.KEY_DOWNLOAD_DATE, 0L)
                .with(PodDBAdapter.KEY_PLAYED_DURATION, 0)
                .with(PodDBAdapter.KEY_LAST_PLAYED_TIME_STATISTICS, 0L)
                .with(PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE, 0);
    }

    @Test
    public void rowIsConvertedFieldByField() {
        FeedItem item = new FeedItemCursor(itemRow().build()).getFeedItem();

        assertEquals(14L, item.getId());
        assertEquals("Episode title", item.getTitle());
        assertEquals("https://example.com/episode", item.getLink());
        assertEquals(1_700_000_000_000L, item.getPubDate().getTime());
        assertEquals("https://example.com/donate", item.getPaymentLink());
        assertEquals(3L, item.getFeedId());
        assertTrue(item.hasChapters());
        assertTrue(item.isPlayed());
        assertEquals("guid-14", item.getItemIdentifier());
        assertTrue(item.isAutoDownloadEnabled());
        assertEquals("https://example.com/cover.png", item.getImageUrl());
        assertEquals("https://example.com/chapters.json", item.getPodcastIndexChapterUrl());
        assertEquals("https://social.example/post", item.getSocialInteractUrl());
        assertEquals("text/vtt", item.getTranscriptType());
        assertEquals("https://example.com/transcript.vtt", item.getTranscriptUrl());
    }

    @Test
    public void mediaColumnsAreConvertedWhenMediaIdIsPresent() {
        FeedItem item = new FeedItemCursor(itemRow().build()).getFeedItem();

        assertTrue(item.hasMedia());
        assertEquals(55L, item.getMedia().getId());
        assertEquals(1_000, item.getMedia().getDuration());
        assertEquals(200, item.getMedia().getPosition());
        assertEquals(4_096L, item.getMedia().getSize());
        assertEquals("https://example.com/episode.mp3", item.getMedia().getDownloadUrl());
        assertSame(item, item.getMedia().getItem());
    }

    @Test
    public void itemWithoutMediaRowHasNoMedia() {
        FeedItem item = new FeedItemCursor(itemRow().with(PodDBAdapter.SELECT_KEY_MEDIA_ID, null).build())
                .getFeedItem();
        assertFalse(item.hasMedia());
        assertNull(item.getMedia());
    }

    @Test
    public void favoriteAndQueueFlagsBecomeTags() {
        FeedItem item = new FeedItemCursor(itemRow()
                .with(PodDBAdapter.SELECT_KEY_IS_FAVORITE, 1)
                .with(PodDBAdapter.SELECT_KEY_IS_IN_QUEUE, 1)
                .build()).getFeedItem();
        assertTrue(item.isTagged(FeedItem.TAG_FAVORITE));
        assertTrue(item.isTagged(FeedItem.TAG_QUEUE));
    }

    @Test
    public void itemsThatAreNeitherFavoriteNorQueuedCarryNoTags() {
        FeedItem item = new FeedItemCursor(itemRow().build()).getFeedItem();
        assertFalse(item.isTagged(FeedItem.TAG_FAVORITE));
        assertFalse(item.isTagged(FeedItem.TAG_QUEUE));
    }

    @Test
    public void favoriteFlagDoesNotImplyQueueTag() {
        FeedItem item = new FeedItemCursor(itemRow().with(PodDBAdapter.SELECT_KEY_IS_FAVORITE, 1).build())
                .getFeedItem();
        assertTrue(item.isTagged(FeedItem.TAG_FAVORITE));
        assertFalse(item.isTagged(FeedItem.TAG_QUEUE));
    }

    @Test
    public void readColumnIsMappedToPlayState() {
        assertTrue(new FeedItemCursor(itemRow().with(PodDBAdapter.KEY_READ, FeedItem.NEW).build())
                .getFeedItem().isNew());
        FeedItem unplayed = new FeedItemCursor(itemRow().with(PodDBAdapter.KEY_READ, FeedItem.UNPLAYED).build())
                .getFeedItem();
        assertFalse(unplayed.isNew());
        assertFalse(unplayed.isPlayed());
    }

    @Test
    public void zeroBooleanColumnsAreFalse() {
        FeedItem item = new FeedItemCursor(itemRow()
                .with(PodDBAdapter.KEY_HAS_CHAPTERS, 0)
                .with(PodDBAdapter.KEY_AUTO_DOWNLOAD_ENABLED, 0)
                .build()).getFeedItem();
        assertFalse(item.hasChapters());
        assertFalse(item.isAutoDownloadEnabled());
    }

    @Test
    public void missingColumnIsRejectedWhenCursorIsWrapped() {
        CursorRow incomplete = new CursorRow().with(PodDBAdapter.SELECT_KEY_ITEM_ID, 1L);
        assertThrows(IllegalArgumentException.class, () -> new FeedItemCursor(incomplete.build()));
    }
}
