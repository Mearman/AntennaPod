package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DbReaderSearchTest extends DatabaseTestBase {
    private Feed science;
    private Feed history;
    private Feed archived;

    @Before
    public void createSearchableContent() {
        science = storeFeed("Science Weekly");
        science.setAuthor("Ada Lovelace");
        science.setDescription("Discoveries from the laboratory");
        history = storeFeed("History Hour");
        history.setCustomTitle("Old Stories");
        history.setAuthor("Herodotus");
        archived = storeFeed("Archived Talks");
        archived.setState(Feed.STATE_ARCHIVED);
        for (Feed feed : Arrays.asList(science, history, archived)) {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setCompleteFeed(feed);
            adapter.setFeedCustomTitle(feed.getId(), feed.getCustomTitle());
            adapter.close();
        }

        storeDescribedItem(science, "Black holes explained", "A tour of gravity and light", 3000);
        storeDescribedItem(science, "Quantum computing", "Qubits and black box models", 2000);
        storeDescribedItem(history, "The Roman Empire", "Rise and fall of Rome", 4000);
        storeDescribedItem(history, "Rock 'n' roll history", "It's only rock and roll", 1000);
        storeDescribedItem(archived, "Black market", "Old trade routes", 5000);
    }

    private void storeDescribedItem(Feed feed, String title, String description, long pubDate) {
        FeedItem item = new FeedItem(0, title, "guid-" + title, "link-" + title, new Date(pubDate),
                FeedItem.UNPLAYED, feed);
        item.setDescriptionIfLonger(description);
        await(DBWriter.setFeedItem(item, false));
    }

    private static List<String> titles(List<FeedItem> items) {
        List<String> result = new ArrayList<>();
        for (FeedItem item : items) {
            result.add(item.getTitle());
        }
        return result;
    }

    private static List<String> feedTitles(List<Feed> feeds) {
        List<String> result = new ArrayList<>();
        for (Feed feed : feeds) {
            result.add(feed.getFeedTitle());
        }
        return result;
    }

    @Test
    public void itemSearchMatchesTitleAndDescriptionIgnoringCase() {
        assertEquals(Arrays.asList("Black holes explained", "Quantum computing"),
                titles(DBReader.searchFeedItems(0, "BLACK", FeedItemFilter.unfiltered())));
        assertEquals(Collections.singletonList("The Roman Empire"),
                titles(DBReader.searchFeedItems(0, "rome", FeedItemFilter.unfiltered())));
    }

    @Test
    public void itemSearchRequiresEveryWordToMatch() {
        assertEquals(Collections.singletonList("Black holes explained"),
                titles(DBReader.searchFeedItems(0, "black gravity", FeedItemFilter.unfiltered())));
        assertTrue(DBReader.searchFeedItems(0, "black rome", FeedItemFilter.unfiltered()).isEmpty());
    }

    @Test
    public void itemSearchCanBeRestrictedToOneFeed() {
        assertEquals(Collections.singletonList("Quantum computing"),
                titles(DBReader.searchFeedItems(science.getId(), "quantum", FeedItemFilter.unfiltered())));
        assertTrue(DBReader.searchFeedItems(history.getId(), "quantum", FeedItemFilter.unfiltered()).isEmpty());
    }

    @Test
    public void itemSearchAcrossFeedsHidesArchivedFeedsByDefault() {
        assertEquals(Arrays.asList("Black holes explained", "Quantum computing"),
                titles(DBReader.searchFeedItems(0, "black", FeedItemFilter.unfiltered())));
        assertEquals(Arrays.asList("Black market", "Black holes explained", "Quantum computing"),
                titles(DBReader.searchFeedItems(0, "black",
                        new FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES))));
    }

    @Test
    public void itemSearchInsideFeedIgnoresFeedState() {
        assertEquals(Collections.singletonList("Black market"),
                titles(DBReader.searchFeedItems(archived.getId(), "black", FeedItemFilter.unfiltered())));
    }

    @Test
    public void itemSearchCombinesWithEpisodeFilter() {
        FeedItem quantum = DBReader.searchFeedItems(0, "quantum", FeedItemFilter.unfiltered()).get(0);
        await(DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(quantum)));

        assertEquals(Collections.singletonList("Quantum computing"),
                titles(DBReader.searchFeedItems(0, "black", new FeedItemFilter(FeedItemFilter.PLAYED))));
        assertEquals(Collections.singletonList("Black holes explained"),
                titles(DBReader.searchFeedItems(0, "black", new FeedItemFilter(FeedItemFilter.UNPLAYED))));
    }

    @Test
    public void itemSearchEscapesQuotesInQuery() {
        assertEquals(Collections.singletonList("Rock 'n' roll history"),
                titles(DBReader.searchFeedItems(0, "'n'", FeedItemFilter.unfiltered())));
        assertEquals(Collections.singletonList("Rock 'n' roll history"),
                titles(DBReader.searchFeedItems(0, "it's", FeedItemFilter.unfiltered())));
    }

    @Test
    public void itemSearchResultsKnowTheirFeed() {
        FeedItem result = DBReader.searchFeedItems(0, "roman", FeedItemFilter.unfiltered()).get(0);

        assertEquals("History Hour", result.getFeed().getFeedTitle());
    }

    @Test
    public void feedSearchMatchesTitleCustomTitleAuthorAndDescription() {
        assertEquals(Collections.singletonList("Science Weekly"),
                feedTitles(DBReader.searchFeeds("science", FeedItemFilter.unfiltered())));
        assertEquals(Collections.singletonList("History Hour"),
                feedTitles(DBReader.searchFeeds("old stories", FeedItemFilter.unfiltered())));
        assertEquals(Collections.singletonList("Science Weekly"),
                feedTitles(DBReader.searchFeeds("lovelace", FeedItemFilter.unfiltered())));
        assertEquals(Collections.singletonList("Science Weekly"),
                feedTitles(DBReader.searchFeeds("laboratory", FeedItemFilter.unfiltered())));
    }

    @Test
    public void feedSearchRequiresEveryWordToMatch() {
        assertEquals(Collections.singletonList("Science Weekly"),
                feedTitles(DBReader.searchFeeds("ada laboratory", FeedItemFilter.unfiltered())));
        assertTrue(DBReader.searchFeeds("ada herodotus", FeedItemFilter.unfiltered()).isEmpty());
    }

    @Test
    public void feedSearchOnlyReturnsRequestedFeedStates() {
        assertTrue(DBReader.searchFeeds("archived", FeedItemFilter.unfiltered()).isEmpty());
        assertEquals(Collections.singletonList("Archived Talks"),
                feedTitles(DBReader.searchFeeds("archived", new FeedItemFilter(FeedItemFilter.INCLUDE_ARCHIVED))));
        assertEquals(Arrays.asList("Archived Talks", "Science Weekly"),
                feedTitles(DBReader.searchFeeds("a", new FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES))));
        assertEquals(Collections.singletonList("Archived Talks"),
                feedTitles(DBReader.searchFeeds("talks", new FeedItemFilter(FeedItemFilter.INCLUDE_ARCHIVED,
                        FeedItemFilter.INCLUDE_NOT_SUBSCRIBED))));
    }
}
