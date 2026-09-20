package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeedItemDuplicateGuesserEdgeCasesTest {
    private static final long MINUTES = 1000 * 60;
    private static final Date SAME_DAY = new Date(0);

    @Test
    public void itemsWithoutMediaAreOnlyDuplicatesWhenIdentifiersMatch() {
        FeedItem first = item("id1", "Title", SAME_DAY, null);
        FeedItem second = item("id2", "Title", SAME_DAY, null);
        FeedItem sameId = item("id1", "Other", SAME_DAY, null);

        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(first, second));
        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(first, sameId));
    }

    @Test
    public void onlyOneItemWithMediaIsNeverADuplicateByAttributes() {
        FeedItem withMedia = item("id1", "Title", SAME_DAY, media("url1", 0, "audio/mpeg"));
        FeedItem withoutMedia = item("id2", "Title", SAME_DAY, null);

        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(withMedia, withoutMedia));
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(withoutMedia, withMedia));
    }

    @Test
    public void emptyIdentifiersAreNotTreatedAsMatching() {
        FeedItem first = item("", "Title", SAME_DAY, media("url1", 0, "audio/mpeg"));
        FeedItem second = item("", "Different", SAME_DAY, media("url2", 0, "audio/mpeg"));
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(first, second));
    }

    @Test
    public void emptyStreamUrlsAreNotTreatedAsMatching() {
        FeedItem first = item("id1", "Title", SAME_DAY, media("", 0, "audio/mpeg"));
        FeedItem second = item("id2", "Different", SAME_DAY, media("", 0, "audio/mpeg"));
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(first, second));
    }

    @Test
    public void missingPublicationDateRulesOutAttributeMatch() {
        FeedItem dated = item("id1", "Title", SAME_DAY, media("url1", 0, "audio/mpeg"));
        FeedItem undated = item("id2", "Title", null, media("url2", 0, "audio/mpeg"));

        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(dated, undated));
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(undated, dated));
    }

    @Test
    public void durationsTenMinutesApartAreNoLongerSimilar() {
        FeedItem base = item("id1", "Title", SAME_DAY, media("url1", 30 * MINUTES, "audio/mpeg"));
        FeedItem justInside = item("id2", "Title", SAME_DAY, media("url2", 40 * MINUTES - 1, "audio/mpeg"));
        FeedItem exactlyTenMinutes = item("id3", "Title", SAME_DAY, media("url3", 40 * MINUTES, "audio/mpeg"));

        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(base, justInside));
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(base, exactlyTenMinutes));
    }

    @Test
    public void missingMimeTypeMatchesAnyMimeType() {
        FeedItem typed = item("id1", "Title", SAME_DAY, media("url1", 0, "audio/mpeg"));
        FeedItem untyped = item("id2", "Title", SAME_DAY, media("url2", 0, null));

        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(typed, untyped));
        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(untyped, typed));
    }

    @Test
    public void mimeTypesWithoutSubtypeMustMatchExactly() {
        FeedItem audio = item("id1", "Title", SAME_DAY, media("url1", 0, "audio"));
        FeedItem sameAudio = item("id2", "Title", SAME_DAY, media("url2", 0, "audio"));
        FeedItem video = item("id3", "Title", SAME_DAY, media("url3", 0, "video"));
        FeedItem audioWithSubtype = item("id4", "Title", SAME_DAY, media("url4", 0, "audio/mpeg"));

        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(audio, sameAudio));
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(audio, video));
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(audio, audioWithSubtype));
    }

    @Test
    public void publicationTimeOfDayIsIgnored() {
        FeedItem morning = item("id1", "Title", new Date(60 * MINUTES), media("url1", 0, "audio/mpeg"));
        FeedItem evening = item("id2", "Title", new Date(600 * MINUTES), media("url2", 0, "audio/mpeg"));
        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(morning, evening));
    }

    @Test
    public void sameAndNotEmptyRequiresTwoEqualNonEmptyStrings() {
        assertTrue(FeedItemDuplicateGuesser.sameAndNotEmpty("a", "a"));
        assertFalse(FeedItemDuplicateGuesser.sameAndNotEmpty("a", "b"));
        assertFalse(FeedItemDuplicateGuesser.sameAndNotEmpty("", ""));
        assertFalse(FeedItemDuplicateGuesser.sameAndNotEmpty(null, null));
        assertFalse(FeedItemDuplicateGuesser.sameAndNotEmpty("a", null));
        assertFalse(FeedItemDuplicateGuesser.sameAndNotEmpty(null, "a"));
    }

    @Test
    public void canonicalizedTitleUnifiesQuotesAndDashes() {
        assertEquals("\"Quoted\" - title", FeedItemDuplicateGuesser.canonicalizeTitle("\u201CQuoted\u201D \u2014 title"));
        assertEquals("\"Quoted\"", FeedItemDuplicateGuesser.canonicalizeTitle("\u201EQuoted\u201D"));
    }

    @Test
    public void canonicalizedTitleIsTrimmed() {
        assertEquals("Title", FeedItemDuplicateGuesser.canonicalizeTitle("  Title \n"));
    }

    @Test
    public void canonicalizedNullTitleIsEmpty() {
        assertEquals("", FeedItemDuplicateGuesser.canonicalizeTitle(null));
    }

    @Test
    public void titlesDifferingOnlyInQuoteStyleAreSimilar() {
        FeedItem straight = item("id1", "Say \"hello\"", SAME_DAY, media("url1", 0, "audio/mpeg"));
        FeedItem curly = item("id2", "Say \u201Chello\u201D", SAME_DAY, media("url2", 0, "audio/mpeg"));
        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(straight, curly));
    }

    @Test
    public void itemsWithoutTitlesAreNeverSimilarByTitle() {
        FeedItem first = item("id1", null, SAME_DAY, media("url1", 0, "audio/mpeg"));
        FeedItem second = item("id2", null, SAME_DAY, media("url2", 0, "audio/mpeg"));
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(first, second));
    }

    private FeedMedia media(String url, long duration, String mimeType) {
        FeedMedia media = new FeedMedia(null, url, 0, mimeType);
        media.setDuration((int) duration);
        return media;
    }

    private FeedItem item(String guid, String title, Date pubDate, FeedMedia media) {
        FeedItem item = new FeedItem(0, title, guid, "link", pubDate, FeedItem.PLAYED, null);
        item.setMedia(media);
        return item;
    }
}
