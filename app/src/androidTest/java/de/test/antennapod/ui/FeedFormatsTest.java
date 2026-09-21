package de.test.antennapod.ui;

import android.content.Intent;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFunding;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.TestAssets;
import de.test.antennapod.util.service.download.StaticContentServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FeedFormatsTest {
    private StaticContentServer server;
    private long startedAt;

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        startedAt = System.currentTimeMillis();
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        UserPreferences.setAllowMobileFeedRefresh(true);
        EspressoTestUtils.setLaunchScreen(AddFeedFragment.TAG);
        server = new StaticContentServer();
        server.start();
        activityRule.launchActivity(new Intent());
    }

    @After
    public void tearDown() {
        server.stop();
        PodDBAdapter.deleteDatabase();
    }

    private String publishAsset(String name, String contentType) throws Exception {
        return server.publish("/feeds/" + name, contentType, TestAssets.readText("feeds/" + name));
    }

    @Test
    public void rssFeedWithAllNamespacesShowsPreviewAndIsStored() throws Exception {
        String url = publishAsset("full.xml", "application/rss+xml");

        FeedRobot.addFeedByUrl(url);
        FeedRobot.assertPreviewHeader("Everything Podcast", "Jane Host");
        onView(withId(R.id.headerDescriptionLabel)).check(matches(withText("Plain channel description")));
        FeedRobot.subscribeToPreviewedFeed();

        Feed feed = FeedRobot.awaitFeed(url);
        assertEquals(Feed.STATE_SUBSCRIBED, feed.getState());
        assertEquals("Everything Podcast", feed.getTitle());
        assertEquals("https://example.com/everything", feed.getLink());
        assertEquals("Plain channel description", feed.getDescription());
        assertEquals("en-gb", feed.getLanguage());
        assertEquals("Jane Host", feed.getAuthor());
        assertEquals(server.getBaseUrl() + "/covers/itunes.jpg", feed.getImageUrl());
        assertEquals(Feed.TYPE_RSS2, feed.getType());
        assertEquals(5, feed.getItems().size());

        List<FeedFunding> funding = feed.getPaymentLinks();
        assertEquals(2, funding.size());
        assertEquals("https://example.com/donate", funding.get(0).url);
        assertEquals("", funding.get(0).content);
        assertEquals("https://example.com/fund", funding.get(1).url);
        assertEquals("Support the show", funding.get(1).content);
    }

    @Test
    public void richEpisodeKeepsItsMetadata() throws Exception {
        String url = publishAsset("full.xml", "application/rss+xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        FeedItem item = FeedRobot.itemByGuid(feed, "full-episode-1");
        assertEquals("Rich & Full Episode", item.getTitle());
        assertEquals("https://example.com/everything/1", item.getLink());
        assertEquals(FeedRobot.utc(2023, 1, 2, 10, 0, 0), item.getPubDate().getTime());
        assertTrue(item.getDescription().contains("Long <b>encoded</b> description"));
        assertEquals(server.getBaseUrl() + "/covers/ep1.jpg", item.getImageUrl());
        assertEquals("https://social.example.com/@host/1", item.getSocialInteractUrl());
        assertEquals(server.getBaseUrl() + "/chapters/ep1.json", item.getPodcastIndexChapterUrl());

        FeedMedia media = item.getMedia();
        assertEquals(server.getBaseUrl() + "/media/ep1.mp3", media.getDownloadUrl());
        assertEquals("audio/mpeg", media.getMimeType());
        assertEquals(5000000, media.getSize());
        assertEquals((1 * 60 * 60 + 2 * 60 + 3) * 1000, media.getDuration());
        assertEquals(MediaType.AUDIO, media.getMediaType());
    }

    @Test
    public void episodePicksTheMostCapableTranscriptFormat() throws Exception {
        String url = publishAsset("full.xml", "application/rss+xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        FeedItem item = FeedRobot.itemByGuid(feed, "full-episode-1");
        assertTrue(item.hasTranscript());
        assertEquals(server.getBaseUrl() + "/transcripts/ep1.json", item.getTranscriptUrl());
        assertEquals("application/json", item.getTranscriptType());
        assertFalse(FeedRobot.itemByGuid(feed, "video-episode").hasTranscript());
    }

    @Test
    public void simpleChaptersFromTheFeedAreStored() throws Exception {
        String url = publishAsset("full.xml", "application/rss+xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        FeedItem item = FeedRobot.itemByGuid(feed, "full-episode-1");
        assertTrue(item.hasChapters());
        List<Chapter> chapters = DBReader.loadChaptersOfFeedItem(item);
        assertEquals(3, chapters.size());
        assertEquals("Introduction", chapters.get(0).getTitle());
        assertEquals(0, chapters.get(0).getStart());
        assertEquals("Main topic", chapters.get(1).getTitle());
        assertEquals(90500, chapters.get(1).getStart());
        assertEquals("https://example.com/main", chapters.get(1).getLink());
        assertEquals(server.getBaseUrl() + "/covers/chapter.jpg", chapters.get(1).getImageUrl());
        assertEquals("Wrap up", chapters.get(2).getTitle());
        assertEquals(3600000, chapters.get(2).getStart());
    }

    @Test
    public void mediaNamespaceProvidesEnclosureImageAndDescription() throws Exception {
        String url = publishAsset("full.xml", "application/rss+xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        FeedItem video = FeedRobot.itemByGuid(feed, "video-episode");
        assertEquals(server.getBaseUrl() + "/media/video.mp4", video.getMedia().getDownloadUrl());
        assertEquals(12345678, video.getMedia().getSize());
        assertEquals(90000, video.getMedia().getDuration());
        assertEquals(MediaType.VIDEO, video.getMedia().getMediaType());
        assertEquals(server.getBaseUrl() + "/covers/thumb.jpg", video.getImageUrl());
        assertEquals("Description from media namespace that is the longest", video.getDescription());
        assertEquals(FeedRobot.utc(2023, 1, 1, 10, 0, 0), video.getPubDate().getTime());

        FeedItem audio = FeedRobot.itemByGuid(feed, "audio-through-media-content");
        assertEquals("Only a description and no title", audio.getTitle());
        assertEquals(server.getBaseUrl() + "/media/first.ogg", audio.getMedia().getDownloadUrl());
        assertEquals(MediaType.AUDIO, audio.getMedia().getMediaType());
        assertEquals(server.getBaseUrl() + "/media/cover.png", audio.getImageUrl());
    }

    @Test
    public void enclosuresOfUnusualTypesAndLengthsAreAccepted() throws Exception {
        String url = publishAsset("full.xml", "application/rss+xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        FeedItem untyped = FeedRobot.itemByTitle(feed, "Markup in the title");
        assertEquals("An episode without a guid", untyped.getDescription());
        assertEquals(server.getBaseUrl() + "/media/unknown.bin", untyped.getMedia().getDownloadUrl());
        assertTrue(untyped.getMedia().checkedOnSizeButUnknown());

        FeedItem future = FeedRobot.itemByGuid(feed, "future-episode");
        assertImportedAfterTestStart(future);
        assertEquals(20000, future.getMedia().getSize());
    }

    @Test
    public void atomFeedIsParsedIntoFeedAndEntries() throws Exception {
        String url = publishAsset("atom.xml", "application/atom+xml");

        FeedRobot.addFeedByUrl(url);
        FeedRobot.assertPreviewHeader("Atom Podcast", "First Author, Second Author");
        onView(withId(R.id.headerDescriptionLabel)).check(matches(withText("Subtitle of the Atom feed")));
        FeedRobot.subscribeToPreviewedFeed();

        Feed feed = FeedRobot.awaitFeed(url);
        assertEquals(Feed.TYPE_ATOM1, feed.getType());
        assertEquals("urn:uuid:atom-feed", feed.getFeedIdentifier());
        assertEquals("fr", feed.getLanguage());
        assertEquals("https://example.com/atom", feed.getLink());
        assertEquals(server.getBaseUrl() + "/covers/icon.png", feed.getImageUrl());
        assertEquals(1, feed.getPaymentLinks().size());
        assertEquals("https://example.com/atom-donate", feed.getPaymentLinks().get(0).url);
        assertFalse(feed.isPaged());
        assertEquals(3, feed.getItems().size());
    }

    @Test
    public void atomEntriesKeepEnclosuresDatesAndDescriptions() throws Exception {
        String url = publishAsset("atom.xml", "application/atom+xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        FeedItem first = FeedRobot.itemByGuid(feed, "urn:uuid:atom-entry-1");
        assertEquals("First Atom entry", first.getTitle());
        assertEquals("https://example.com/atom/1", first.getLink());
        assertEquals("https://example.com/atom/1/pay", first.getPaymentLink());
        assertEquals(FeedRobot.utc(2023, 3, 1, 10, 0, 0), first.getPubDate().getTime());
        assertTrue(first.getDescription().startsWith("Content with markup that is longer than the summary"));
        assertEquals(server.getBaseUrl() + "/media/atom1.mp3", first.getMedia().getDownloadUrl());
        assertEquals("audio/mpeg", first.getMedia().getMimeType());
        assertEquals(123456, first.getMedia().getSize());
        assertEquals(120000, first.getMedia().getDuration());

        FeedItem second = FeedRobot.itemByGuid(feed, "urn:uuid:atom-entry-2");
        assertEquals("https://example.com/atom/2", second.getLink());
        assertEquals(FeedRobot.utc(2023, 2, 1, 10, 0, 0), second.getPubDate().getTime());
        assertEquals("Plain content of the second entry", second.getDescription());
        assertEquals(server.getBaseUrl() + "/media/atom2.bin", second.getMedia().getDownloadUrl());

        FeedItem third = FeedRobot.itemByGuid(feed, "urn:uuid:atom-entry-3");
        assertFalse(third.hasMedia());
    }

    @Test
    public void everyDateNotationIsUnderstood() throws Exception {
        String url = publishAsset("dates.xml", "application/rss+xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        long january2 = FeedRobot.utc(2023, 1, 2, 10, 0, 0);
        assertEquals(january2, pubDate(feed, "rfc822"));
        assertEquals(january2, pubDate(feed, "short-year"));
        assertEquals(FeedRobot.utc(2023, 1, 2, 11, 0, 0), pubDate(feed, "cet"));
        assertEquals(FeedRobot.utc(2023, 7, 3, 10, 0, 0), pubDate(feed, "cest"));
        assertEquals(january2 + 123, pubDate(feed, "iso-micro"));
        assertEquals(january2 + 500, pubDate(feed, "iso-short-fraction"));
        assertEquals(FeedRobot.utc(2023, 1, 2, 0, 0, 0), pubDate(feed, "iso-date-only"));
        assertEquals(FeedRobot.utc(2023, 1, 2, 8, 0, 0), pubDate(feed, "no-seconds"));
        assertEquals(FeedRobot.utc(2023, 9, 5, 10, 0, 0), pubDate(feed, "sept"));
        assertEquals(FeedRobot.utc(2023, 1, 2, 8, 0, 0), pubDate(feed, "iso-offset"));
        assertEquals(FeedRobot.utc(2023, 1, 2, 0, 0, 0), pubDate(feed, "slashes"));
        assertEquals(january2, pubDate(feed, "wrong-weekday"));
        assertEquals(january2, pubDate(feed, "dublin-core"));
        assertImportedAfterTestStart(FeedRobot.itemByGuid(feed, "garbage"));
    }

    private void assertImportedAfterTestStart(FeedItem item) {
        assertNotNull(item.getPubDate());
        assertTrue(item.getPubDate().getTime() >= startedAt);
        assertTrue(item.getPubDate().getTime() <= System.currentTimeMillis());
    }

    private long pubDate(Feed feed, String guid) {
        FeedItem item = FeedRobot.itemByGuid(feed, guid);
        assertNotNull("No publication date for " + guid, item.getPubDate());
        return item.getPubDate().getTime();
    }

    @Test
    public void durationsInEveryNotationAreConverted() throws Exception {
        String url = publishAsset("durations.xml", "application/rss+xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        assertEquals("Subtitle used as description", feed.getDescription());
        assertEquals("Nested author", feed.getAuthor());
        assertEquals(90000, FeedRobot.itemByGuid(feed, "seconds").getMedia().getDuration());
        assertEquals((12 * 60 + 34) * 1000, FeedRobot.itemByGuid(feed, "minutes").getMedia().getDuration());
        assertEquals((60 * 60 + 2 * 60 + 3) * 1000, FeedRobot.itemByGuid(feed, "hours").getMedia().getDuration());
        assertEquals(45500, FeedRobot.itemByGuid(feed, "fraction").getMedia().getDuration());
        assertEquals(0, FeedRobot.itemByGuid(feed, "invalid").getMedia().getDuration());
        assertEquals(0, FeedRobot.itemByGuid(feed, "too-many-parts").getMedia().getDuration());
        assertFalse(FeedRobot.itemByGuid(feed, "no-media").hasMedia());
        FeedRobot.awaitCondition(() -> FeedRobot.itemByGuid(FeedRobot.reload(feed), "small-length")
                .getMedia().checkedOnSizeButUnknown());
    }

    @Test
    public void itemDescriptionsPreferTheLongestSource() throws Exception {
        String url = publishAsset("durations.xml", "application/rss+xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        assertEquals("Item subtitle", FeedRobot.itemByGuid(feed, "seconds").getDescription());
        assertEquals("Item summary that is longer than the subtitle",
                FeedRobot.itemByGuid(feed, "minutes").getDescription());
    }

    @Test
    public void legacyRssDialectIsSupported() throws Exception {
        String url = publishAsset("rss091.xml", "text/xml");
        FeedRobot.subscribeByUrl(url);
        Feed feed = FeedRobot.awaitFeed(url);

        assertEquals("Legacy Feed", feed.getTitle());
        assertEquals("en-us", feed.getLanguage());
        List<String> titles = new ArrayList<>();
        for (FeedItem item : feed.getItems()) {
            titles.add(item.getTitle());
        }
        assertEquals(List.of("Legacy item"), titles);
        assertEquals(server.getBaseUrl() + "/media/legacy.mp3", feed.getItems().get(0).getMedia().getDownloadUrl());
    }
}
