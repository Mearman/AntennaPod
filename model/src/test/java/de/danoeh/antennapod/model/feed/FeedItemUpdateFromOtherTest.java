package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FeedItemUpdateFromOtherTest {
    private FeedItem original;
    private FeedItem other;

    @Before
    public void setUp() {
        original = new FeedItem(1, "Original title", "guid", "http://example.com/original", new Date(1000),
                FeedItem.UNPLAYED, FeedMother.anyFeed());
        other = new FeedItem(1, null, "guid", null, null, FeedItem.UNPLAYED, FeedMother.anyFeed());
    }

    @Test
    public void title_replacedWhenPresentAndKeptWhenNull() {
        original.updateFromOther(other);
        assertEquals("Original title", original.getTitle());

        other.setTitle("New title");
        original.updateFromOther(other);
        assertEquals("New title", original.getTitle());
    }

    @Test
    public void link_replacedWhenPresentAndKeptWhenNull() {
        original.updateFromOther(other);
        assertEquals("http://example.com/original", original.getLink());

        other.setLink("http://example.com/new");
        original.updateFromOther(other);
        assertEquals("http://example.com/new", original.getLink());
    }

    @Test
    public void description_replacedWhenPresentAndKeptWhenNull() {
        original.setDescriptionIfLonger("Original description");
        original.updateFromOther(other);
        assertEquals("Original description", original.getDescription());

        other.setDescriptionIfLonger("Short");
        original.updateFromOther(other);
        assertEquals("Short", original.getDescription());
    }

    @Test
    public void pubDate_replacedWhenDifferentAndKeptWhenNull() {
        original.updateFromOther(other);
        assertEquals(1000, original.getPubDate().getTime());

        other.setPubDate(new Date(2000));
        original.updateFromOther(other);
        assertEquals(2000, original.getPubDate().getTime());
    }

    @Test
    public void paymentLink_replacedWhenPresentAndKeptWhenNull() {
        original.setPaymentLink("http://example.com/pay");
        original.updateFromOther(other);
        assertEquals("http://example.com/pay", original.getPaymentLink());

        other.setPaymentLink("http://example.com/donate");
        original.updateFromOther(other);
        assertEquals("http://example.com/donate", original.getPaymentLink());
    }

    @Test
    public void podcastIndexUrls_replacedWhenPresentAndKeptWhenNull() {
        original.setPodcastIndexChapterUrl("http://example.com/chapters");
        original.setSocialInteractUrl("http://example.com/social");
        original.updateFromOther(other);
        assertEquals("http://example.com/chapters", original.getPodcastIndexChapterUrl());
        assertEquals("http://example.com/social", original.getSocialInteractUrl());

        other.setPodcastIndexChapterUrl("http://example.com/new-chapters");
        other.setSocialInteractUrl("http://example.com/new-social");
        original.updateFromOther(other);
        assertEquals("http://example.com/new-chapters", original.getPodcastIndexChapterUrl());
        assertEquals("http://example.com/new-social", original.getSocialInteractUrl());
    }

    @Test
    public void transcript_takenFromOtherWhenPresentAndKeptWhenAbsent() {
        original.setTranscriptUrl("text/vtt", "http://example.com/transcript.vtt");
        original.updateFromOther(other);
        assertEquals("http://example.com/transcript.vtt", original.getTranscriptUrl());
        assertEquals("text/vtt", original.getTranscriptType());

        other.setTranscriptUrl("application/json", "http://example.com/transcript.json");
        original.updateFromOther(other);
        assertEquals("http://example.com/transcript.json", original.getTranscriptUrl());
        assertEquals("application/json", original.getTranscriptType());
    }

    @Test
    public void chapters_takenFromOtherWhenDatabaseHasNone() {
        List<Chapter> chapters = Collections.singletonList(new Chapter(0, "One", null, null));
        other.setChapters(chapters);
        original.updateFromOther(other);
        assertSame(chapters, original.getChapters());
    }

    @Test
    public void chapters_keptWhenDatabaseAlreadyHasChapters() {
        FeedItem withChaptersInDatabase = new FeedItem(1, "t", "guid", "link", new Date(), FeedItem.UNPLAYED,
                FeedMother.anyFeed(), true);
        other.setChapters(Collections.singletonList(new Chapter(0, "One", null, null)));
        withChaptersInDatabase.updateFromOther(other);
        assertNull(withChaptersInDatabase.getChapters());
    }

    @Test
    public void media_newMediaOnOtherIsAttachedAndItemBecomesNew() {
        original.setPlayed(true);
        FeedMedia newMedia = FeedMediaMother.anyFeedMedia();
        other.setMedia(newMedia);

        original.updateFromOther(other);

        assertSame(newMedia, original.getMedia());
        assertSame(original, newMedia.getItem());
        assertTrue(original.isNew());
    }

    @Test
    public void media_changedMediaUpdatesExistingMediaInPlace() {
        FeedMedia existing = new FeedMedia(original, "http://example.com/episode", 100, "audio/mp3");
        original.setMedia(existing);
        FeedMedia changed = new FeedMedia(other, "http://example.com/moved", 500, "audio/mp3");
        other.setMedia(changed);

        original.updateFromOther(other);

        assertSame(existing, original.getMedia());
        assertEquals("http://example.com/moved", existing.getDownloadUrl());
        assertEquals(500, existing.getSize());
    }

    @Test
    public void media_unchangedMediaLeavesExistingMediaUntouched() {
        FeedMedia existing = new FeedMedia(original, "http://example.com/episode", 100, "audio/mp3");
        original.setMedia(existing);
        FeedMedia same = new FeedMedia(other, "http://example.com/episode", 100, "audio/mp3");
        other.setMedia(same);

        original.updateFromOther(other);

        assertSame(existing, original.getMedia());
        assertEquals(100, existing.getSize());
        assertEquals("audio/mp3", existing.getMimeType());
    }

    @Test
    public void media_absentOnOtherKeepsExistingMedia() {
        FeedMedia existing = FeedMediaMother.anyFeedMedia();
        original.setMedia(existing);
        original.updateFromOther(other);
        assertSame(existing, original.getMedia());
    }
}
