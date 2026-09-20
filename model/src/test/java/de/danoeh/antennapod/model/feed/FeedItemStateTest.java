package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FeedItemStateTest {
    private FeedItem item;

    @Before
    public void setUp() {
        item = new FeedItem(1, "Title", "guid", "link", new Date(1000), FeedItem.UNPLAYED, FeedMother.anyFeed());
    }

    @Test
    public void newItem_isUnplayedNotNewAndAutoDownloadEnabled() {
        FeedItem fresh = new FeedItem();
        assertEquals(FeedItem.UNPLAYED, fresh.getPlayState());
        assertFalse(fresh.isNew());
        assertFalse(fresh.isPlayed());
        assertTrue(fresh.isAutoDownloadEnabled());
        assertFalse(fresh.hasChapters());
    }

    @Test
    public void setNew_setsNewState() {
        item.setNew();
        assertTrue(item.isNew());
        assertEquals(FeedItem.NEW, item.getPlayState());
    }

    @Test
    public void setPlayedFalse_movesNewItemToUnplayed() {
        item.setNew();
        item.setPlayed(false);
        assertFalse(item.isNew());
        assertEquals(FeedItem.UNPLAYED, item.getPlayState());
    }

    @Test
    public void setPlayState_storesGivenState() {
        item.setPlayState(FeedItem.PLAYED);
        assertTrue(item.isPlayed());
        item.setPlayState(FeedItem.NEW);
        assertTrue(item.isNew());
    }

    @Test
    public void disableAutoDownload_turnsOffAutoDownload() {
        item.disableAutoDownload();
        assertFalse(item.isAutoDownloadEnabled());
    }

    @Test
    public void tags_canBeAddedQueriedAndRemoved() {
        assertFalse(item.isTagged(FeedItem.TAG_FAVORITE));
        item.addTag(FeedItem.TAG_FAVORITE);
        assertTrue(item.isTagged(FeedItem.TAG_FAVORITE));
        assertFalse(item.isTagged(FeedItem.TAG_QUEUE));
        item.removeTag(FeedItem.TAG_FAVORITE);
        assertFalse(item.isTagged(FeedItem.TAG_FAVORITE));
    }

    @Test
    public void pubDate_isDefensivelyCopiedOnReadAndWrite() {
        Date date = new Date(5000);
        item.setPubDate(date);
        date.setTime(9000);
        assertEquals(5000, item.getPubDate().getTime());
        assertNotSame(item.getPubDate(), item.getPubDate());
    }

    @Test
    public void pubDate_nullClearsDate() {
        item.setPubDate(null);
        assertNull(item.getPubDate());
    }

    @Test
    public void constructor_clonesPubDate() {
        Date date = new Date(5000);
        FeedItem created = new FeedItem(1, "t", "g", "l", date, FeedItem.UNPLAYED, null);
        date.setTime(9000);
        assertEquals(5000, created.getPubDate().getTime());
        assertNull(new FeedItem(1, "t", "g", "l", null, FeedItem.UNPLAYED, null).getPubDate());
    }

    @Test
    public void constructorWithChapterFlag_setsChapterFlag() {
        assertTrue(new FeedItem(1, "t", "g", "l", new Date(5000), FeedItem.UNPLAYED, null, true).hasChapters());
        assertFalse(new FeedItem(1, "t", "g", "l", new Date(5000), FeedItem.UNPLAYED, null, false).hasChapters());
    }

    @Test
    public void setDescriptionIfLonger_equalLengthKeepsExistingDescription() {
        item.setDescriptionIfLonger("abcd");
        item.setDescriptionIfLonger("wxyz");
        assertEquals("abcd", item.getDescription());
    }

    @Test
    public void isInProgress_dependsOnMediaPosition() {
        assertFalse(item.isInProgress());
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        item.setMedia(media);
        assertFalse(item.isInProgress());
        media.setPosition(1000);
        assertTrue(item.isInProgress());
    }

    @Test
    public void isDownloaded_dependsOnMediaDownloadState() {
        assertFalse(item.isDownloaded());
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        item.setMedia(media);
        assertFalse(item.isDownloaded());
        media.setDownloaded(true, 1000);
        assertTrue(item.isDownloaded());
    }

    @Test
    public void setMedia_linksMediaBackToItem() {
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        assertFalse(item.hasMedia());
        item.setMedia(media);
        assertTrue(item.hasMedia());
        assertEquals(item, media.getItem());
        item.setMedia(null);
        assertFalse(item.hasMedia());
    }

    @Test
    public void feedAccessors_returnAssignedValues() {
        Feed feed = FeedMother.anyFeed();
        item.setFeed(feed);
        item.setFeedId(33);
        item.setItemIdentifier("other-guid");
        assertEquals(feed, item.getFeed());
        assertEquals(33, item.getFeedId());
        assertEquals("other-guid", item.getItemIdentifier());
    }
}
