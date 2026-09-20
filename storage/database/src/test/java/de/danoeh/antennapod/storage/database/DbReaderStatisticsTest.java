package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DbReaderStatisticsTest extends DatabaseTestBase {
    private static final long JANUARY_2020_NOON = 1_577_880_000_000L;
    private static final long FEBRUARY_2020_NOON = 1_580_558_400_000L;

    private FeedItem storePlayedEpisode(Feed feed, String title, long pubDate, int state, int duration,
                                        int playedDuration, long lastPlayed) {
        FeedItem item = storeItem(feed, title, pubDate, state);
        FeedMedia media = item.getMedia();
        media.setDuration(duration);
        media.setPlayedDuration(playedDuration);
        media.setLastPlayedTimeStatistics(lastPlayed);
        media.setLastPlayedTimeHistory(new Date(5));
        media.setSize(100);
        await(DBWriter.setFeedMedia(media));
        await(DBWriter.setFeedMediaPlaybackInformation(media));
        return item;
    }

    @Test
    public void statisticsSummariseEpisodesPerFeed() {
        long now = System.currentTimeMillis();
        Feed feed = storeFeed("Stats");
        FeedItem started = storePlayedEpisode(feed, "started", now - 1000, FeedItem.UNPLAYED, 60000, 30000,
                2_000_000);
        started.getMedia().setLocalFileUrl("/downloads/started.mp3");
        started.getMedia().setDownloaded(true, 3);
        await(DBWriter.setMediaDownloadInformation(started.getMedia()));
        storePlayedEpisode(feed, "marked played", now - 2000, FeedItem.PLAYED, 120000, 0, 0);
        storePlayedEpisode(feed, "untouched", now - 3000, FeedItem.UNPLAYED, 30000, 0, 0);

        DBReader.StatisticsResult result = DBReader.getStatistics(false, 0, Long.MAX_VALUE);

        assertEquals(1, result.feedTime.size());
        StatisticsItem item = result.feedTime.get(0);
        assertEquals(feed.getId(), item.feed.getId());
        assertEquals(210, item.time);
        assertEquals(30, item.timePlayed);
        assertEquals(3, item.episodes);
        assertEquals(1, item.episodesStarted);
        assertEquals(100, item.totalDownloadSize);
        assertEquals(1, item.episodesDownloadCount);
        assertTrue(item.hasRecentUnplayed);
        assertEquals(2_000_000, result.oldestDate);
    }

    @Test
    public void statisticsCanCountEpisodesMarkedAsPlayed() {
        long now = System.currentTimeMillis();
        Feed feed = storeFeed("Stats");
        storePlayedEpisode(feed, "started", now - 1000, FeedItem.UNPLAYED, 60000, 30000, 2_000_000);
        storePlayedEpisode(feed, "marked played", now - 2000, FeedItem.PLAYED, 120000, 0, 0);

        StatisticsItem without = DBReader.getStatistics(false, 0, Long.MAX_VALUE).feedTime.get(0);
        StatisticsItem with = DBReader.getStatistics(true, 0, Long.MAX_VALUE).feedTime.get(0);

        assertEquals(1, without.episodesStarted);
        assertEquals(30, without.timePlayed);
        assertEquals(2, with.episodesStarted);
        assertEquals(150, with.timePlayed);
    }

    @Test
    public void statisticsOnlyCountPlaybackInsideTimeFilter() {
        long now = System.currentTimeMillis();
        Feed feed = storeFeed("Stats");
        storePlayedEpisode(feed, "early", now, FeedItem.UNPLAYED, 60000, 10000, 1000);
        storePlayedEpisode(feed, "late", now, FeedItem.UNPLAYED, 60000, 20000, 5000);

        assertEquals(30, DBReader.getStatistics(false, 0, 10000).feedTime.get(0).timePlayed);
        assertEquals(20, DBReader.getStatistics(false, 2000, 10000).feedTime.get(0).timePlayed);
        assertEquals(10, DBReader.getStatistics(false, 0, 2000).feedTime.get(0).timePlayed);
        assertEquals(0, DBReader.getStatistics(false, 6000, 10000).feedTime.get(0).timePlayed);
    }

    @Test
    public void statisticsSkipFeedsThatAreNotSubscribed() {
        long now = System.currentTimeMillis();
        Feed subscribed = storeFeed("Subscribed");
        Feed browsed = storeFeed("Browsed");
        browsed.setState(Feed.STATE_NOT_SUBSCRIBED);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setFeedState(browsed.getId(), Feed.STATE_NOT_SUBSCRIBED);
        adapter.close();
        storePlayedEpisode(subscribed, "one", now, FeedItem.UNPLAYED, 1000, 0, 0);
        storePlayedEpisode(browsed, "two", now, FeedItem.UNPLAYED, 1000, 0, 0);

        List<StatisticsItem> feeds = DBReader.getStatistics(false, 0, Long.MAX_VALUE).feedTime;

        assertEquals(1, feeds.size());
        assertEquals(subscribed.getId(), feeds.get(0).feed.getId());
    }

    @Test
    public void statisticsOfEpisodesPlayedLongAgoAreNotRecentUnplayed() {
        Feed feed = storeFeed("Old");
        storePlayedEpisode(feed, "old unplayed", 1000, FeedItem.UNPLAYED, 1000, 0, 0);

        StatisticsItem item = DBReader.getStatistics(false, 0, Long.MAX_VALUE).feedTime.get(0);

        assertFalse(item.hasRecentUnplayed);
    }

    @Test
    public void statisticsWithoutAnyPlaybackKeepDefaultOldestDate() {
        long before = System.currentTimeMillis();
        Feed feed = storeFeed("Unplayed");
        storePlayedEpisode(feed, "unplayed", 1000, FeedItem.UNPLAYED, 1000, 0, 0);

        DBReader.StatisticsResult result = DBReader.getStatistics(false, 0, Long.MAX_VALUE);
        long after = System.currentTimeMillis();

        assertTrue(result.oldestDate >= before);
        assertTrue(result.oldestDate <= after);
    }

    @Test
    public void statisticsWithoutFeedsAreEmpty() {
        assertTrue(DBReader.getStatistics(false, 0, Long.MAX_VALUE).feedTime.isEmpty());
    }

    @Test
    public void monthlyStatisticsSumPlayedDurationPerMonth() {
        Feed feed = storeFeed("Monthly");
        storePlayedEpisode(feed, "jan one", 1000, FeedItem.UNPLAYED, 5000, 1000, JANUARY_2020_NOON);
        storePlayedEpisode(feed, "jan two", 2000, FeedItem.UNPLAYED, 5000, 500, JANUARY_2020_NOON + 1000);
        storePlayedEpisode(feed, "feb", 3000, FeedItem.UNPLAYED, 5000, 2000, FEBRUARY_2020_NOON);
        storePlayedEpisode(feed, "never played", 4000, FeedItem.UNPLAYED, 5000, 0, 0);

        List<DBReader.MonthlyStatisticsItem> months = DBReader.getMonthlyTimeStatistics();

        assertEquals(2, months.size());
        assertEquals(2020, months.get(0).getYear());
        assertEquals(1, months.get(0).getMonth());
        assertEquals(1500, months.get(0).getTimePlayed());
        assertEquals(2020, months.get(1).getYear());
        assertEquals(2, months.get(1).getMonth());
        assertEquals(2000, months.get(1).getTimePlayed());
    }

    @Test
    public void monthlyStatisticsAreEmptyWithoutPlayback() {
        assertTrue(DBReader.getMonthlyTimeStatistics().isEmpty());
    }

    @Test
    public void timeBetweenReleaseAndPlaybackIsTheMedianDelay() {
        Feed feed = storeFeed("Delay");
        storePlayedEpisode(feed, "fast", 10000, FeedItem.UNPLAYED, 1000, 1, 10100);
        storePlayedEpisode(feed, "medium", 20000, FeedItem.UNPLAYED, 1000, 1, 20200);
        storePlayedEpisode(feed, "slow", 30000, FeedItem.UNPLAYED, 1000, 1, 30900);

        assertEquals(200, DBReader.getTimeBetweenReleaseAndPlayback(0, Long.MAX_VALUE));
    }

    @Test
    public void timeBetweenReleaseAndPlaybackIgnoresPlaybackOutsideTimeFilter() {
        Feed feed = storeFeed("Delay");
        storePlayedEpisode(feed, "inside", 10000, FeedItem.UNPLAYED, 1000, 1, 10400);
        storePlayedEpisode(feed, "outside", 90000, FeedItem.UNPLAYED, 1000, 1, 99000);

        assertEquals(400, DBReader.getTimeBetweenReleaseAndPlayback(0, 50000));
    }
}
