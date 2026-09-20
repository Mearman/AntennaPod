package de.danoeh.antennapod.storage.importexport;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.StringWriter;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class HtmlExportPipelineTest extends ImportExportPipelineTestBase {

    private String exportSubscriptions() throws Exception {
        StringWriter writer = new StringWriter();
        HtmlWriter.writeDocument(storedFeeds(), writer, context);
        return writer.toString();
    }

    private List<FeedItem> favorites() {
        return DBReader.getEpisodes(0, Integer.MAX_VALUE, new FeedItemFilter(FeedItemFilter.IS_FAVORITE),
                SortOrder.DATE_NEW_OLD);
    }

    private String exportFavorites() throws Exception {
        StringWriter writer = new StringWriter();
        FavoritesWriter.writeDocument(favorites(), writer, context);
        return writer.toString();
    }

    @Test
    public void subscriptionsPageListsEachSubscribedFeedWithImageWebsiteAndFeedAddress() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one", "https://example.com/1.png");
        subscribe("https://example.com/two.xml", "Second podcast", "https://example.com/two", "https://example.com/2.png");

        String html = exportSubscriptions();

        assertTrue(html.contains("<title>AntennaPod Subscriptions</title>"));
        assertTrue(html.contains("<img src=\"https://example.com/1.png\" /><p>First podcast"
                + " <span><a href=\"https://example.com/one\">Website</a>"));
        assertTrue(html.contains("<a href=\"https://example.com/one.xml\">Feed</a>"));
        assertTrue(html.indexOf("First podcast") < html.indexOf("Second podcast"));
        assertFalse(html.contains("{FEEDS}"));
    }

    @Test
    public void subscriptionsPageLeavesOutFeedsThatAreNotSubscribed() throws Exception {
        subscribe("https://example.com/one.xml", "Kept", "https://example.com/one", "https://example.com/1.png");
        store("https://example.com/two.xml", "Not kept", "https://example.com/two", "https://example.com/2.png",
                Feed.STATE_NOT_SUBSCRIBED);

        String html = exportSubscriptions();

        assertTrue(html.contains("Kept"));
        assertFalse(html.contains("Not kept"));
    }

    @Test
    public void favoritesPageGroupsFavoriteEpisodesUnderTheirFeed() throws Exception {
        Feed first = subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one",
                "https://example.com/1.png", "Alpha", "Beta");
        Feed second = subscribe("https://example.com/two.xml", "Second podcast", "https://example.com/two",
                "https://example.com/2.png", "Gamma");
        List<FeedItem> firstItems = DBReader.getFeedItemList(first, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        List<FeedItem> secondItems = DBReader.getFeedItemList(second, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        DBWriter.addFavoriteItems(List.of(secondItems.get(0), firstItems.get(0), firstItems.get(1)));
        DBWriter.tearDownTests();

        String html = exportFavorites();

        assertTrue(html.contains("<title>AntennaPod Favorites</title>"));
        int firstFeed = html.indexOf("First podcast");
        int secondFeed = html.indexOf("Second podcast");
        assertTrue(firstFeed >= 0 && firstFeed < secondFeed);
        int alpha = html.indexOf("Alpha");
        int beta = html.indexOf("Beta");
        int gamma = html.indexOf("Gamma");
        assertTrue(firstFeed < beta && firstFeed < alpha);
        assertTrue(beta < alpha);
        assertTrue(secondFeed < gamma);
        assertTrue(alpha < secondFeed && beta < secondFeed);
        assertTrue(html.contains("<a href=\"https://example.com/two/episode-0\">Website</a>"));
        assertTrue(html.contains("<a href=\"https://example.com/two.xml/episode-0.mp3\">Media</a>"));
    }

    @Test
    public void favoritesPageOnlyContainsEpisodesMarkedAsFavorite() throws Exception {
        Feed feed = subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one",
                "https://example.com/1.png", "Chosen", "Ignored");
        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        DBWriter.toggleFavoriteItem(items.get(0));
        DBWriter.tearDownTests();

        String html = exportFavorites();

        assertTrue(html.contains(items.get(0).getTitle()));
        assertFalse(html.contains(items.get(1).getTitle()));
        assertEquals(1, favorites().size());
    }

    @Test
    public void favoritesPageWithoutFavoritesContainsNoFeedSections() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one",
                "https://example.com/1.png", "Alpha");

        String html = exportFavorites();

        assertTrue(html.contains("<title>AntennaPod Favorites</title>"));
        assertFalse(html.contains("First podcast"));
    }
}
