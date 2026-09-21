package de.test.antennapod.storage.database;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.LongList;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.test.antennapod.service.download.DownloadTestFixture;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class QueueDatabaseTest {
    private static final int EPISODES = 5;
    private static final long TIMEOUT_SECONDS = 30;

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Context context;
    private Feed feed;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        feed = fixture.subscribe("Queue", EPISODES);
    }

    @After
    public void tearDown() throws Exception {
        PlaybackPreferences.writeNoMediaPlaying();
        fixture.tearDown();
    }

    private FeedItem item(int index) {
        return feed.getItemAtIndex(index);
    }

    private void enqueue(int... indices) throws Exception {
        for (int index : indices) {
            DBWriter.addQueueItem(context, item(index)).get();
        }
    }

    private List<String> queueTitles() {
        List<String> titles = new ArrayList<>();
        for (FeedItem queued : DBReader.getQueue()) {
            titles.add(queued.getTitle());
        }
        return titles;
    }

    private List<String> titles(int... indices) {
        List<String> titles = new ArrayList<>();
        for (int index : indices) {
            titles.add(item(index).getTitle());
        }
        return titles;
    }

    @Test
    public void itemsAreAppendedInTheOrderTheyWereAdded() throws Exception {
        enqueue(2, 0, 3);

        assertEquals(titles(2, 0, 3), queueTitles());
        LongList ids = DBReader.getQueueIDList();
        assertEquals(3, ids.size());
        assertEquals(item(2).getId(), ids.get(0));
        assertEquals(item(3).getId(), ids.get(2));
        assertTrue(DBReader.getFeedItem(item(2).getId()).isTagged(FeedItem.TAG_QUEUE));
        assertFalse(DBReader.getFeedItem(item(1).getId()).isTagged(FeedItem.TAG_QUEUE));
    }

    @Test
    public void addingSeveralItemsAtOnceKeepsTheirOrder() throws Exception {
        DBWriter.addQueueItem(context, item(1), item(3), item(0)).get();

        assertEquals(titles(1, 3, 0), queueTitles());
    }

    @Test
    public void itemThatIsAlreadyQueuedKeepsItsPosition() throws Exception {
        enqueue(0, 1);

        DBWriter.addQueueItem(context, item(0), item(2)).get();

        assertEquals(titles(0, 1, 2), queueTitles());
    }

    @Test
    public void addingNothingLeavesTheQueueUntouched() throws Exception {
        DBWriter.addQueueItem(context).get();

        assertTrue(DBReader.getQueue().isEmpty());
    }

    @Test
    public void enqueueingAtTheFrontPutsNewItemsBeforeExistingOnes() throws Exception {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.FRONT);

        enqueue(0, 1);

        assertEquals(titles(1, 0), queueTitles());
        DBWriter.addQueueItem(context, item(2)).get();
        assertEquals(titles(2, 1, 0), queueTitles());
    }

    @Test
    public void enqueueingAfterTheCurrentEpisodePutsNewItemsBehindIt() throws Exception {
        enqueue(0, 1, 2);
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.AFTER_CURRENTLY_PLAYING);
        PlaybackPreferences.writeMediaPlaying(DBReader.getFeedMedia(item(1).getMedia().getId()));

        DBWriter.addQueueItem(context, item(3)).get();

        assertEquals(titles(0, 1, 3, 2), queueTitles());
    }

    @Test
    public void enqueueingAfterTheCurrentEpisodeWithoutOnePutsNewItemsAtTheFront() throws Exception {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.AFTER_CURRENTLY_PLAYING);
        PlaybackPreferences.writeNoMediaPlaying();
        enqueue(0, 1);

        assertEquals(titles(1, 0), queueTitles());
    }

    @Test
    public void randomEnqueueLocationNeverReordersTheItemsThatAreAlreadyQueued() throws Exception {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.RANDOM);

        for (int index = 0; index < EPISODES; index++) {
            List<String> before = queueTitles();

            enqueue(index);

            List<String> after = queueTitles();
            assertEquals(before.size() + 1, after.size());
            assertTrue(after.contains(item(index).getTitle()));
            after.remove(item(index).getTitle());
            assertEquals(before, after);
        }
    }

    @Test
    public void itemsAreInsertedAtAnExplicitIndex() throws Exception {
        enqueue(0, 1);

        DBWriter.addQueueItemAt(context, item(4).getId(), 1).get();
        DBWriter.addQueueItemAt(context, item(4).getId(), 0).get();

        assertEquals(titles(0, 4, 1), queueTitles());
    }

    @Test
    public void itemsThatAreNewLeaveTheInboxWhenTheyAreQueued() throws Exception {
        Feed inbox = fixture.subscribe("Inbox", 2, FeedItem.NEW);

        DBWriter.addQueueItem(context, inbox.getItemAtIndex(0)).get();
        DBWriter.addQueueItemAt(context, inbox.getItemAtIndex(1).getId(), 1).get();

        assertFalse(DBReader.getFeedItem(inbox.getItemAtIndex(0).getId()).isNew());
        assertFalse(DBReader.getFeedItem(inbox.getItemAtIndex(1).getId()).isNew());
    }

    @Test
    public void removingAnItemClosesTheGap() throws Exception {
        enqueue(0, 1, 2);

        DBWriter.removeQueueItem(context, false, item(1)).get();

        assertEquals(titles(0, 2), queueTitles());
        assertFalse(DBReader.getFeedItem(item(1).getId()).isTagged(FeedItem.TAG_QUEUE));
    }

    @Test
    public void removingSeveralItemsAtOnce() throws Exception {
        enqueue(0, 1, 2, 3);

        DBWriter.removeQueueItem(context, true, item(0).getId(), item(2).getId()).get();

        assertEquals(titles(1, 3), queueTitles());
    }

    @Test
    public void removingAnItemThatIsNotQueuedChangesNothing() throws Exception {
        enqueue(0, 1);

        DBWriter.removeQueueItem(context, false, item(4)).get();

        assertEquals(titles(0, 1), queueTitles());
    }

    @Test
    public void removingAFavouriteFromTheQueueKeepsItAsFavourite() throws Exception {
        enqueue(0, 1);
        DBWriter.addFavoriteItems(Collections.singletonList(item(0))).get();

        DBWriter.removeQueueItem(context, false, DBReader.getFeedItem(item(0).getId())).get();

        assertEquals(titles(1), queueTitles());
        assertTrue(DBReader.getFeedItem(item(0).getId()).isTagged(FeedItem.TAG_FAVORITE));
    }

    @Test
    public void clearQueueRemovesEverything() throws Exception {
        enqueue(0, 1, 2);

        DBWriter.clearQueue().get();

        assertTrue(DBReader.getQueue().isEmpty());
        assertEquals(0, DBReader.getQueueIDList().size());
        assertFalse(DBReader.getFeedItem(item(0).getId()).isTagged(FeedItem.TAG_QUEUE));
    }

    @Test
    public void itemsCanBeMovedWithinTheQueue() throws Exception {
        enqueue(0, 1, 2, 3);

        DBWriter.moveQueueItem(0, 2, true).get();
        assertEquals(titles(1, 2, 0, 3), queueTitles());

        DBWriter.moveQueueItem(3, 1, false).get();
        assertEquals(titles(1, 3, 2, 0), queueTitles());
    }

    @Test
    public void movingAnItemOutsideTheQueueChangesNothing() throws Exception {
        enqueue(0, 1);

        DBWriter.moveQueueItem(0, 7, true).get();

        assertEquals(titles(0, 1), queueTitles());
    }

    @Test
    public void itemsCanBeMovedToTheTopAndTheBottom() throws Exception {
        enqueue(0, 1, 2, 3, 4);

        DBWriter.moveQueueItemsToTop(Arrays.asList(item(3), item(4))).get();
        assertEquals(titles(3, 4, 0, 1, 2), queueTitles());

        DBWriter.moveQueueItemsToBottom(Arrays.asList(item(3), item(0))).get();
        assertEquals(titles(4, 1, 2, 3, 0), queueTitles());
    }

    @Test
    public void movingNoItemsChangesNothing() throws Exception {
        enqueue(0, 1);

        DBWriter.moveQueueItemsToTop(Collections.emptyList()).get();
        DBWriter.moveQueueItemsToBottom(Collections.emptyList()).get();

        assertEquals(titles(0, 1), queueTitles());
    }

    @Test
    public void queueCanBeSortedByTitleAndDate() throws Exception {
        enqueue(2, 4, 0, 3, 1);

        DBWriter.reorderQueue(SortOrder.EPISODE_TITLE_A_Z, true).get();
        assertEquals(titles(0, 1, 2, 3, 4), queueTitles());

        DBWriter.reorderQueue(SortOrder.DATE_OLD_NEW, true).get();
        assertEquals(titles(4, 3, 2, 1, 0), queueTitles());

        DBWriter.reorderQueue(SortOrder.DATE_NEW_OLD, true).get();
        assertEquals(titles(0, 1, 2, 3, 4), queueTitles());

        DBWriter.reorderQueue(SortOrder.EPISODE_TITLE_Z_A, false).get();
        assertEquals(titles(4, 3, 2, 1, 0), queueTitles());
    }

    @Test
    public void queueCanBeSortedByDurationAndSize() throws Exception {
        fixture.setDurationAndSize(item(0), 3000, 300);
        fixture.setDurationAndSize(item(1), 1000, 500);
        fixture.setDurationAndSize(item(2), 2000, 100);
        enqueue(0, 1, 2);

        DBWriter.reorderQueue(SortOrder.DURATION_SHORT_LONG, true).get();
        assertEquals(titles(1, 2, 0), queueTitles());

        DBWriter.reorderQueue(SortOrder.DURATION_LONG_SHORT, true).get();
        assertEquals(titles(0, 2, 1), queueTitles());

        DBWriter.reorderQueue(SortOrder.SIZE_SMALL_LARGE, true).get();
        assertEquals(titles(2, 0, 1), queueTitles());

        DBWriter.reorderQueue(SortOrder.SIZE_LARGE_SMALL, true).get();
        assertEquals(titles(1, 0, 2), queueTitles());
    }

    @Test
    public void queueCanBeSortedByLinkAndFeedTitle() throws Exception {
        Feed other = fixture.subscribe("Another", 1);
        List<String> otherTitle = Collections.singletonList(other.getItemAtIndex(0).getTitle());
        enqueue(3, 1);
        DBWriter.addQueueItem(context, other.getItemAtIndex(0)).get();

        DBWriter.reorderQueue(SortOrder.EPISODE_FILENAME_A_Z, true).get();
        assertEquals(concat(otherTitle, titles(1, 3)), queueTitles());

        DBWriter.reorderQueue(SortOrder.EPISODE_FILENAME_Z_A, true).get();
        assertEquals(concat(titles(3, 1), otherTitle), queueTitles());

        DBWriter.reorderQueue(SortOrder.FEED_TITLE_A_Z, true).get();
        assertEquals(concat(otherTitle, titles(3, 1)), queueTitles());

        DBWriter.reorderQueue(SortOrder.FEED_TITLE_Z_A, true).get();
        assertEquals(concat(titles(3, 1), otherTitle), queueTitles());
    }

    private List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    @Test
    public void shufflingReordersTheQueueAndKeepsEveryItem() throws Exception {
        enqueue(0, 1, 2, 3, 4);
        List<String> original = queueTitles();

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> {
            DBWriter.reorderQueue(SortOrder.RANDOM, true).get();
            return !queueTitles().equals(original);
        });

        List<String> shuffled = queueTitles();
        assertEquals(EPISODES, shuffled.size());
        assertTrue(shuffled.containsAll(original));
    }

    @Test
    public void smartShuffleSpreadsEpisodesOfTheSameFeedAndKeepsEachFeedInOrder() throws Exception {
        Feed other = fixture.subscribe("Other", 2);
        enqueue(0, 1, 2, 3);
        DBWriter.addQueueItem(context, other.getItemAtIndex(0), other.getItemAtIndex(1)).get();
        String otherNewest = other.getItemAtIndex(0).getTitle();
        String otherOldest = other.getItemAtIndex(1).getTitle();

        DBWriter.reorderQueue(SortOrder.SMART_SHUFFLE_OLD_NEW, true).get();
        assertEquals(concat(concat(Collections.singletonList(otherOldest), titles(3, 2, 1, 0)),
                Collections.singletonList(otherNewest)), queueTitles());

        DBWriter.reorderQueue(SortOrder.SMART_SHUFFLE_NEW_OLD, true).get();
        assertEquals(concat(concat(Collections.singletonList(otherNewest), titles(0, 1, 2, 3)),
                Collections.singletonList(otherOldest)), queueTitles());
    }

    @Test
    public void queueCanBeSortedByCompletionDate() throws Exception {
        long now = System.currentTimeMillis();
        fixture.markPlayed(item(0), now - 3000);
        fixture.markPlayed(item(1), now - 1000);
        fixture.markPlayed(item(2), now - 2000);
        enqueue(0, 1, 2);

        DBWriter.reorderQueue(SortOrder.COMPLETION_DATE_NEW_OLD, true).get();

        assertEquals(titles(1, 2, 0), queueTitles());
    }

    @Test
    public void sortingWithoutASortOrderChangesNothing() throws Exception {
        enqueue(2, 0);

        DBWriter.reorderQueue(null, true).get();

        assertEquals(titles(2, 0), queueTitles());
    }

    @Test
    public void globalDefaultSortOrderUsesTheGlobalSetting() throws Exception {
        UserPreferences.setPrefGlobalSortedOrder(SortOrder.EPISODE_TITLE_Z_A);
        enqueue(0, 1, 2);

        DBWriter.reorderQueue(SortOrder.GLOBAL_DEFAULT, true).get();

        assertEquals(titles(2, 1, 0), queueTitles());
    }

    @Test
    public void queueThatKeepsItselfSortedInsertsItemsInOrder() throws Exception {
        UserPreferences.setQueueKeepSortedOrder(SortOrder.EPISODE_TITLE_A_Z);
        UserPreferences.setQueueKeepSorted(true);

        enqueue(3, 1, 4, 0);

        assertEquals(titles(0, 1, 3, 4), queueTitles());
    }

    @Test
    public void queueThatKeepsItselfShuffledIsNotReshuffledOnEveryChange() throws Exception {
        UserPreferences.setQueueKeepSortedOrder(SortOrder.RANDOM);
        UserPreferences.setQueueKeepSorted(true);

        enqueue(2, 0, 1);

        assertEquals(titles(2, 0, 1), queueTitles());
    }

    @Test
    public void deletingAnEpisodeRemovesItFromTheQueueWhenTheSettingIsOn() throws Exception {
        DownloadTestFixture.putBoolean(UserPreferences.PREF_DELETE_REMOVES_FROM_QUEUE, true);
        enqueue(0, 1);
        fixture.markDownloaded(item(0));

        DBWriter.deleteFeedMediaOfItem(context, DBReader.getFeedMedia(item(0).getMedia().getId())).get();

        assertEquals(titles(1), queueTitles());
        assertFalse(DBReader.getFeedItem(item(0).getId()).isDownloaded());
    }

    @Test
    public void nextEpisodeInTheQueueFollowsTheGivenOne() throws Exception {
        enqueue(0, 1, 2);

        assertEquals(item(1).getId(), DBReader.getNextInQueue(item(0)).getId());
        assertEquals(item(2).getId(), DBReader.getNextInQueue(item(1)).getId());
        assertNull(DBReader.getNextInQueue(item(2)));
    }

    @Test
    public void remainingQueueSizeCountsItemsBehindTheGivenOne() throws Exception {
        enqueue(0, 1, 2, 3);

        assertEquals(4, DBReader.getRemainingQueueSize(item(0).getId()));
        assertEquals(2, DBReader.getRemainingQueueSize(item(2).getId()));
        assertEquals(1, DBReader.getRemainingQueueSize(item(3).getId()));
        assertEquals(0, DBReader.getRemainingQueueSize(item(4).getId()));
    }

    @Test
    public void pausedQueueListsStartedEpisodesFirst() throws Exception {
        enqueue(0, 1, 2);
        FeedMedia started = DBReader.getFeedMedia(item(1).getMedia().getId());
        started.setPosition(120_000);
        started.setLastPlayedTimeStatistics(System.currentTimeMillis());
        started.setLastPlayedTimeHistory(new Date());
        DBWriter.setFeedMediaPlaybackInformation(started).get();

        List<FeedItem> paused = DBReader.getPausedQueue(10);

        assertEquals(3, paused.size());
        assertEquals(item(1).getId(), paused.get(0).getId());
        assertEquals(120_000, paused.get(0).getMedia().getPosition());
        assertEquals(2, DBReader.getPausedQueue(2).size());
    }

    @Test
    public void queueSizeIsReportedAfterEveryChange() throws Exception {
        enqueue(0, 1, 2);
        assertEquals(3, DBReader.getQueue().size());

        DBWriter.removeQueueItem(context, false, item(0)).get();

        assertEquals(2, DBReader.getQueueIDList().size());
    }
}
