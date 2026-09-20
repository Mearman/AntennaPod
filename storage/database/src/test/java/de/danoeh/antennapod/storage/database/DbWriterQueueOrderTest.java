package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.download.DownloadStatus;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DbWriterQueueOrderTest extends DatabaseTestBase {
    private Feed betaFeed;
    private Feed alphaFeed;
    private FeedItem banana;
    private FeedItem apple;
    private FeedItem cherry;

    @Before
    public void createQueue() {
        betaFeed = storeFeed("Beta feed");
        alphaFeed = storeFeed("alpha feed");
        banana = storeQueueCandidate(betaFeed, "Banana", 3000, 200, 20, 5000);
        apple = storeQueueCandidate(alphaFeed, "apple", 1000, 300, 10, 9000);
        cherry = storeQueueCandidate(betaFeed, "Cherry", 2000, 100, 30, 7000);
        await(DBWriter.addQueueItem(context, banana, apple, cherry));
    }

    private FeedItem storeQueueCandidate(Feed feed, String title, long pubDate, int duration, long size,
                                         long history) {
        FeedItem item = storeItem(feed, title, pubDate, FeedItem.UNPLAYED);
        item.getMedia().setDuration(duration);
        item.getMedia().setSize(size);
        item.getMedia().setLastPlayedTimeHistory(new Date(history));
        await(DBWriter.setFeedMedia(item.getMedia()));
        return item;
    }

    private List<Long> queueIds() {
        List<Long> ids = new ArrayList<>();
        for (FeedItem item : DBReader.getQueue()) {
            ids.add(item.getId());
        }
        return ids;
    }

    private static List<Long> ids(FeedItem... items) {
        List<Long> ids = new ArrayList<>();
        for (FeedItem item : items) {
            ids.add(item.getId());
        }
        return ids;
    }

    private void assertReorderedTo(SortOrder sortOrder, FeedItem... expected) {
        await(DBWriter.reorderQueue(sortOrder, false));
        assertEquals(ids(expected), queueIds());
    }

    @Test
    public void queueSortsByTitleIgnoringCase() {
        assertReorderedTo(SortOrder.EPISODE_TITLE_A_Z, apple, banana, cherry);
        assertReorderedTo(SortOrder.EPISODE_TITLE_Z_A, cherry, banana, apple);
    }

    @Test
    public void queueSortsByPublicationDate() {
        assertReorderedTo(SortOrder.DATE_OLD_NEW, apple, cherry, banana);
        assertReorderedTo(SortOrder.DATE_NEW_OLD, banana, cherry, apple);
    }

    @Test
    public void queueSortsByDuration() {
        assertReorderedTo(SortOrder.DURATION_SHORT_LONG, cherry, banana, apple);
        assertReorderedTo(SortOrder.DURATION_LONG_SHORT, apple, banana, cherry);
    }

    @Test
    public void queueSortsBySize() {
        assertReorderedTo(SortOrder.SIZE_SMALL_LARGE, apple, banana, cherry);
        assertReorderedTo(SortOrder.SIZE_LARGE_SMALL, cherry, banana, apple);
    }

    @Test
    public void queueSortsByEpisodeLinkIgnoringCase() {
        assertReorderedTo(SortOrder.EPISODE_FILENAME_A_Z, apple, banana, cherry);
        assertReorderedTo(SortOrder.EPISODE_FILENAME_Z_A, cherry, banana, apple);
    }

    @Test
    public void queueSortsByFeedTitleKeepingOrderWithinFeed() {
        assertReorderedTo(SortOrder.FEED_TITLE_A_Z, apple, banana, cherry);
        assertReorderedTo(SortOrder.FEED_TITLE_Z_A, banana, cherry, apple);
    }

    @Test
    public void queueSortsByCompletionDateWithMostRecentFirst() {
        assertReorderedTo(SortOrder.COMPLETION_DATE_NEW_OLD, apple, cherry, banana);
    }

    @Test
    public void queueSortWithGlobalDefaultUsesConfiguredOrder() {
        UserPreferences.setPrefGlobalSortedOrder(SortOrder.DURATION_SHORT_LONG);

        assertReorderedTo(SortOrder.GLOBAL_DEFAULT, cherry, banana, apple);
    }

    @Test
    public void randomSortKeepsAllQueueItems() {
        await(DBWriter.reorderQueue(SortOrder.RANDOM, false));

        assertEquals(new HashSet<>(ids(banana, apple, cherry)), new HashSet<>(queueIds()));
        assertEquals(3, queueIds().size());
    }

    private void fillQueueForSmartShuffle() {
        await(DBWriter.clearQueue());
        List<FeedItem> queue = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            queue.add(storeItem(alphaFeed, "alpha " + i, 100 + i, FeedItem.UNPLAYED));
            queue.add(storeItem(betaFeed, "beta " + i, 200 + i, FeedItem.UNPLAYED));
        }
        await(DBWriter.addQueueItem(context, queue.toArray(new FeedItem[0])));
    }

    private void assertSmartShuffleAlternatesFeeds(boolean ascending) {
        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(6, queue.size());
        List<Long> feedPattern = new ArrayList<>();
        for (FeedItem item : queue) {
            feedPattern.add(item.getFeedId());
        }
        long a = alphaFeed.getId();
        long b = betaFeed.getId();
        assertTrue(feedPattern.equals(Arrays.asList(b, a, a, b, a, b))
                || feedPattern.equals(Arrays.asList(a, b, b, a, b, a)));
        for (Feed feed : Arrays.asList(alphaFeed, betaFeed)) {
            List<Long> dates = new ArrayList<>();
            for (FeedItem item : queue) {
                if (item.getFeedId() == feed.getId()) {
                    dates.add(item.getPubDate().getTime());
                }
            }
            List<Long> sorted = new ArrayList<>(dates);
            Collections.sort(sorted);
            if (!ascending) {
                Collections.reverse(sorted);
            }
            assertEquals(sorted, dates);
        }
    }

    @Test
    public void smartShuffleSpreadsFeedsAndKeepsOldestEpisodesFirst() {
        fillQueueForSmartShuffle();

        await(DBWriter.reorderQueue(SortOrder.SMART_SHUFFLE_OLD_NEW, false));

        assertSmartShuffleAlternatesFeeds(true);
    }

    @Test
    public void smartShuffleSpreadsFeedsAndKeepsNewestEpisodesFirst() {
        fillQueueForSmartShuffle();

        await(DBWriter.reorderQueue(SortOrder.SMART_SHUFFLE_NEW_OLD, false));

        assertSmartShuffleAlternatesFeeds(false);
    }

    @Test
    public void enqueueAtFrontSkipsItemsThatAreDownloading() {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.FRONT);
        Map<String, DownloadStatus> running = new HashMap<>();
        running.put(banana.getMedia().getDownloadUrl(), new DownloadStatus(DownloadStatus.STATE_RUNNING, 10));
        downloadService.setCurrentDownloads(running);
        FeedItem added = storeItem(alphaFeed, "added");

        await(DBWriter.addQueueItem(context, added));

        assertEquals(ids(banana, added, apple, cherry), queueIds());
    }

    @Test
    public void enqueueAfterCurrentlyPlayingSkipsDownloadingItems() {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.AFTER_CURRENTLY_PLAYING);
        Map<String, DownloadStatus> running = new HashMap<>();
        running.put(apple.getMedia().getDownloadUrl(), new DownloadStatus(DownloadStatus.STATE_RUNNING, 10));
        downloadService.setCurrentDownloads(running);
        PlaybackPreferences.writeMediaPlaying(banana.getMedia());
        FeedItem added = storeItem(alphaFeed, "added");

        await(DBWriter.addQueueItem(context, added));

        assertEquals(ids(banana, apple, added, cherry), queueIds());
    }

    @Test
    public void enqueueAfterCurrentlyPlayingWithNothingPlayingInsertsAtFront() {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.AFTER_CURRENTLY_PLAYING);
        FeedItem added = storeItem(alphaFeed, "added");

        await(DBWriter.addQueueItem(context, added));

        assertEquals(ids(added, banana, apple, cherry), queueIds());
    }

    @Test
    public void enqueueAtRandomPositionKeepsExistingOrder() {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.RANDOM);
        FeedItem added = storeItem(alphaFeed, "added");

        await(DBWriter.addQueueItem(context, added));

        List<Long> ids = queueIds();
        assertEquals(4, ids.size());
        assertTrue(ids.remove(Long.valueOf(added.getId())));
        assertEquals(ids(banana, apple, cherry), ids);
    }
}
