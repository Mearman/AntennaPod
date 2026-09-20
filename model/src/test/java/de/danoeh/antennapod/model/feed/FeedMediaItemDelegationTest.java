package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FeedMediaItemDelegationTest {
    private FeedItem item;
    private FeedMedia media;

    @Before
    public void setUp() {
        item = new FeedItem(3, "Episode title", "guid", "http://example.com/episode-page", new Date(7000),
                FeedItem.UNPLAYED, FeedMother.anyFeed());
        media = new FeedMedia(item, "http://example.com/episode.mp3", 1000, "audio/mpeg");
        item.setMedia(media);
    }

    private static FeedMedia orphan() {
        return new FeedMedia(null, "http://example.com/orphan.mp3", 0, "audio/mpeg");
    }

    @Test
    public void humanReadableIdentifier_usesItemTitleElseDownloadUrl() {
        assertEquals("Episode title", media.getHumanReadableIdentifier());
        item.setTitle(null);
        assertEquals("http://example.com/episode.mp3", media.getHumanReadableIdentifier());
        assertEquals("http://example.com/orphan.mp3", orphan().getHumanReadableIdentifier());
    }

    @Test
    public void episodeTitle_usesItemTitleElseIdentifyingValue() {
        assertEquals("Episode title", media.getEpisodeTitle());
        item.setTitle(null);
        assertEquals("guid", media.getEpisodeTitle());
        assertNull(orphan().getEpisodeTitle());
    }

    @Test
    public void feedTitle_comesFromItemFeed() {
        assertEquals("title", media.getFeedTitle());
        item.setFeed(null);
        assertNull(media.getFeedTitle());
        assertNull(orphan().getFeedTitle());
    }

    @Test
    public void websiteLink_comesFromItem() {
        assertEquals("http://example.com/episode-page", media.getWebsiteLink());
        assertNull(orphan().getWebsiteLink());
    }

    @Test
    public void pubDate_comesFromItem() {
        assertEquals(7000, media.getPubDate().getTime());
        item.setPubDate(null);
        assertNull(media.getPubDate());
        assertNull(orphan().getPubDate());
    }

    @Test
    public void description_comesFromItem() {
        item.setDescriptionIfLonger("Shownotes");
        assertEquals("Shownotes", media.getDescription());
        assertNull(orphan().getDescription());
    }

    @Test
    public void chapters_areReadFromAndWrittenToItem() {
        List<Chapter> chapters = Collections.singletonList(new Chapter(0, "Intro", null, null));
        media.setChapters(chapters);
        assertSame(chapters, item.getChapters());
        assertSame(chapters, media.getChapters());

        FeedMedia detached = orphan();
        detached.setChapters(chapters);
        assertNull(detached.getChapters());
    }

    @Test
    public void transcript_isReadFromAndWrittenToItem() {
        assertFalse(media.hasTranscript());
        Transcript transcript = new Transcript();
        media.setTranscript(transcript);
        assertSame(transcript, item.getTranscript());
        assertSame(transcript, media.getTranscript());

        item.setTranscriptUrl("text/vtt", "http://example.com/t.vtt");
        assertTrue(media.hasTranscript());
    }

    @Test
    public void transcript_withoutItemIsAbsentAndIgnoresWrites() {
        FeedMedia detached = orphan();
        detached.setTranscript(new Transcript());
        assertNull(detached.getTranscript());
        assertFalse(detached.hasTranscript());
    }

    @Test
    public void setItem_linksItemBackToMediaAndTracksItemId() {
        FeedItem another = new FeedItem(9, "Other", "guid-2", "link", new Date(), FeedItem.UNPLAYED, null);
        FeedMedia detached = orphan();
        detached.setItem(another);
        assertSame(another, detached.getItem());
        assertSame(detached, another.getMedia());
        assertEquals(9, detached.getItemId());

        detached.setItem(null);
        assertNull(detached.getItem());
        assertEquals(0, detached.getItemId());
    }

    @Test
    public void imageLocation_prefersItemImageLocation() {
        item.setImageUrl("http://example.com/item.png");
        assertEquals("http://example.com/item.png", media.getImageLocation());
    }

    @Test
    public void imageLocation_withoutItemUsesEmbeddedPictureOfLocalFile() {
        FeedMedia detached = orphan();
        detached.setDownloaded(true, 1000);
        detached.setLocalFileUrl("/storage/orphan.mp3");
        detached.setHasEmbeddedPicture(true);
        assertEquals(FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER + "/storage/orphan.mp3", detached.getImageLocation());
    }

    @Test
    public void imageLocation_withoutItemAndWithoutEmbeddedPictureIsNull() {
        FeedMedia detached = orphan();
        detached.setHasEmbeddedPicture(false);
        assertNull(detached.getImageLocation());
    }
}
