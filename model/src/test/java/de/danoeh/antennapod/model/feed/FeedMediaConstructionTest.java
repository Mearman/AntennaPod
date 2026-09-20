package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FeedMediaConstructionTest {

    @Test
    public void downloadConstructor_startsUndownloadedAndUnplayed() {
        FeedItem item = new FeedItem();
        item.setId(8);
        FeedMedia media = new FeedMedia(item, "http://example.com/episode", 1234, "audio/mpeg");

        assertSame(item, media.getItem());
        assertEquals(8, media.getItemId());
        assertEquals("http://example.com/episode", media.getDownloadUrl());
        assertEquals("http://example.com/episode", media.getStreamUrl());
        assertEquals(1234, media.getSize());
        assertEquals("audio/mpeg", media.getMimeType());
        assertNull(media.getLocalFileUrl());
        assertEquals(0, media.getDownloadDate());
        assertEquals(0, media.getPosition());
        assertEquals(0, media.getDuration());
        assertFalse(media.isDownloaded());
    }

    @Test
    public void downloadConstructor_withoutItemHasItemIdZero() {
        assertEquals(0, FeedMediaMother.anyFeedMedia().getItemId());
        assertNull(FeedMediaMother.anyFeedMedia().getItem());
    }

    @Test
    public void databaseConstructor_keepsValuesAndCopiesHistoryDate() {
        FeedItem item = new FeedItem();
        item.setId(4);
        Date history = new Date(99000);
        FeedMedia media = new FeedMedia(7, item, 60000, 15000, 4321, "audio/ogg", "/local/file.ogg",
                "http://example.com/file.ogg", 5000, history, 20000, 77000);

        assertEquals(7, media.getId());
        assertEquals(4, media.getItemId());
        assertEquals(60000, media.getDuration());
        assertEquals(15000, media.getPosition());
        assertEquals(4321, media.getSize());
        assertEquals("audio/ogg", media.getMimeType());
        assertEquals("/local/file.ogg", media.getLocalFileUrl());
        assertEquals("http://example.com/file.ogg", media.getDownloadUrl());
        assertEquals(5000, media.getDownloadDate());
        assertEquals(20000, media.getPlayedDuration());
        assertEquals(20000, media.getPlayedDurationWhenStarted());
        assertEquals(77000, media.getLastPlayedTimeStatistics());
        assertEquals(history, media.getLastPlayedTimeHistory());
        history.setTime(1);
        assertEquals(99000, media.getLastPlayedTimeHistory().getTime());
        assertNotSame(media.getLastPlayedTimeHistory(), media.getLastPlayedTimeHistory());
    }

    @Test
    public void databaseConstructor_nullHistoryStaysNull() {
        FeedMedia media = new FeedMedia(7, null, 0, 0, 0, null, null, null, 0, null, 0, 0);
        assertNull(media.getLastPlayedTimeHistory());
        assertEquals(0, media.getItemId());
    }

    @Test
    public void databaseConstructorWithEmbeddedPicture_keepsKnownPictureState() {
        FeedMedia withPicture = new FeedMedia(1, null, 0, 0, 0, null, null, null, 0, null, 0, Boolean.TRUE, 0);
        FeedMedia withoutPicture = new FeedMedia(1, null, 0, 0, 0, null, null, null, 0, null, 0, Boolean.FALSE, 0);
        assertTrue(withPicture.hasEmbeddedPicture());
        assertFalse(withoutPicture.hasEmbeddedPicture());
    }

    @Test
    public void setLastPlayedTimeHistory_copiesDateAndAcceptsNull() {
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        Date date = new Date(5000);
        media.setLastPlayedTimeHistory(date);
        date.setTime(9000);
        assertEquals(5000, media.getLastPlayedTimeHistory().getTime());
        media.setLastPlayedTimeHistory(null);
        assertNull(media.getLastPlayedTimeHistory());
    }
}
