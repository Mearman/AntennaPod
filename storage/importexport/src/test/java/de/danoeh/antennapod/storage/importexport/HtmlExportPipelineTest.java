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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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

    private static List<String> feedSections(String html) {
        List<String> sections = new ArrayList<>(List.of(html.split(Pattern.quote("<li><div>"))));
        sections.remove(0);
        return sections;
    }

    private static FeedItem episodeTitled(Feed feed, String title) {
        for (FeedItem item : DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD, 0,
                Integer.MAX_VALUE)) {
            if (title.equals(item.getTitle())) {
                return item;
            }
        }
        throw new AssertionError("No stored episode titled " + title);
    }

    @Test
    public void favoritesPageGroupsFavoriteEpisodesUnderTheirFeed() throws Exception {
        Feed first = subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one",
                "https://example.com/1.png", "Alpha", "Beta");
        Feed second = subscribe("https://example.com/two.xml", "Second podcast", "https://example.com/two",
                "https://example.com/2.png", "Gamma");
        DBWriter.addFavoriteItems(List.of(episodeTitled(second, "Gamma"), episodeTitled(first, "Alpha"),
                episodeTitled(first, "Beta")));
        DBWriter.tearDownTests();

        String html = exportFavorites();

        assertTrue(html.contains("<title>AntennaPod Favorites</title>"));
        List<String> sections = feedSections(html);
        assertEquals(2, sections.size());
        String firstSection = sections.get(0);
        String secondSection = sections.get(1);
        assertTrue(firstSection.contains("https://example.com/1.png"));
        assertTrue(firstSection.contains("First podcast"));
        assertTrue(firstSection.contains("Alpha<br>"));
        assertTrue(firstSection.contains("Beta<br>"));
        assertTrue(firstSection.indexOf("Beta<br>") < firstSection.indexOf("Alpha<br>"));
        assertFalse(firstSection.contains("Gamma"));
        assertTrue(secondSection.contains("https://example.com/2.png"));
        assertTrue(secondSection.contains("Second podcast"));
        assertTrue(secondSection.contains("Gamma<br>"));
        assertFalse(secondSection.contains("Alpha"));
        assertFalse(secondSection.contains("Beta"));
        assertTrue(secondSection.contains("href=\"https://example.com/two/episode-0\""));
        assertTrue(secondSection.contains("href=\"https://example.com/two.xml/episode-0.mp3\""));
    }

    @Test
    public void favoritesPageOnlyContainsEpisodesMarkedAsFavorite() throws Exception {
        Feed feed = subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one",
                "https://example.com/1.png", "Chosen", "Ignored");
        DBWriter.toggleFavoriteItem(episodeTitled(feed, "Chosen"));
        DBWriter.tearDownTests();

        String html = exportFavorites();

        assertTrue(html.contains("Chosen<br>"));
        assertFalse(html.contains("Ignored"));
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
