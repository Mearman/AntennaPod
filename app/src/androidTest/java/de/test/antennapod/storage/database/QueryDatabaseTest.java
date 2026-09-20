package de.test.antennapod.storage.database;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.StatisticsItem;
import de.test.antennapod.service.download.DownloadTestFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class QueryDatabaseTest {
    private static final long HOUR_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final int URL_LIST_LARGER_THAN_SQL_LIMIT = 1000;
    private static final int MAXIMUM_URLS_PER_QUERY = 800;
    private static final int RANDOM_SEEDS = 20;

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Context context;
    private Feed alpha;
    private Feed beta;
    private Feed gamma;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        alpha = fixture.subscribe("Alpha", 4);
        beta = fixture.subscribe("Beta", 3);
        Feed textOnly = fixture.newFeed("Gamma", 2);
        for (FeedItem item : textOnly.getItems()) {
            item.setMedia(null);
        }
        textOnly.setDownloadUrl(fixture.hostFeed(textOnly));
        gamma = fixture.subscribe(textOnly);

        DBWriter.markItemsPlayed(FeedItem.NEW, false, Collections.singletonList(alpha.getItemAtIndex(0))).get();
        fixture.markDownloaded(alpha.getItemAtIndex(1));
        fixture.markPlayed(alpha.getItemAtIndex(2), System.currentTimeMillis() - HOUR_MILLIS);
        DBWriter.addQueueItem(context, alpha.getItemAtIndex(3)).get();
        FeedMedia paused = DBReader.getFeedMedia(alpha.getItemAtIndex(3).getMedia().getId());
        paused.setPosition(90_000);
        paused.setLastPlayedTimeHistory(new Date(0));
        DBWriter.setFeedMediaPlaybackInformation(paused).get();
        fixture.markDownloaded(beta.getItemAtIndex(0));
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(beta.getItemAtIndex(0))).get();
        DBWriter.addFavoriteItems(Collections.singletonList(beta.getItemAtIndex(0))).get();
    }

    @After
    public void tearDown() throws Exception {
        fixture.tearDown();
    }

    private int count(String... filter) {
        return DBReader.getTotalEpisodeCount(new FeedItemFilter(filter));
    }

    private List<String> titles(List<FeedItem> items) {
        List<String> titles = new ArrayList<>();
        for (FeedItem item : items) {
            titles.add(item.getTitle());
        }
        return titles;
    }

    private List<FeedItem> episodes(SortOrder order, String... filter) {
        return DBReader.getEpisodes(0, Integer.MAX_VALUE, new FeedItemFilter(filter), order);
    }

    @Test
    public void filtersSelectEpisodesByTheirState() throws Exception {
        assertEquals(9, count());
        assertEquals(1, count(FeedItemFilter.NEW));
        assertEquals(2, count(FeedItemFilter.PLAYED));
        assertEquals(1, count(FeedItemFilter.PAUSED));
        assertEquals(8, count(FeedItemFilter.NOT_PAUSED));
        assertEquals(1, count(FeedItemFilter.QUEUED));
        assertEquals(8, count(FeedItemFilter.NOT_QUEUED));
        assertEquals(2, count(FeedItemFilter.DOWNLOADED));
        assertEquals(5, count(FeedItemFilter.NOT_DOWNLOADED));
        assertEquals(1, count(FeedItemFilter.IS_FAVORITE));
        assertEquals(8, count(FeedItemFilter.NOT_FAVORITE));
        assertEquals(7, count(FeedItemFilter.HAS_MEDIA));
        assertEquals(2, count(FeedItemFilter.NO_MEDIA));
        assertEquals(1, count(FeedItemFilter.IS_IN_HISTORY));
    }

    @Test
    public void filtersCanBeCombined() throws Exception {
        assertEquals(1, count(FeedItemFilter.DOWNLOADED, FeedItemFilter.PLAYED));
        assertEquals(1, count(FeedItemFilter.DOWNLOADED, FeedItemFilter.NOT_FAVORITE));
        assertEquals(0, count(FeedItemFilter.NEW, FeedItemFilter.DOWNLOADED));
        assertEquals(1, count(FeedItemFilter.QUEUED, FeedItemFilter.PAUSED));
        assertEquals(2, count(FeedItemFilter.NO_MEDIA, FeedItemFilter.NOT_QUEUED, FeedItemFilter.UNPLAYED));
    }

    @Test
    public void unplayedFilterExcludesPlayedEpisodes() throws Exception {
        assertEquals(7, count(FeedItemFilter.UNPLAYED));
        List<FeedItem> unplayed = episodes(SortOrder.DATE_NEW_OLD, FeedItemFilter.UNPLAYED);
        for (FeedItem item : unplayed) {
            assertTrue(!item.isPlayed());
        }
    }

    @Test
    public void feedStatesCanBeIncludedInEpisodeCounts() throws Exception {
        DBWriter.setFeedState(context, DBReader.getFeed(beta.getId(), false, 0, Integer.MAX_VALUE),
                Feed.STATE_ARCHIVED).get();
        DBWriter.setFeedState(context, DBReader.getFeed(gamma.getId(), false, 0, Integer.MAX_VALUE),
                Feed.STATE_NOT_SUBSCRIBED).get();

        assertEquals(4, count());
        assertEquals(7, count(FeedItemFilter.INCLUDE_SUBSCRIBED + "," + FeedItemFilter.INCLUDE_ARCHIVED));
        assertEquals(9, count(FeedItemFilter.INCLUDE_ALL_FEED_STATES));
        assertEquals(2, count(FeedItemFilter.INCLUDE_NOT_SUBSCRIBED));
        assertEquals(3, DBReader.getFeedEpisodeCount(beta.getId(), FeedItemFilter.unfiltered()));
        assertEquals(1, DBReader.getFeedEpisodeCount(beta.getId(), new FeedItemFilter(FeedItemFilter.DOWNLOADED)));
    }

    @Test
    public void episodesOfOneFeedAreCountedWithTheFilter() throws Exception {
        assertEquals(4, DBReader.getFeedEpisodeCount(alpha.getId(), FeedItemFilter.unfiltered()));
        assertEquals(1, DBReader.getFeedEpisodeCount(alpha.getId(), new FeedItemFilter(FeedItemFilter.PLAYED)));
        assertEquals(2, DBReader.getFeedEpisodeCount(gamma.getId(), new FeedItemFilter(FeedItemFilter.NO_MEDIA)));
    }

    private void assertSortedBy(SortOrder order, Comparator<FeedItem> expected, String... filter) {
        List<FeedItem> actual = episodes(order, filter);
        List<FeedItem> sorted = new ArrayList<>(actual);
        Collections.sort(sorted, expected);

        assertEquals(order.name(), count(filter), actual.size());
        assertEquals(order.name(), ids(sorted), ids(actual));
        assertTrue(order.name(), expected.compare(actual.get(0), actual.get(actual.size() - 1)) < 0);
    }

    private List<Long> ids(List<FeedItem> items) {
        List<Long> ids = new ArrayList<>();
        for (FeedItem item : items) {
            ids.add(item.getId());
        }
        return ids;
    }

    private void giveEveryEpisodeItsOwnDurationAndSize() throws Exception {
        int rank = 1;
        for (Feed feed : Arrays.asList(alpha, beta)) {
            for (FeedItem item : feed.getItems()) {
                fixture.setDurationAndSize(item, rank * 1000, (rank * 7919) % 1000 + 1);
                rank++;
            }
        }
    }

    @Test
    public void episodesAreSortedByTitle() throws Exception {
        Comparator<FeedItem> ascending = (a, b) -> a.getTitle().compareTo(b.getTitle());

        assertSortedBy(SortOrder.EPISODE_TITLE_A_Z, ascending);
        assertSortedBy(SortOrder.EPISODE_TITLE_Z_A, Collections.reverseOrder(ascending));
        assertEquals("Alpha episode 0", titles(episodes(SortOrder.EPISODE_TITLE_A_Z)).get(0));
        assertEquals("Gamma episode 1", titles(episodes(SortOrder.EPISODE_TITLE_Z_A)).get(0));
    }

    @Test
    public void episodesAreSortedByDate() throws Exception {
        Comparator<FeedItem> oldestFirst = (a, b) -> a.getPubDate().compareTo(b.getPubDate());

        assertSortedBy(SortOrder.DATE_OLD_NEW, oldestFirst);
        assertSortedBy(SortOrder.DATE_NEW_OLD, Collections.reverseOrder(oldestFirst));
    }

    @Test
    public void episodesAreSortedByLink() throws Exception {
        Comparator<FeedItem> ascending = (a, b) -> a.getLink().compareTo(b.getLink());

        assertSortedBy(SortOrder.EPISODE_FILENAME_A_Z, ascending);
        assertSortedBy(SortOrder.EPISODE_FILENAME_Z_A, Collections.reverseOrder(ascending));
    }

    @Test
    public void episodesAreSortedByDuration() throws Exception {
        giveEveryEpisodeItsOwnDurationAndSize();
        Comparator<FeedItem> shortestFirst = (a, b) ->
                Integer.compare(a.getMedia().getDuration(), b.getMedia().getDuration());

        assertSortedBy(SortOrder.DURATION_SHORT_LONG, shortestFirst, FeedItemFilter.HAS_MEDIA);
        assertSortedBy(SortOrder.DURATION_LONG_SHORT, Collections.reverseOrder(shortestFirst),
                FeedItemFilter.HAS_MEDIA);
    }

    @Test
    public void episodesAreSortedBySize() throws Exception {
        giveEveryEpisodeItsOwnDurationAndSize();
        Comparator<FeedItem> smallestFirst = (a, b) -> Long.compare(a.getMedia().getSize(), b.getMedia().getSize());

        assertSortedBy(SortOrder.SIZE_SMALL_LARGE, smallestFirst, FeedItemFilter.HAS_MEDIA);
        assertSortedBy(SortOrder.SIZE_LARGE_SMALL, Collections.reverseOrder(smallestFirst), FeedItemFilter.HAS_MEDIA);
    }

    @Test
    public void historyIsSortedByCompletionDate() throws Exception {
        long now = System.currentTimeMillis();
        fixture.markPlayed(beta.getItemAtIndex(1), now - 3 * HOUR_MILLIS);
        fixture.markPlayed(beta.getItemAtIndex(2), now - 2 * HOUR_MILLIS);

        List<FeedItem> history = episodes(SortOrder.COMPLETION_DATE_NEW_OLD, FeedItemFilter.IS_IN_HISTORY);

        assertEquals(Arrays.asList(alpha.getItemAtIndex(2).getId(), beta.getItemAtIndex(2).getId(),
                beta.getItemAtIndex(1).getId()), ids(history));
    }

    @Test
    public void episodesArePagedWithOffsetAndLimit() throws Exception {
        List<FeedItem> all = episodes(SortOrder.EPISODE_TITLE_A_Z);

        List<FeedItem> page = DBReader.getEpisodes(2, 3, FeedItemFilter.unfiltered(), SortOrder.EPISODE_TITLE_A_Z);

        assertEquals(3, page.size());
        assertEquals(all.get(2).getId(), page.get(0).getId());
        assertEquals(all.get(4).getId(), page.get(2).getId());
    }

    @Test
    public void randomEpisodesArePlayableUnplayedAndComeFromDifferentFeeds() throws Exception {
        for (int seed = 1; seed <= RANDOM_SEEDS; seed++) {
            List<FeedItem> random = DBReader.getRandomEpisodes(3, seed);

            assertEquals(2, random.size());
            Set<Long> feedIds = new HashSet<>();
            for (FeedItem item : random) {
                assertNotNull(item.getMedia());
                assertFalse(item.isPlayed());
                feedIds.add(item.getFeedId());
            }
            assertEquals(new HashSet<>(Arrays.asList(alpha.getId(), beta.getId())), feedIds);
            assertEquals(titles(random), titles(DBReader.getRandomEpisodes(3, seed)));
        }
    }

    @Test
    public void randomEpisodesAreLimited() throws Exception {
        assertEquals(1, DBReader.getRandomEpisodes(1, 1).size());
    }

    @Test
    public void episodesCanBeFoundByDownloadUrl() throws Exception {
        String url = alpha.getItemAtIndex(1).getMedia().getDownloadUrl();
        String other = beta.getItemAtIndex(1).getMedia().getDownloadUrl();

        List<FeedItem> found = DBReader.getFeedItemsWithUrl(Arrays.asList(url, other, "http://example.com/none"));

        assertEquals(2, found.size());
        assertTrue(titles(found).containsAll(Arrays.asList("Alpha episode 1", "Beta episode 1")));
    }

    @Test
    public void tooLongUrlListsAreRejected() throws Exception {
        List<String> urls = new ArrayList<>();
        urls.add(alpha.getItemAtIndex(0).getMedia().getDownloadUrl());
        for (int i = 0; i < URL_LIST_LARGER_THAN_SQL_LIMIT; i++) {
            urls.add("http://example.com/missing-" + i + ".mp3");
        }
        urls.add(beta.getItemAtIndex(2).getMedia().getDownloadUrl());

        boolean rejected = false;
        try {
            DBReader.getFeedItemsWithUrl(urls);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }

        assertTrue(rejected);
        assertEquals(1, DBReader.getFeedItemsWithUrl(urls.subList(0, MAXIMUM_URLS_PER_QUERY)).size());
    }

    @Test
    public void episodesCanBeFoundByGuidOrEpisodeUrl() throws Exception {
        FeedItem byGuid = DBReader.getFeedItemByGuidOrEpisodeUrl("Alpha-episode-2", "http://example.com/other.mp3");
        FeedItem byUrl = DBReader.getFeedItemByGuidOrEpisodeUrl(null,
                beta.getItemAtIndex(1).getMedia().getDownloadUrl());

        assertEquals("Alpha episode 2", byGuid.getTitle());
        assertEquals("Beta episode 1", byUrl.getTitle());
        assertNull(DBReader.getFeedItemByGuidOrEpisodeUrl("missing", "http://example.com/other.mp3"));
    }

    @Test
    public void itemsWithoutAFeedAreLeftAlone() throws Exception {
        FeedItem orphan = new FeedItem();
        orphan.setFeedId(987654);

        DBReader.loadFeedDataOfFeedItemList(Collections.singletonList(orphan));

        assertEquals("Error: Item without feed", orphan.getFeed().getTitle());
    }

    @Test
    public void searchFindsEpisodesByTitleAndDescription() throws Exception {
        FeedItem described = DBReader.getFeedItem(beta.getItemAtIndex(2).getId());
        described.setDescriptionIfLonger("Interview about kayaking in Norway");
        DBWriter.setFeedItem(described, false).get();

        List<FeedItem> byTitle = DBReader.searchFeedItems(0, "alpha episode", FeedItemFilter.unfiltered());
        List<FeedItem> byDescription = DBReader.searchFeedItems(0, "kayaking norway", FeedItemFilter.unfiltered());
        List<FeedItem> inFeed = DBReader.searchFeedItems(beta.getId(), "episode", FeedItemFilter.unfiltered());
        List<FeedItem> filtered = DBReader.searchFeedItems(0, "episode", new FeedItemFilter(FeedItemFilter.PLAYED));

        assertEquals(4, byTitle.size());
        assertEquals(1, byDescription.size());
        assertEquals("Beta episode 2", byDescription.get(0).getTitle());
        assertEquals(3, inFeed.size());
        assertEquals(2, filtered.size());
        assertTrue(DBReader.searchFeedItems(0, "nothing like this", FeedItemFilter.unfiltered()).isEmpty());
    }

    @Test
    public void searchEscapesQuotesInTheQuery() throws Exception {
        assertTrue(DBReader.searchFeedItems(0, "it's \"quoted\"", FeedItemFilter.unfiltered()).isEmpty());
        assertTrue(DBReader.searchFeeds("it's", FeedItemFilter.unfiltered()).isEmpty());
    }

    @Test
    public void searchFindsFeedsByTitleAuthorAndState() throws Exception {
        List<Feed> byTitle = DBReader.searchFeeds("alp", FeedItemFilter.unfiltered());
        List<Feed> byAuthor = DBReader.searchFeeds("Author of Beta", FeedItemFilter.unfiltered());
        DBWriter.setFeedState(context, DBReader.getFeed(gamma.getId(), false, 0, Integer.MAX_VALUE),
                Feed.STATE_NOT_SUBSCRIBED).get();
        List<Feed> hidden = DBReader.searchFeeds("gamma", FeedItemFilter.unfiltered());
        List<Feed> shown = DBReader.searchFeeds("gamma", new FeedItemFilter(FeedItemFilter.INCLUDE_NOT_SUBSCRIBED));

        assertEquals(1, byTitle.size());
        assertEquals("Alpha", byTitle.get(0).getTitle());
        assertEquals("Beta", byAuthor.get(0).getTitle());
        assertTrue(hidden.isEmpty());
        assertEquals(1, shown.size());
    }

    @Test
    public void statisticsSumUpThePlayedTimePerFeed() throws Exception {
        long now = System.currentTimeMillis();
        setPlayed(alpha.getItemAtIndex(1), 60_000, now - HOUR_MILLIS);
        setPlayed(alpha.getItemAtIndex(2), 30_000, now - 2 * HOUR_MILLIS);
        setPlayed(beta.getItemAtIndex(0), 10_000, now - 3 * HOUR_MILLIS);

        DBReader.StatisticsResult result = DBReader.getStatistics(true, 0, Long.MAX_VALUE);

        assertEquals(3, result.feedTime.size());
        StatisticsItem alphaStatistics = statisticsOf(result, "Alpha");
        assertEquals(90, alphaStatistics.timePlayed);
        assertEquals(4, alphaStatistics.episodes);
        assertEquals(3, alphaStatistics.episodesStarted);
        assertEquals(1, alphaStatistics.episodesDownloadCount);
        assertTrue(alphaStatistics.totalDownloadSize > 0);
        assertEquals(10, statisticsOf(result, "Beta").timePlayed);
        assertEquals(0, statisticsOf(result, "Gamma").episodesStarted);
        assertTrue(result.oldestDate < now);
        assertEquals(2, statisticsOf(DBReader.getStatistics(false, 0, Long.MAX_VALUE), "Alpha").episodesStarted);
    }

    @Test
    public void statisticsCanBeRestrictedToATimeRange() throws Exception {
        long now = System.currentTimeMillis();
        setPlayed(alpha.getItemAtIndex(1), 60_000, now - 10 * HOUR_MILLIS);
        setPlayed(beta.getItemAtIndex(0), 10_000, now - HOUR_MILLIS);

        DBReader.StatisticsResult recent = DBReader.getStatistics(false, now - 2 * HOUR_MILLIS, now);

        assertEquals(0, statisticsOf(recent, "Alpha").timePlayed);
        assertEquals(10, statisticsOf(recent, "Beta").timePlayed);
    }

    private long utc(int year, int month, int day) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month, day, 12, 0, 0);
        return calendar.getTimeInMillis();
    }

    @Test
    public void monthlyStatisticsListPlayedTimePerMonthInChronologicalOrder() throws Exception {
        setPlayed(alpha.getItemAtIndex(1), 60_000, utc(2024, Calendar.JANUARY, 10));
        setPlayed(alpha.getItemAtIndex(2), 20_000, utc(2024, Calendar.JANUARY, 20));
        setPlayed(beta.getItemAtIndex(1), 5_000, utc(2023, Calendar.DECEMBER, 31));

        List<DBReader.MonthlyStatisticsItem> months = DBReader.getMonthlyTimeStatistics();

        assertEquals(2, months.size());
        assertEquals(2023, months.get(0).getYear());
        assertEquals(12, months.get(0).getMonth());
        assertEquals(5_000, months.get(0).getTimePlayed());
        assertEquals(2024, months.get(1).getYear());
        assertEquals(1, months.get(1).getMonth());
        assertEquals(80_000, months.get(1).getTimePlayed());
    }

    @Test
    public void timeBetweenReleaseAndPlaybackIsTheMedianDelay() throws Exception {
        FeedItem first = DBReader.getFeedItem(alpha.getItemAtIndex(0).getId());
        FeedItem second = DBReader.getFeedItem(alpha.getItemAtIndex(1).getId());
        FeedItem third = DBReader.getFeedItem(beta.getItemAtIndex(1).getId());
        setPlayed(first, 60_000, first.getPubDate().getTime() + HOUR_MILLIS);
        setPlayed(second, 60_000, second.getPubDate().getTime() + 3 * HOUR_MILLIS);
        setPlayed(third, 60_000, third.getPubDate().getTime() + 2 * HOUR_MILLIS);
        long earliestRelease = Math.min(first.getPubDate().getTime(),
                Math.min(second.getPubDate().getTime(), third.getPubDate().getTime()));

        assertEquals(2 * HOUR_MILLIS, DBReader.getTimeBetweenReleaseAndPlayback(earliestRelease, Long.MAX_VALUE));
    }

    private void setPlayed(FeedItem item, int playedMillis, long when) throws Exception {
        FeedMedia media = DBReader.getFeedMedia(item.getMedia().getId());
        media.setPlayedDuration(playedMillis);
        media.setLastPlayedTimeStatistics(when);
        media.setLastPlayedTimeHistory(new Date(when));
        DBWriter.setFeedMediaPlaybackInformation(media).get();
    }

    private StatisticsItem statisticsOf(DBReader.StatisticsResult result, String title) {
        for (StatisticsItem item : result.feedTime) {
            if (item.feed.getTitle().equals(title)) {
                return item;
            }
        }
        throw new AssertionError("No statistics for " + title);
    }
}
