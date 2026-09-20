package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FeedMediaDownloadStateTest {
    private FeedMedia media;

    @Before
    public void setUp() {
        media = new FeedMedia(null, "http://example.com/episode.mp3", 1000, "audio/mpeg");
    }

    @Test
    public void setDownloaded_recordsDownloadDate() {
        media.setDownloaded(true, 5000);
        assertTrue(media.isDownloaded());
        assertEquals(5000, media.getDownloadDate());
    }

    @Test
    public void setDownloaded_falseClearsDownloadDate() {
        media.setDownloaded(true, 5000);
        media.setDownloaded(false, 9000);
        assertFalse(media.isDownloaded());
        assertEquals(0, media.getDownloadDate());
    }

    @Test
    public void isDownloaded_localFeedItemsCountAsDownloaded() {
        Feed localFeed = new Feed("http://example.com/feed", null);
        localFeed.setDownloadUrl(Feed.PREFIX_LOCAL_FOLDER + "content://folder");
        FeedItem item = new FeedItem(1, "t", "g", "l", new Date(), FeedItem.UNPLAYED, localFeed);
        media.setItem(item);
        assertTrue(media.isDownloaded());
    }

    @Test
    public void isDownloaded_remoteFeedItemIsNotDownloadedWithoutDownloadDate() {
        FeedItem item = new FeedItem(1, "t", "g", "l", new Date(), FeedItem.UNPLAYED, FeedMother.anyFeed());
        media.setItem(item);
        assertFalse(media.isDownloaded());
    }

    @Test
    public void localFileAvailable_requiresDownloadAndLocalFileUrl() {
        assertFalse(media.localFileAvailable());
        media.setDownloaded(true, 5000);
        assertFalse(media.localFileAvailable());
        media.setLocalFileUrl("/storage/episode.mp3");
        assertTrue(media.localFileAvailable());
    }

    @Test
    public void setLocalFileUrl_nullResetsDownloadDate() {
        media.setDownloaded(true, 5000);
        media.setLocalFileUrl("/storage/episode.mp3");
        media.setLocalFileUrl(null);
        assertNull(media.getLocalFileUrl());
        assertEquals(0, media.getDownloadDate());
        assertFalse(media.isDownloaded());
    }

    @Test
    public void setLocalFileUrl_keepsDownloadDateForNonNullUrl() {
        media.setDownloaded(true, 5000);
        media.setLocalFileUrl("/storage/other.mp3");
        assertEquals(5000, media.getDownloadDate());
    }

    @Test
    public void fileExists_withoutLocalFileUrlIsFalse() {
        assertFalse(media.fileExists());
    }

    @Test
    public void transcriptFileUrl_appendsTranscriptSuffixToLocalFile() {
        assertNull(media.getTranscriptFileUrl());
        media.setLocalFileUrl("/storage/episode.mp3");
        assertEquals("/storage/episode.mp3.transcript", media.getTranscriptFileUrl());
    }

    @Test
    public void identifier_isDatabaseId() {
        media.setId(31);
        assertEquals(31L, media.getIdentifier());
        assertEquals(31, media.getId());
    }
}
