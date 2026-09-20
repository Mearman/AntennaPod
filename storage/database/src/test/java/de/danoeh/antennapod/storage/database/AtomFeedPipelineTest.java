package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.parser.feed.FeedHandlerResult;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.Instant;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class AtomFeedPipelineTest extends FeedPipelineTestBase {

    private static String atom(String feedBody) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xml:lang=\"fr\">\n" + feedBody + "\n</feed>\n";
    }

    @Test
    public void feedMetadataIsStoredAndReadBack() throws Exception {
        Feed stored = parseAndStore(atom("""
                <id>urn:uuid:feed-identifier</id>
                <title>Atom Feed</title>
                <subtitle>About this feed</subtitle>
                <link href="https://example.com/site"/>
                <logo>https://example.com/logo.png</logo>
                """));

        assertEquals(Feed.TYPE_ATOM1, stored.getType());
        assertEquals("urn:uuid:feed-identifier", stored.getFeedIdentifier());
        assertEquals("Atom Feed", stored.getTitle());
        assertEquals("About this feed", stored.getDescription());
        assertEquals("https://example.com/site", stored.getLink());
        assertEquals("https://example.com/logo.png", stored.getImageUrl());
        assertEquals("fr", stored.getLanguage());
    }

    @Test
    public void iconOverridesLogoAsFeedImage() throws Exception {
        Feed stored = parseAndStore(atom("""
                <title>Images</title>
                <logo>https://example.com/logo.png</logo>
                <icon>https://example.com/icon.png</icon>
                """));

        assertEquals("https://example.com/icon.png", stored.getImageUrl());
    }

    @Test
    public void authorNamesAreJoinedWithCommas() throws Exception {
        Feed stored = parseAndStore(atom("""
                <title>Authors</title>
                <author><name>Ada</name></author>
                <author><name>Grace</name></author>
                """));

        assertEquals("Ada, Grace", stored.getAuthor());
    }

    @Test
    public void htmlLinkTakesPrecedenceOverUntypedLink() throws Exception {
        Feed stored = parseAndStore(atom("""
                <title>Links</title>
                <link href="https://example.com/untyped"/>
                <link rel="alternate" type="text/html" href="https://example.com/site"/>
                """));

        assertEquals("https://example.com/site", stored.getLink());
    }

    @Test
    public void alternateAndArchiveFeedsAreCollected() throws Exception {
        FeedHandlerResult result = parse(atom("""
                <title>Alternates</title>
                <link rel="alternate" type="application/rss+xml" title="RSS version" href="https://example.com/rss.xml"/>
                <link rel="archives" type="application/atom+xml" href="https://example.com/archive.xml"/>
                <link rel="archives" type="text/html" href="https://example.com/archive.html"/>
                """));

        assertEquals(2, result.alternateFeedUrls.size());
        assertEquals("RSS version", result.alternateFeedUrls.get("https://example.com/rss.xml"));
        assertEquals("https://example.com/archive.xml", result.alternateFeedUrls.get("https://example.com/archive.xml"));
    }

    @Test
    public void nextAndPaymentLinksAreStoredOnTheFeed() throws Exception {
        Feed stored = parseAndStore(atom("""
                <title>Paged</title>
                <link rel="next" href="https://example.com/feed.xml?page=2"/>
                <link rel="payment" href="https://example.com/donate"/>
                """));

        assertEquals("https://example.com/feed.xml?page=2", stored.getNextPageLink());
        assertEquals(1, stored.getPaymentLinks().size());
        assertEquals("https://example.com/donate", stored.getPaymentLinks().get(0).url);
    }

    @Test
    public void entryLinkEnclosureAndTextSurviveStorage() throws Exception {
        Feed stored = parseAndStore(atom("""
                <title>Entries</title>
                <entry>
                  <id>entry-1</id>
                  <title>First entry</title>
                  <link rel="alternate" href="https://example.com/entries/1"/>
                  <link rel="enclosure" type="audio/mpeg" length="4000000" href="https://example.com/entries/1.mp3"/>
                  <link rel="payment" href="https://example.com/entries/1/tip"/>
                  <published>2015-03-04T05:06:07Z</published>
                  <summary type="text">Short summary</summary>
                </entry>
                """));

        FeedItem item = storedItem(stored, "entry-1");
        assertEquals("First entry", item.getTitle());
        assertEquals("https://example.com/entries/1", item.getLink());
        assertEquals("https://example.com/entries/1/tip", item.getPaymentLink());
        assertEquals(new Date(Instant.parse("2015-03-04T05:06:07Z").toEpochMilli()), item.getPubDate());
        assertTrue(item.hasMedia());
        assertEquals("https://example.com/entries/1.mp3", item.getMedia().getDownloadUrl());
        assertEquals(4000000, item.getMedia().getSize());
        assertEquals("audio/mpeg", item.getMedia().getMimeType());
        DBReader.loadDescriptionOfFeedItem(item);
        assertEquals("Short summary", item.getDescription());
    }

    @Test
    public void publishedDateWinsOverUpdatedDateRegardlessOfElementOrder() throws Exception {
        Feed stored = parseAndStore(atom("""
                <title>Dates</title>
                <entry>
                  <id>updated-first</id><title>updated-first</title>
                  <updated>2016-01-01T00:00:00Z</updated>
                  <published>2015-01-01T00:00:00Z</published>
                </entry>
                <entry>
                  <id>published-first</id><title>published-first</title>
                  <published>2015-01-01T00:00:00Z</published>
                  <updated>2016-01-01T00:00:00Z</updated>
                </entry>
                <entry>
                  <id>updated-only</id><title>updated-only</title>
                  <updated>2016-01-01T00:00:00Z</updated>
                </entry>
                """));

        long published = Instant.parse("2015-01-01T00:00:00Z").toEpochMilli();
        assertEquals(published, storedItem(stored, "updated-first").getPubDate().getTime());
        assertEquals(published, storedItem(stored, "published-first").getPubDate().getTime());
        assertEquals(Instant.parse("2016-01-01T00:00:00Z").toEpochMilli(),
                storedItem(stored, "updated-only").getPubDate().getTime());
    }

    @Test
    public void htmlContentIsConvertedToPlainTextAndLongestTextWins() throws Exception {
        Feed stored = parseAndStore(atom("""
                <title>Content</title>
                <entry>
                  <id>html</id><title type="html">Bold &lt;b&gt;title&lt;/b&gt;</title>
                  <summary>Short</summary>
                  <content type="html">&lt;p&gt;Rich &lt;em&gt;content&lt;/em&gt; that is longer than the summary&lt;/p&gt;</content>
                </entry>
                """));

        FeedItem item = storedItem(stored, "html");
        assertEquals("Bold title", item.getTitle());
        DBReader.loadDescriptionOfFeedItem(item);
        assertNotNull(item.getDescription());
        assertTrue(item.getDescription().contains("Rich content that is longer than the summary"));
        assertFalse(item.getDescription().contains("<"));
    }

    @Test
    public void onlyFirstEnclosureLinkBecomesMediaAndImageLinksAreIgnored() throws Exception {
        Feed stored = parseAndStore(atom("""
                <title>Enclosures</title>
                <entry>
                  <id>images</id><title>images</title>
                  <link rel="enclosure" type="image/png" href="https://example.com/cover.png"/>
                  <link rel="enclosure" type="audio/ogg" href="https://example.com/one.ogg"/>
                  <link rel="enclosure" type="audio/ogg" href="https://example.com/two.ogg"/>
                </entry>
                """));

        FeedItem item = storedItem(stored, "images");
        assertEquals("https://example.com/one.ogg", item.getMedia().getDownloadUrl());
    }
}
