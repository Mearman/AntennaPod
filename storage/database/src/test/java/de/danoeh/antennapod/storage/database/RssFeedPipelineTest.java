package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFunding;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.parser.feed.FeedHandlerResult;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class RssFeedPipelineTest extends FeedPipelineTestBase {

    @Test
    public void channelMetadataIsStoredAndReadBack() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Feed &amp; Title</title>
                <link>https://example.com/</link>
                <description>Channel &lt;b&gt;description&lt;/b&gt;</description>
                <language>EN-gb</language>
                <itunes:author>Some Author</itunes:author>
                <image><url>https://example.com/rss.png</url></image>
                """));

        assertEquals(FEED_URL, stored.getDownloadUrl());
        assertEquals(Feed.TYPE_RSS2, stored.getType());
        assertEquals("Feed & Title", stored.getTitle());
        assertEquals("https://example.com/", stored.getLink());
        assertEquals("Channel description", stored.getDescription());
        assertEquals("en-gb", stored.getLanguage());
        assertEquals("Some Author", stored.getAuthor());
        assertEquals("https://example.com/rss.png", stored.getImageUrl());
    }

    @Test
    public void itunesImageTakesPrecedenceOverRssImage() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Images</title>
                <itunes:image href="https://example.com/itunes.png"/>
                <image><url>https://example.com/rss.png</url></image>
                """));

        assertEquals("https://example.com/itunes.png", stored.getImageUrl());
    }

    @Test
    public void episodeFieldsAndEnclosureSurviveStorage() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Episodes</title>
                <item>
                  <guid>episode-1</guid>
                  <title>First</title>
                  <link>https://example.com/first</link>
                  <pubDate>Mon, 02 Jan 2006 15:04:05 +0000</pubDate>
                  <description>The first episode</description>
                  <enclosure url="https://example.com/first.mp3" length="5000000" type="audio/mpeg"/>
                </item>
                """));

        FeedItem item = storedItem(stored, "episode-1");
        assertEquals("First", item.getTitle());
        assertEquals("https://example.com/first", item.getLink());
        assertEquals(new Date(Instant.parse("2006-01-02T15:04:05Z").toEpochMilli()), item.getPubDate());
        assertTrue(item.hasMedia());
        assertEquals("https://example.com/first.mp3", item.getMedia().getDownloadUrl());
        assertEquals(5000000, item.getMedia().getSize());
        assertEquals("audio/mpeg", item.getMedia().getMimeType());
        DBReader.loadDescriptionOfFeedItem(item);
        assertEquals("The first episode", item.getDescription());
    }

    @Test
    public void enclosureSmallerThanSixteenKilobytesIsStoredWithUnknownSize() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Sizes</title>
                <item>
                  <guid>tiny</guid>
                  <title>Tiny</title>
                  <enclosure url="https://example.com/tiny.mp3" length="1000" type="audio/mpeg"/>
                </item>
                """));

        assertEquals(0, storedItem(stored, "tiny").getMedia().getSize());
    }

    @Test
    public void enclosureWithoutTypeGetsMimeTypeFromFileExtension() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Extensions</title>
                <item>
                  <guid>by-extension</guid>
                  <title>By extension</title>
                  <enclosure url="https://example.com/episode.mp3" length="5000000"/>
                </item>
                """));

        assertEquals("audio/mpeg", storedItem(stored, "by-extension").getMedia().getMimeType());
    }

    @Test
    public void enclosureWithUnknownTypeAndExtensionFallsBackToGenericAudio() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Fallback</title>
                <item>
                  <guid>unknown</guid>
                  <title>Unknown</title>
                  <enclosure url="https://example.com/episode.qqq" length="5000000" type="text/plain"/>
                </item>
                """));

        FeedMedia media = storedItem(stored, "unknown").getMedia();
        assertEquals("audio/*", media.getMimeType());
        assertEquals("https://example.com/episode.qqq", media.getDownloadUrl());
    }

    @Test
    public void imageEnclosureDoesNotBecomeEpisodeMedia() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Images</title>
                <item>
                  <guid>picture</guid>
                  <title>Picture</title>
                  <enclosure url="https://example.com/picture.jpg" length="5000000" type="image/jpeg"/>
                </item>
                """));

        FeedItem item = storedItem(stored, "picture");
        assertFalse(item.hasMedia());
    }

    @Test
    public void onlyFirstEnclosureIsUsedAsMedia() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Two enclosures</title>
                <item>
                  <guid>two</guid>
                  <title>Two</title>
                  <enclosure url="https://example.com/one.mp3" length="5000000" type="audio/mpeg"/>
                  <enclosure url="https://example.com/two.mp3" length="5000000" type="audio/mpeg"/>
                </item>
                """));

        assertEquals("https://example.com/one.mp3", storedItem(stored, "two").getMedia().getDownloadUrl());
    }

    @Test
    public void itemWithoutTitleUsesDescriptionAsTitle() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Untitled</title>
                <item>
                  <guid>no-title</guid>
                  <description>Only a description</description>
                </item>
                """));

        assertEquals("Only a description", storedItem(stored, "no-title").getTitle());
    }

    @Test
    public void emptyGuidIsIgnoredAndItemIsIdentifiedByTitle() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Guids</title>
                <item>
                  <guid></guid>
                  <title>Guidless</title>
                </item>
                """));

        List<FeedItem> items = storedItems(stored);
        assertEquals(1, items.size());
        assertNull(items.get(0).getItemIdentifier());
        assertEquals("Guidless", items.get(0).getIdentifyingValue());
    }

    @Test
    public void itunesDurationIsStoredOnEpisodeMedia() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Durations</title>
                <item>
                  <guid>hms</guid><title>Hours</title>
                  <enclosure url="https://example.com/hms.mp3" length="5000000" type="audio/mpeg"/>
                  <itunes:duration>01:02:03</itunes:duration>
                </item>
                <item>
                  <guid>ms</guid><title>Minutes</title>
                  <enclosure url="https://example.com/ms.mp3" length="5000000" type="audio/mpeg"/>
                  <itunes:duration>15:30</itunes:duration>
                </item>
                <item>
                  <guid>seconds</guid><title>Seconds</title>
                  <enclosure url="https://example.com/seconds.mp3" length="5000000" type="audio/mpeg"/>
                  <itunes:duration>90</itunes:duration>
                </item>
                <item>
                  <guid>broken</guid><title>Broken</title>
                  <enclosure url="https://example.com/broken.mp3" length="5000000" type="audio/mpeg"/>
                  <itunes:duration>soon</itunes:duration>
                </item>
                """));

        assertEquals(3723000, storedItem(stored, "hms").getMedia().getDuration());
        assertEquals(930000, storedItem(stored, "ms").getMedia().getDuration());
        assertEquals(90000, storedItem(stored, "seconds").getMedia().getDuration());
        assertEquals(0, storedItem(stored, "broken").getMedia().getDuration());
    }

    @Test
    public void itunesSubtitleIsUsedWhenEpisodeHasNoDescription() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Subtitles</title>
                <itunes:subtitle>Channel subtitle</itunes:subtitle>
                <item>
                  <guid>subtitle</guid><title>Subtitle</title>
                  <itunes:subtitle>Episode subtitle</itunes:subtitle>
                </item>
                """));

        assertEquals("Channel subtitle", stored.getDescription());
        FeedItem item = storedItem(stored, "subtitle");
        DBReader.loadDescriptionOfFeedItem(item);
        assertEquals("Episode subtitle", item.getDescription());
    }

    @Test
    public void longestOfDescriptionSummaryAndEncodedContentIsStored() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Descriptions</title>
                <item>
                  <guid>longest</guid><title>Longest</title>
                  <description>Short</description>
                  <itunes:summary>A summary that is somewhat longer</itunes:summary>
                  <content:encoded><![CDATA[<p>The encoded content is by far the longest of the three</p>]]></content:encoded>
                </item>
                <item>
                  <guid>summary</guid><title>Summary</title>
                  <description>Short</description>
                  <itunes:summary>A summary that is somewhat longer</itunes:summary>
                </item>
                """));

        FeedItem encoded = storedItem(stored, "longest");
        DBReader.loadDescriptionOfFeedItem(encoded);
        assertEquals("<p>The encoded content is by far the longest of the three</p>", encoded.getDescription());
        FeedItem summary = storedItem(stored, "summary");
        DBReader.loadDescriptionOfFeedItem(summary);
        assertEquals("A summary that is somewhat longer", summary.getDescription());
    }

    @Test
    public void itunesEpisodeImageIsStoredOnEpisode() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Episode images</title>
                <item>
                  <guid>image</guid><title>Image</title>
                  <itunes:image href="https://example.com/episode.png"/>
                </item>
                """));

        assertEquals("https://example.com/episode.png", storedItem(stored, "image").getImageUrl());
    }

    @Test
    public void mediaContentBecomesEpisodeMediaWithSizeAndDuration() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Media RSS</title>
                <item>
                  <guid>audio</guid><title>Audio</title>
                  <media:content url="https://example.com/audio.ogg" medium="audio" fileSize="7000000" duration="120"/>
                </item>
                <item>
                  <guid>video</guid><title>Video</title>
                  <media:content url="https://example.com/video.mp4" medium="video" fileSize="9000000"/>
                </item>
                """));

        FeedMedia audio = storedItem(stored, "audio").getMedia();
        assertEquals("https://example.com/audio.ogg", audio.getDownloadUrl());
        assertEquals("audio/*", audio.getMimeType());
        assertEquals(7000000, audio.getSize());
        assertEquals(120000, audio.getDuration());
        FeedMedia video = storedItem(stored, "video").getMedia();
        assertEquals("video/*", video.getMimeType());
        assertEquals(9000000, video.getSize());
    }

    @Test
    public void mediaThumbnailAndImageContentBecomeImagesInsteadOfMedia() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Media images</title>
                <media:thumbnail url="https://example.com/channel-thumbnail.png"/>
                <item>
                  <guid>thumbnail</guid><title>Thumbnail</title>
                  <media:thumbnail url="https://example.com/thumbnail.png"/>
                </item>
                <item>
                  <guid>image-content</guid><title>Image content</title>
                  <media:content url="https://example.com/cover.png" medium="image"/>
                </item>
                """));

        assertEquals("https://example.com/channel-thumbnail.png", stored.getImageUrl());
        FeedItem thumbnail = storedItem(stored, "thumbnail");
        assertEquals("https://example.com/thumbnail.png", thumbnail.getImageUrl());
        assertFalse(thumbnail.hasMedia());
        FeedItem imageContent = storedItem(stored, "image-content");
        assertEquals("https://example.com/cover.png", imageContent.getImageUrl());
        assertFalse(imageContent.hasMedia());
    }

    @Test
    public void mediaDescriptionIsStoredAsEpisodeDescription() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Media descriptions</title>
                <item>
                  <guid>described</guid><title>Described</title>
                  <media:description>Described through media</media:description>
                </item>
                """));

        FeedItem item = storedItem(stored, "described");
        DBReader.loadDescriptionOfFeedItem(item);
        assertEquals("Described through media", item.getDescription());
    }

    @Test
    public void podcastIndexFundingLinksAreStoredWithTheirLabels() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Funding</title>
                <podcast:funding url="https://example.com/donate">Support the show</podcast:funding>
                <podcast:funding url="https://example.com/patron"/>
                """));

        List<FeedFunding> funding = stored.getPaymentLinks();
        assertEquals(2, funding.size());
        assertEquals(new FeedFunding("https://example.com/donate", "Support the show"), funding.get(0));
        assertEquals("https://example.com/patron", funding.get(1).url);
        assertEquals("", funding.get(1).content);
    }

    @Test
    public void podcastIndexEpisodeLinksAreStored() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Podcast namespace</title>
                <item>
                  <guid>namespaced</guid><title>Namespaced</title>
                  <podcast:chapters url="https://example.com/chapters.json" type="application/json+chapters"/>
                  <podcast:socialInteract uri="https://social.example.com/post/1" protocol="activitypub"/>
                  <podcast:transcript url="https://example.com/transcript.srt" type="application/srt"/>
                  <podcast:transcript url="https://example.com/transcript.json" type="application/json"/>
                </item>
                """));

        FeedItem item = storedItem(stored, "namespaced");
        assertEquals("https://example.com/chapters.json", item.getPodcastIndexChapterUrl());
        assertEquals("https://social.example.com/post/1", item.getSocialInteractUrl());
        assertTrue(item.hasTranscript());
        assertEquals("https://example.com/transcript.json", item.getTranscriptUrl());
        assertEquals("application/json", item.getTranscriptType());
    }

    @Test
    public void podlovePsChaptersAreStoredInOrder() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Chapters</title>
                <item>
                  <guid>chaptered</guid><title>Chaptered</title>
                  <psc:chapters version="1.2">
                    <psc:chapter start="00:00:00" title="Intro"/>
                    <psc:chapter start="00:01:30.500" title="Main" href="https://example.com/main"
                        image="https://example.com/main.png"/>
                    <psc:chapter start="" title="Ignored"/>
                  </psc:chapters>
                </item>
                """));

        FeedItem item = storedItem(stored, "chaptered");
        assertTrue(item.hasChapters());
        List<Chapter> chapters = DBReader.loadChaptersOfFeedItem(item);
        assertNotNull(chapters);
        assertEquals(2, chapters.size());
        assertEquals("Intro", chapters.get(0).getTitle());
        assertEquals(0, chapters.get(0).getStart());
        assertEquals("Main", chapters.get(1).getTitle());
        assertEquals(90500, chapters.get(1).getStart());
        assertEquals("https://example.com/main", chapters.get(1).getLink());
        assertEquals("https://example.com/main.png", chapters.get(1).getImageUrl());
    }

    @Test
    public void dublinCoreDateIsUsedAsPublicationDate() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Dublin Core</title>
                <item>
                  <guid>dated</guid><title>Dated</title>
                  <dc:date>2019-05-04T10:20:30Z</dc:date>
                </item>
                """));

        assertEquals(new Date(Instant.parse("2019-05-04T10:20:30Z").toEpochMilli()),
                storedItem(stored, "dated").getPubDate());
    }

    @Test
    public void publicationDatesInDifferentFormatsAreNormalisedToTheSameTimeline() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Dates</title>
                <item><guid>rfc822</guid><title>rfc822</title>
                  <pubDate>Mon, 02 Jan 2006 15:04:05 +0000</pubDate></item>
                <item><guid>short-year</guid><title>short-year</title>
                  <pubDate>02 Jan 06 15:04 +0100</pubDate></item>
                <item><guid>iso-zulu</guid><title>iso-zulu</title>
                  <pubDate>2006-01-02T15:04:05Z</pubDate></item>
                <item><guid>iso-offset</guid><title>iso-offset</title>
                  <pubDate>2006-01-02T15:04:05+01:00</pubDate></item>
                <item><guid>cest</guid><title>cest</title>
                  <pubDate>Mon, 02 Jan 2006 15:04:05 CEST</pubDate></item>
                <item><guid>sept</guid><title>sept</title>
                  <pubDate>Mon, 02 Sept 2019 10:00:00 GMT</pubDate></item>
                <item><guid>micros</guid><title>micros</title>
                  <pubDate>2006-01-02T15:04:05.123456Z</pubDate></item>
                <item><guid>date-only</guid><title>date-only</title>
                  <pubDate>2006-01-02</pubDate></item>
                """));

        assertEquals(millis("2006-01-02T15:04:05Z"), storedItem(stored, "rfc822").getPubDate().getTime());
        assertEquals(millis("2006-01-02T14:04:00Z"), storedItem(stored, "short-year").getPubDate().getTime());
        assertEquals(millis("2006-01-02T15:04:05Z"), storedItem(stored, "iso-zulu").getPubDate().getTime());
        assertEquals(millis("2006-01-02T14:04:05Z"), storedItem(stored, "iso-offset").getPubDate().getTime());
        assertEquals(millis("2006-01-02T13:04:05Z"), storedItem(stored, "cest").getPubDate().getTime());
        assertEquals(millis("2019-09-02T10:00:00Z"), storedItem(stored, "sept").getPubDate().getTime());
        assertEquals(millis("2006-01-02T15:04:05.123Z"), storedItem(stored, "micros").getPubDate().getTime());
        assertEquals(millis("2006-01-02T00:00:00Z"), storedItem(stored, "date-only").getPubDate().getTime());
    }

    @Test
    public void futureAndUnparseablePublicationDatesAreReplacedByTheStorageTime() throws Exception {
        long before = System.currentTimeMillis();
        Feed stored = parseAndStore(rss("""
                <title>Bad dates</title>
                <item><guid>future</guid><title>future</title>
                  <pubDate>Mon, 02 Jan 2999 15:04:05 +0000</pubDate></item>
                <item><guid>garbage</guid><title>garbage</title>
                  <pubDate>whenever</pubDate></item>
                """));
        long after = System.currentTimeMillis();

        for (String identifier : new String[] {"future", "garbage"}) {
            long stamp = storedItem(stored, identifier).getPubDate().getTime();
            assertTrue(identifier, stamp >= before && stamp <= after);
        }
    }

    @Test
    public void newFeedUrlIsReportedAsRedirectWithoutChangingTheStoredDownloadUrl() throws Exception {
        FeedHandlerResult result = parse(rss("""
                <title>Moved</title>
                <itunes:new-feed-url>  https://example.org/new-feed.xml  </itunes:new-feed-url>
                """));

        assertEquals("https://example.org/new-feed.xml", result.redirectUrl);
        assertEquals(FEED_URL, storeParsed(result.feed).getDownloadUrl());
    }

    @Test
    public void newFeedUrlThatIsNotAnHttpAddressIsNotReportedAsRedirect() throws Exception {
        FeedHandlerResult result = parse(rss("""
                <title>Not moved</title>
                <itunes:new-feed-url>ftp://example.org/feed.xml</itunes:new-feed-url>
                """));

        assertNull(result.redirectUrl);
    }

    @Test
    public void alternateFeedLinksAreCollectedWithTheirTitles() throws Exception {
        FeedHandlerResult result = parse(rss("""
                <title>Alternates</title>
                <atom:link rel="alternate" type="application/rss+xml" title="Other format" href="https://example.com/other.xml"/>
                <atom:link rel="alternate" type="application/atom+xml" href="https://example.com/untitled.xml"/>
                """));

        assertEquals(2, result.alternateFeedUrls.size());
        assertEquals("Other format", result.alternateFeedUrls.get("https://example.com/other.xml"));
        assertEquals("https://example.com/untitled.xml",
                result.alternateFeedUrls.get("https://example.com/untitled.xml"));
    }

    @Test
    public void nextPageLinkMarksTheStoredFeedAsPaged() throws Exception {
        Feed parsed = parse(rss("""
                <title>Paged</title>
                <atom:link rel="next" href="https://example.com/feed.xml?page=2"/>
                """)).feed;

        assertTrue(parsed.isPaged());
        assertEquals("https://example.com/feed.xml?page=2", parsed.getNextPageLink());
        assertEquals("https://example.com/feed.xml?page=2", storeParsed(parsed).getNextPageLink());
    }

    @Test
    public void paymentLinkFromAtomNamespaceIsStoredAsFunding() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Payment</title>
                <atom:link rel="payment" href="https://example.com/pay"/>
                """));

        assertEquals(1, stored.getPaymentLinks().size());
        assertEquals("https://example.com/pay", stored.getPaymentLinks().get(0).url);
    }

    @Test
    public void nonAsciiTextInLegacyEncodingSurvivesTheWholePipeline() throws Exception {
        String document = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<rss version=\"2.0\"><channel><title>Café Français</title>"
                + "<item><guid>latin</guid><title>Crème brûlée</title></item>"
                + "</channel></rss>";

        Feed stored = storeParsed(parse(document, StandardCharsets.ISO_8859_1).feed);

        assertEquals("Café Français", stored.getTitle());
        assertEquals("Crème brûlée", storedItem(stored, "latin").getTitle());
    }

    @Test
    public void rss091FeedIsParsedWithItsItems() throws Exception {
        String document = "<?xml version=\"1.0\"?>\n"
                + "<rss version=\"0.91\"><channel><title>Old feed</title>"
                + "<item><guid>legacy</guid><title>Legacy item</title></item>"
                + "</channel></rss>";

        Feed stored = storeParsed(parse(document).feed);

        assertEquals("Old feed", stored.getTitle());
        assertEquals("Legacy item", storedItem(stored, "legacy").getTitle());
    }

    private static long millis(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }
}
