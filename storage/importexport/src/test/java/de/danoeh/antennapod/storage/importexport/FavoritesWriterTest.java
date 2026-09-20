package de.danoeh.antennapod.storage.importexport;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FavoritesWriterTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    private Feed createFeed(long id, String title) {
        Feed feed = new Feed(id, null, title, "https://example.com/feed" + id, null, null, null, null, null, null,
                "https://example.com/feed" + id + ".png", null, "https://example.com/feed" + id + ".xml", 0);
        return feed;
    }

    private FeedItem createFavorite(Feed feed, long id, String title, String link, String mediaUrl) {
        FeedItem item = new FeedItem(id, title, "guid" + id, link, new Date(), FeedItem.NEW, feed);
        item.setFeedId(feed.getId());
        if (mediaUrl != null) {
            item.setMedia(new FeedMedia(id, item, 0, 0, 0, "audio/mpeg", null, mediaUrl, 0, null, 0, 0));
        }
        return item;
    }

    private String write(FeedItem... favorites) throws Exception {
        StringWriter writer = new StringWriter();
        FavoritesWriter.writeDocument(Arrays.asList(favorites), writer, context);
        return writer.toString();
    }

    private int count(String haystack, String needle) {
        int count = 0;
        for (int index = haystack.indexOf(needle); index >= 0; index = haystack.indexOf(needle, index + 1)) {
            count++;
        }
        return count;
    }

    @Test
    public void documentUsesFavoritesTitleAndReplacesAllPlaceholders() throws Exception {
        Feed feed = createFeed(1, "Feed One");

        String html = write(createFavorite(feed, 10, "Episode", "https://example.com/e", "https://example.com/e.mp3"));

        assertTrue(html.contains("<title>AntennaPod Favorites</title>"));
        assertFalse(html.contains("{TITLE}"));
        assertFalse(html.contains("{FEEDS}"));
        assertFalse(html.contains("{FEED_"));
        assertFalse(html.contains("{FAV_"));
    }

    @Test
    public void favoriteIsWrittenWithTitleWebsiteAndMediaUrl() throws Exception {
        Feed feed = createFeed(1, "Feed One");

        String html = write(createFavorite(feed, 10, "  Episode Title  ", "https://example.com/e",
                "https://example.com/e.mp3"));

        assertTrue(html.contains("Episode Title<br>"));
        assertTrue(html.contains("<a href=\"https://example.com/e\">Website</a>"));
        assertTrue(html.contains("<a href=\"https://example.com/e.mp3\">Media</a>"));
    }

    @Test
    public void feedInformationIsWrittenOncePerFeed() throws Exception {
        Feed feed = createFeed(1, "Feed One");

        String html = write(
                createFavorite(feed, 10, "First", "https://example.com/1", "https://example.com/1.mp3"),
                createFavorite(feed, 11, "Second", "https://example.com/2", "https://example.com/2.mp3"));

        assertEquals(1, count(html, "<img src=\"https://example.com/feed1.png\" />"));
        assertTrue(html.contains("Feed One"));
        assertTrue(html.contains("<a href=\"https://example.com/feed1\">Website</a>"));
        assertTrue(html.contains("<a href=\"https://example.com/feed1.xml\">Feed</a>"));
        assertEquals(2, count(html, ">Media</a>"));
    }

    @Test
    public void favoritesAreGroupedByFeedInFeedIdOrder() throws Exception {
        Feed feedOne = createFeed(1, "Feed One");
        Feed feedTwo = createFeed(2, "Feed Two");

        String html = write(
                createFavorite(feedTwo, 20, "Two A", "https://example.com/2a", "https://example.com/2a.mp3"),
                createFavorite(feedOne, 10, "One A", "https://example.com/1a", "https://example.com/1a.mp3"),
                createFavorite(feedTwo, 21, "Two B", "https://example.com/2b", "https://example.com/2b.mp3"));

        assertEquals(2, count(html, "<li><div>\n"));
        int feedOneIndex = html.indexOf("Feed One");
        int feedTwoIndex = html.indexOf("Feed Two");
        assertTrue(feedOneIndex < feedTwoIndex);
        assertTrue(feedOneIndex < html.indexOf("One A"));
        assertTrue(html.indexOf("One A") < feedTwoIndex);
        assertTrue(feedTwoIndex < html.indexOf("Two A"));
        assertTrue(html.indexOf("Two A") < html.indexOf("Two B"));
    }

    @Test
    public void missingLinkAndMediaAreWrittenAsEmptyUrls() throws Exception {
        Feed feed = createFeed(1, "Feed One");

        String html = write(createFavorite(feed, 10, "No Links", null, null));

        assertTrue(html.contains("<a href=\"\">Website</a>"));
        assertTrue(html.contains("<a href=\"\">Media</a>"));
    }

    @Test
    public void mediaWithoutDownloadUrlIsWrittenAsEmptyUrl() throws Exception {
        Feed feed = createFeed(1, "Feed One");
        FeedItem item = createFavorite(feed, 10, "Local Only", "https://example.com/e", null);
        item.setMedia(new FeedMedia(10, item, 0, 0, 0, "audio/mpeg", "/local/file.mp3", null, 0, null, 0, 0));

        String html = write(item);

        assertTrue(html.contains("<a href=\"https://example.com/e\">Website</a>"));
        assertTrue(html.contains("<a href=\"\">Media</a>"));
    }

    @Test
    public void noFavoritesProducesTemplateWithoutEntries() throws Exception {
        StringWriter writer = new StringWriter();
        FavoritesWriter.writeDocument(Collections.emptyList(), writer, context);

        assertTrue(writer.toString().contains("<ul>"));
        assertFalse(writer.toString().contains("<li><div>\n"));
    }
}
