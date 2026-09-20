package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class FeedItemFilterPipelineTest extends FeedPipelineTestBase {
    private static final String[] SINGLE_PROPERTIES = {
            FeedItemFilter.PLAYED, FeedItemFilter.UNPLAYED, FeedItemFilter.NEW, FeedItemFilter.PAUSED,
            FeedItemFilter.NOT_PAUSED, FeedItemFilter.IS_FAVORITE, FeedItemFilter.NOT_FAVORITE,
            FeedItemFilter.HAS_MEDIA, FeedItemFilter.NO_MEDIA, FeedItemFilter.QUEUED, FeedItemFilter.NOT_QUEUED,
            FeedItemFilter.DOWNLOADED,
            FeedItemFilter.INCLUDE_SUBSCRIBED, FeedItemFilter.INCLUDE_ARCHIVED,
            FeedItemFilter.INCLUDE_NOT_SUBSCRIBED, FeedItemFilter.INCLUDE_ALL_FEED_STATES,
    };

    private static String item(String guid, boolean withMedia) {
        return "<item><guid>" + guid + "</guid><title>" + guid + "</title>"
                + "<pubDate>Mon, 02 Jan 2006 15:04:05 +0000</pubDate>"
                + (withMedia ? "<enclosure url=\"https://example.com/" + guid
                + ".mp3\" length=\"5000000\" type=\"audio/mpeg\"/>" : "")
                + "</item>\n";
    }

    @Before
    public void createEpisodesInEveryState() throws Exception {
        Feed subscribed = parseAndStore(rss("<title>Subscribed</title>\n"
                + item("played", true) + item("paused", true) + item("new", true) + item("no-media", false)
                + item("favorite", true) + item("queued", true) + item("downloaded", true)
                + item("in-history", true) + item("plain", true)));
        Feed browsed = storeParsedInState(rss("<title>Browsed</title>\n" + item("browsed", true)),
                "https://example.com/browsed.xml", Feed.STATE_NOT_SUBSCRIBED);
        storeParsedInState(rss("<title>Archived</title>\n" + item("archived", true)),
                "https://example.com/archived.xml", Feed.STATE_ARCHIVED);
        assertEquals(Feed.STATE_NOT_SUBSCRIBED, browsed.getState());

        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, List.of(storedItem(subscribed, "played")));
        FeedItem paused = storedItem(subscribed, "paused");
        paused.getMedia().setPosition(60000);
        paused.getMedia().setLastPlayedTimeHistory(new Date(0));
        DBWriter.setFeedMediaPlaybackInformation(paused.getMedia());
        DBWriter.markItemsPlayed(FeedItem.NEW, false, List.of(storedItem(subscribed, "new")));
        DBWriter.addFavoriteItems(List.of(storedItem(subscribed, "favorite")));
        DBWriter.addQueueItem(context, storedItem(subscribed, "queued"));
        FeedItem downloaded = storedItem(subscribed, "downloaded");
        downloaded.getMedia().setLocalFileUrl("/downloads/downloaded.mp3");
        downloaded.getMedia().setDownloaded(true, new Date().getTime());
        DBWriter.setMediaDownloadInformation(downloaded.getMedia());
        DBWriter.addItemToPlaybackHistory(storedItem(subscribed, "in-history").getMedia());
        DBWriter.tearDownTests();
    }

    private Feed storeParsedInState(String document, String url, int state) throws Exception {
        Feed parsed = parse(document, url).feed;
        parsed.setState(state);
        return storeParsed(parsed);
    }

    private List<FeedItem> query(FeedItemFilter filter) {
        return DBReader.getEpisodes(0, Integer.MAX_VALUE, filter, SortOrder.EPISODE_TITLE_A_Z);
    }

    private Set<String> titles(List<FeedItem> items) {
        Set<String> titles = new TreeSet<>();
        for (FeedItem item : items) {
            titles.add(item.getTitle());
        }
        return titles;
    }

    private Set<String> titlesOf(FeedItemFilter filter) {
        return titles(query(filter));
    }

    @Test
    public void filteringInTheDatabaseSelectsTheEpisodesInTheMatchingState() {
        assertEquals(Set.of("played"), titlesOf(new FeedItemFilter(FeedItemFilter.PLAYED)));
        assertEquals(Set.of("paused"), titlesOf(new FeedItemFilter(FeedItemFilter.PAUSED)));
        assertEquals(Set.of("new"), titlesOf(new FeedItemFilter(FeedItemFilter.NEW)));
        assertEquals(Set.of("favorite"), titlesOf(new FeedItemFilter(FeedItemFilter.IS_FAVORITE)));
        assertEquals(Set.of("queued"), titlesOf(new FeedItemFilter(FeedItemFilter.QUEUED)));
        assertEquals(Set.of("downloaded"), titlesOf(new FeedItemFilter(FeedItemFilter.DOWNLOADED)));
        assertEquals(Set.of("in-history"), titlesOf(new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY)));
        assertEquals(Set.of("no-media"), titlesOf(new FeedItemFilter(FeedItemFilter.NO_MEDIA)));
    }

    @Test
    public void episodesOfUnsubscribedAndArchivedFeedsAreOnlyIncludedWhenRequested() {
        assertFalse(titlesOf(FeedItemFilter.unfiltered()).contains("browsed"));
        assertFalse(titlesOf(FeedItemFilter.unfiltered()).contains("archived"));
        assertEquals(Set.of("browsed"), titlesOf(new FeedItemFilter(FeedItemFilter.INCLUDE_NOT_SUBSCRIBED)));
        assertEquals(Set.of("archived"), titlesOf(new FeedItemFilter(FeedItemFilter.INCLUDE_ARCHIVED)));
        assertTrue(titlesOf(new FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES)).containsAll(
                Set.of("browsed", "archived", "plain")));
    }

    @Test
    public void inMemoryMatchingAgreesWithTheDatabaseQueryForEverySingleProperty() {
        List<FeedItem> everything = query(new FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES));
        assertEquals(11, everything.size());

        for (String property : SINGLE_PROPERTIES) {
            FeedItemFilter filter = new FeedItemFilter(property);
            assertEquals(property, matching(filter, everything), titlesOf(filter));
        }
    }

    @Test
    public void inMemoryMatchingAgreesWithTheDatabaseQueryForCombinedProperties() {
        List<FeedItem> everything = query(new FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES));
        List<FeedItemFilter> filters = new ArrayList<>();
        filters.add(new FeedItemFilter(FeedItemFilter.UNPLAYED, FeedItemFilter.DOWNLOADED));
        filters.add(new FeedItemFilter(FeedItemFilter.NEW, FeedItemFilter.HAS_MEDIA));
        filters.add(new FeedItemFilter(FeedItemFilter.QUEUED, FeedItemFilter.NOT_FAVORITE));
        filters.add(new FeedItemFilter(FeedItemFilter.NOT_PAUSED, FeedItemFilter.NOT_QUEUED));
        filters.add(new FeedItemFilter(FeedItemFilter.IS_FAVORITE, FeedItemFilter.INCLUDE_SUBSCRIBED,
                FeedItemFilter.INCLUDE_ARCHIVED));
        filters.add(new FeedItemFilter(FeedItemFilter.HAS_MEDIA, FeedItemFilter.INCLUDE_ALL_FEED_STATES));
        filters.add(new FeedItemFilter(FeedItemFilter.NO_MEDIA, FeedItemFilter.NOT_QUEUED));

        for (FeedItemFilter filter : filters) {
            assertEquals(String.join(",", filter.getValues()), matching(filter, everything), titlesOf(filter));
        }
    }

    @Test
    public void notDownloadedSelectsEpisodesWithMediaThatIsNotDownloaded() {
        Set<String> expected = Set.of("played", "paused", "new", "favorite", "queued", "in-history", "plain");

        assertEquals(expected, titlesOf(new FeedItemFilter(FeedItemFilter.NOT_DOWNLOADED)));
        assertEquals(expected, matching(new FeedItemFilter(FeedItemFilter.NOT_DOWNLOADED), withMedia()));
    }

    @Test
    public void episodesInThePlaybackHistoryAreThoseWithACompletionDate() {
        List<FeedItem> history = query(new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY));

        assertEquals(Set.of("in-history"), titles(history));
        assertTrue(history.get(0).getMedia().getLastPlayedTimeHistory().getTime() > 0);
    }

    @Test
    public void filterCanBeExtendedAndReducedWithoutChangingTheOriginal() {
        FeedItemFilter base = new FeedItemFilter(FeedItemFilter.UNPLAYED);

        FeedItemFilter extended = new FeedItemFilter(base, FeedItemFilter.DOWNLOADED);
        FeedItemFilter reduced = extended.without(FeedItemFilter.UNPLAYED);

        assertEquals(List.of(FeedItemFilter.UNPLAYED), base.getValuesList());
        assertTrue(extended.showUnplayed && extended.showDownloaded);
        assertFalse(reduced.showUnplayed);
        assertTrue(reduced.showDownloaded);
        assertEquals(Set.of("downloaded"), titlesOf(reduced));
    }

    private List<FeedItem> withMedia() {
        return query(new FeedItemFilter(FeedItemFilter.HAS_MEDIA));
    }

    private Set<String> matching(FeedItemFilter filter, List<FeedItem> items) {
        Set<String> titles = new TreeSet<>();
        for (FeedItem item : items) {
            if (filter.matches(item)) {
                titles.add(item.getTitle());
            }
        }
        return titles;
    }
}
