package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DbWriterQueueTest extends DatabaseTestBase {

    private static List<Long> ids(List<FeedItem> items) {
        List<Long> result = new ArrayList<>();
        for (FeedItem item : items) {
            result.add(item.getId());
        }
        return result;
    }

    private static List<String> titles(List<FeedItem> items) {
        List<String> result = new ArrayList<>();
        for (FeedItem item : items) {
            result.add(item.getTitle());
        }
        return result;
    }

    @Test
    public void addQueueItemAppendsItemsInGivenOrder() {
        Feed feed = storeFeed("feed");
        FeedItem first = storeItem(feed, "first");
        FeedItem second = storeItem(feed, "second");

        await(DBWriter.addQueueItem(context, first, second));

        assertEquals(Arrays.asList("first", "second"), titles(DBReader.getQueue()));
        List<QueueEvent> queueEvents = events.eventsOfType(QueueEvent.class);
        assertEquals(2, queueEvents.size());
        assertEquals(QueueEvent.Action.ADDED, queueEvents.get(0).action);
        assertEquals(0, queueEvents.get(0).position);
        assertEquals(1, queueEvents.get(1).position);
        assertTrue(first.isTagged(FeedItem.TAG_QUEUE));
    }

    @Test
    public void addQueueItemRequestsAutodownload() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");

        await(DBWriter.addQueueItem(context, item));

        assertEquals(1, autoDownloadManager.getAutodownloadRequests());
    }

    @Test
    public void addQueueItemWithoutItemsDoesNothing() {
        await(DBWriter.addQueueItem(context));

        assertTrue(DBReader.getQueue().isEmpty());
        assertTrue(events.eventsOfType(QueueEvent.class).isEmpty());
        assertEquals(0, autoDownloadManager.getAutodownloadRequests());
    }

    @Test
    public void addQueueItemIgnoresItemsAlreadyQueuedAndItemsWithoutMedia() {
        Feed feed = storeFeed("feed");
        FeedItem queued = storeItem(feed, "queued");
        FeedItem noMedia = storeItemWithoutMedia(feed, "no media", 5);
        FeedItem fresh = storeItem(feed, "fresh");
        await(DBWriter.addQueueItem(context, queued));
        events.clear();

        await(DBWriter.addQueueItem(context, queued, noMedia, fresh));

        assertEquals(Arrays.asList("queued", "fresh"), titles(DBReader.getQueue()));
        List<QueueEvent> queueEvents = events.eventsOfType(QueueEvent.class);
        assertEquals(1, queueEvents.size());
        assertEquals(fresh.getId(), queueEvents.get(0).item.getId());
        assertEquals(1, queueEvents.get(0).position);
    }

    @Test
    public void addQueueItemOnlyReportsQueuedItemsWhenNothingWasAdded() {
        Feed feed = storeFeed("feed");
        FeedItem noMedia = storeItemWithoutMedia(feed, "no media", 5);

        await(DBWriter.addQueueItem(context, noMedia));

        assertTrue(DBReader.getQueue().isEmpty());
        assertTrue(events.eventsOfType(QueueEvent.class).isEmpty());
        assertTrue(events.eventsOfType(FeedItemEvent.class).isEmpty());
    }

    @Test
    public void addQueueItemMarksNewItemsAsUnplayed() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item", 1000, FeedItem.NEW);

        await(DBWriter.addQueueItem(context, item));

        assertEquals(FeedItem.UNPLAYED, DBReader.getFeedItem(item.getId()).getPlayState());
    }

    @Test
    public void addQueueItemInsertsAtFrontWhenConfigured() {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.FRONT);
        Feed feed = storeFeed("feed");
        FeedItem old = storeItem(feed, "old");
        FeedItem added = storeItem(feed, "added");
        await(DBWriter.addQueueItem(context, old));

        await(DBWriter.addQueueItem(context, added));

        assertEquals(Arrays.asList("added", "old"), titles(DBReader.getQueue()));
    }

    @Test
    public void addQueueItemInsertsAfterCurrentlyPlayingItem() {
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.AFTER_CURRENTLY_PLAYING);
        Feed feed = storeFeed("feed");
        FeedItem playing = storeItem(feed, "playing");
        FeedItem next = storeItem(feed, "next");
        FeedItem added = storeItem(feed, "added");
        await(DBWriter.addQueueItem(context, playing, next));
        PlaybackPreferences.writeMediaPlaying(playing.getMedia());

        await(DBWriter.addQueueItem(context, added));

        assertEquals(Arrays.asList("playing", "added", "next"), titles(DBReader.getQueue()));
    }

    @Test
    public void addQueueItemKeepsQueueSortedWhenConfigured() {
        UserPreferences.setQueueKeepSorted(true);
        UserPreferences.setQueueKeepSortedOrder(SortOrder.EPISODE_TITLE_A_Z);
        Feed feed = storeFeed("feed");
        FeedItem banana = storeItem(feed, "banana");
        FeedItem apple = storeItem(feed, "apple");
        FeedItem cherry = storeItem(feed, "cherry");
        await(DBWriter.addQueueItem(context, banana, cherry));
        events.clear();

        await(DBWriter.addQueueItem(context, apple));

        assertEquals(Arrays.asList("apple", "banana", "cherry"), titles(DBReader.getQueue()));
        List<QueueEvent> queueEvents = events.eventsOfType(QueueEvent.class);
        assertEquals(1, queueEvents.size());
        assertEquals(QueueEvent.Action.SORTED, queueEvents.get(0).action);
    }

    @Test
    public void addQueueItemDoesNotShuffleRandomKeepSortedQueue() {
        UserPreferences.setQueueKeepSorted(true);
        UserPreferences.setQueueKeepSortedOrder(SortOrder.RANDOM);
        Feed feed = storeFeed("feed");
        FeedItem first = storeItem(feed, "zebra");
        FeedItem second = storeItem(feed, "ant");

        await(DBWriter.addQueueItem(context, first, second));

        assertEquals(Arrays.asList("zebra", "ant"), titles(DBReader.getQueue()));
        assertEquals(QueueEvent.Action.ADDED, events.eventsOfType(QueueEvent.class).get(0).action);
    }

    @Test
    public void addQueueItemAtInsertsAtIndex() {
        Feed feed = storeFeed("feed");
        FeedItem first = storeItem(feed, "first");
        FeedItem last = storeItem(feed, "last");
        FeedItem middle = storeItem(feed, "middle");
        await(DBWriter.addQueueItem(context, first, last));
        events.clear();

        await(DBWriter.addQueueItemAt(context, middle.getId(), 1));

        assertEquals(Arrays.asList("first", "middle", "last"), titles(DBReader.getQueue()));
        QueueEvent event = events.eventsOfType(QueueEvent.class).get(0);
        assertEquals(QueueEvent.Action.ADDED, event.action);
        assertEquals(1, event.position);
    }

    @Test
    public void addQueueItemAtKeepsPositionOfItemAlreadyQueued() {
        Feed feed = storeFeed("feed");
        FeedItem first = storeItem(feed, "first");
        FeedItem second = storeItem(feed, "second");
        await(DBWriter.addQueueItem(context, first, second));
        events.clear();

        await(DBWriter.addQueueItemAt(context, second.getId(), 0));

        assertEquals(Arrays.asList("first", "second"), titles(DBReader.getQueue()));
        assertTrue(events.eventsOfType(QueueEvent.class).isEmpty());
    }

    @Test
    public void addQueueItemAtIgnoresUnknownItem() {
        await(DBWriter.addQueueItemAt(context, 4711, 0));

        assertTrue(DBReader.getQueue().isEmpty());
        assertTrue(events.eventsOfType(QueueEvent.class).isEmpty());
    }

    @Test
    public void addQueueItemAtMarksNewItemAsUnplayed() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item", 1000, FeedItem.NEW);

        await(DBWriter.addQueueItemAt(context, item.getId(), 0));

        assertEquals(FeedItem.UNPLAYED, DBReader.getFeedItem(item.getId()).getPlayState());
    }

    @Test
    public void removeQueueItemRemovesOnlyTheGivenItem() {
        Feed feed = storeFeed("feed");
        FeedItem first = storeItem(feed, "first");
        FeedItem second = storeItem(feed, "second");
        FeedItem third = storeItem(feed, "third");
        await(DBWriter.addQueueItem(context, first, second, third));
        events.clear();

        await(DBWriter.removeQueueItem(context, false, second));

        assertEquals(Arrays.asList("first", "third"), titles(DBReader.getQueue()));
        QueueEvent event = events.eventsOfType(QueueEvent.class).get(0);
        assertEquals(QueueEvent.Action.REMOVED, event.action);
        assertEquals(second.getId(), event.item.getId());
        assertEquals(second.getId(), events.eventsOfType(FeedItemEvent.class).get(0).items.get(0).getId());
    }

    @Test
    public void removeQueueItemWithIdsRemovesAllGivenItems() {
        Feed feed = storeFeed("feed");
        FeedItem first = storeItem(feed, "first");
        FeedItem second = storeItem(feed, "second");
        FeedItem third = storeItem(feed, "third");
        await(DBWriter.addQueueItem(context, first, second, third));

        await(DBWriter.removeQueueItem(context, false, first.getId(), third.getId()));

        assertEquals(Collections.singletonList("second"), titles(DBReader.getQueue()));
    }

    @Test
    public void removeQueueItemRequestsAutodownloadOnlyWhenAsked() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        await(DBWriter.addQueueItem(context, item));
        int requestsBefore = autoDownloadManager.getAutodownloadRequests();

        await(DBWriter.removeQueueItem(context, false, item));
        assertEquals(requestsBefore, autoDownloadManager.getAutodownloadRequests());

        await(DBWriter.removeQueueItem(context, true, item));
        assertEquals(requestsBefore + 1, autoDownloadManager.getAutodownloadRequests());
    }

    @Test
    public void removeQueueItemIgnoresItemsThatAreNotQueued() {
        Feed feed = storeFeed("feed");
        FeedItem queued = storeItem(feed, "queued");
        FeedItem other = storeItem(feed, "other");
        await(DBWriter.addQueueItem(context, queued));
        events.clear();

        await(DBWriter.removeQueueItem(context, false, other));

        assertEquals(Collections.singletonList("queued"), titles(DBReader.getQueue()));
        assertTrue(events.eventsOfType(QueueEvent.class).isEmpty());
    }

    @Test
    public void removeQueueItemWithoutIdsDoesNothing() {
        Feed feed = storeFeed("feed");
        FeedItem queued = storeItem(feed, "queued");
        await(DBWriter.addQueueItem(context, queued));

        await(DBWriter.removeQueueItem(context, true, new long[0]));

        assertEquals(1, DBReader.getQueue().size());
    }

    @Test
    public void clearQueueEmptiesQueueAndPostsClearedEvent() {
        Feed feed = storeFeed("feed");
        await(DBWriter.addQueueItem(context, storeItem(feed, "one"), storeItem(feed, "two")));
        events.clear();

        await(DBWriter.clearQueue());

        assertTrue(DBReader.getQueue().isEmpty());
        assertEquals(QueueEvent.Action.CLEARED, events.eventsOfType(QueueEvent.class).get(0).action);
    }

    @Test
    public void moveQueueItemChangesPosition() {
        Feed feed = storeFeed("feed");
        await(DBWriter.addQueueItem(context, storeItem(feed, "a"), storeItem(feed, "b"), storeItem(feed, "c")));
        events.clear();

        await(DBWriter.moveQueueItem(0, 2, true));

        assertEquals(Arrays.asList("b", "c", "a"), titles(DBReader.getQueue()));
        QueueEvent event = events.eventsOfType(QueueEvent.class).get(0);
        assertEquals(QueueEvent.Action.MOVED, event.action);
        assertEquals(2, event.position);
    }

    @Test
    public void moveQueueItemWithoutBroadcastPostsNoEvent() {
        Feed feed = storeFeed("feed");
        await(DBWriter.addQueueItem(context, storeItem(feed, "a"), storeItem(feed, "b")));
        events.clear();

        await(DBWriter.moveQueueItem(1, 0, false));

        assertEquals(Arrays.asList("b", "a"), titles(DBReader.getQueue()));
        assertTrue(events.eventsOfType(QueueEvent.class).isEmpty());
    }

    @Test
    public void moveQueueItemOutsideQueueBoundsLeavesQueueUntouched() {
        Feed feed = storeFeed("feed");
        await(DBWriter.addQueueItem(context, storeItem(feed, "a"), storeItem(feed, "b")));
        events.clear();

        await(DBWriter.moveQueueItem(0, 2, true));
        await(DBWriter.moveQueueItem(-1, 1, true));
        await(DBWriter.moveQueueItem(2, 0, true));
        await(DBWriter.moveQueueItem(0, -1, true));

        assertEquals(Arrays.asList("a", "b"), titles(DBReader.getQueue()));
        assertTrue(events.eventsOfType(QueueEvent.class).isEmpty());
    }

    @Test
    public void moveQueueItemsToTopKeepsSelectionOrder() {
        Feed feed = storeFeed("feed");
        FeedItem a = storeItem(feed, "a");
        FeedItem b = storeItem(feed, "b");
        FeedItem c = storeItem(feed, "c");
        FeedItem d = storeItem(feed, "d");
        await(DBWriter.addQueueItem(context, a, b, c, d));

        await(DBWriter.moveQueueItemsToTop(Arrays.asList(c, d)));

        assertEquals(Arrays.asList("c", "d", "a", "b"), titles(DBReader.getQueue()));
    }

    @Test
    public void moveQueueItemsToBottomKeepsSelectionOrder() {
        Feed feed = storeFeed("feed");
        FeedItem a = storeItem(feed, "a");
        FeedItem b = storeItem(feed, "b");
        FeedItem c = storeItem(feed, "c");
        FeedItem d = storeItem(feed, "d");
        await(DBWriter.addQueueItem(context, a, b, c, d));

        await(DBWriter.moveQueueItemsToBottom(Arrays.asList(a, b)));

        assertEquals(Arrays.asList("c", "d", "a", "b"), titles(DBReader.getQueue()));
    }

    @Test
    public void moveQueueItemsWithEmptySelectionDoesNothing() {
        Feed feed = storeFeed("feed");
        await(DBWriter.addQueueItem(context, storeItem(feed, "a"), storeItem(feed, "b")));
        events.clear();

        await(DBWriter.moveQueueItemsToTop(Collections.emptyList()));
        await(DBWriter.moveQueueItemsToBottom(Collections.emptyList()));

        assertEquals(Arrays.asList("a", "b"), titles(DBReader.getQueue()));
        assertTrue(events.eventsOfType(QueueEvent.class).isEmpty());
    }

    @Test
    public void reorderQueueSortsByGivenOrderAndBroadcasts() {
        Feed feed = storeFeed("feed");
        await(DBWriter.addQueueItem(context, storeItem(feed, "banana"), storeItem(feed, "cherry"),
                storeItem(feed, "apple")));
        events.clear();

        await(DBWriter.reorderQueue(SortOrder.EPISODE_TITLE_A_Z, true));

        assertEquals(Arrays.asList("apple", "banana", "cherry"), titles(DBReader.getQueue()));
        assertEquals(QueueEvent.Action.SORTED, events.eventsOfType(QueueEvent.class).get(0).action);
    }

    @Test
    public void reorderQueueWithoutBroadcastPostsNoEvent() {
        Feed feed = storeFeed("feed");
        await(DBWriter.addQueueItem(context, storeItem(feed, "banana"), storeItem(feed, "apple")));
        events.clear();

        await(DBWriter.reorderQueue(SortOrder.EPISODE_TITLE_Z_A, false));

        assertEquals(Arrays.asList("banana", "apple"), titles(DBReader.getQueue()));
        assertTrue(events.eventsOfType(QueueEvent.class).isEmpty());
    }

    @Test
    public void reorderQueueWithoutSortOrderLeavesQueueUntouched() {
        Feed feed = storeFeed("feed");
        await(DBWriter.addQueueItem(context, storeItem(feed, "banana"), storeItem(feed, "apple")));

        await(DBWriter.reorderQueue(null, true));

        assertEquals(Arrays.asList("banana", "apple"), titles(DBReader.getQueue()));
    }

    @Test
    public void queueIdListFollowsQueueOrder() {
        Feed feed = storeFeed("feed");
        FeedItem a = storeItem(feed, "a");
        FeedItem b = storeItem(feed, "b");
        await(DBWriter.addQueueItem(context, b, a));

        LongList queueIds = DBReader.getQueueIDList();

        assertEquals(2, queueIds.size());
        assertEquals(b.getId(), queueIds.get(0));
        assertEquals(a.getId(), queueIds.get(1));
    }

    @Test
    public void remainingQueueSizeCountsItemFromItsPosition() {
        Feed feed = storeFeed("feed");
        FeedItem a = storeItem(feed, "a");
        FeedItem b = storeItem(feed, "b");
        FeedItem c = storeItem(feed, "c");
        FeedItem outside = storeItem(feed, "outside");
        await(DBWriter.addQueueItem(context, a, b, c));

        assertEquals(3, DBReader.getRemainingQueueSize(a.getId()));
        assertEquals(1, DBReader.getRemainingQueueSize(c.getId()));
        assertEquals(0, DBReader.getRemainingQueueSize(outside.getId()));
    }

    @Test
    public void nextInQueueReturnsFollowingItemOrNullAtEnd() {
        Feed feed = storeFeed("feed");
        FeedItem a = storeItem(feed, "a");
        FeedItem b = storeItem(feed, "b");
        await(DBWriter.addQueueItem(context, a, b));

        assertEquals(b.getId(), DBReader.getNextInQueue(a).getId());
        assertNull(DBReader.getNextInQueue(b));
    }

    @Test
    public void pausedQueueListsItemsWithProgressFirstAndHonoursLimit() {
        Feed feed = storeFeed("feed");
        FeedItem untouched = storeItem(feed, "untouched");
        FeedItem started = storeItem(feed, "started");
        started.getMedia().setPosition(5000);
        started.getMedia().setLastPlayedTimeStatistics(System.currentTimeMillis());
        await(DBWriter.setFeedMedia(started.getMedia()));
        await(DBWriter.addQueueItem(context, untouched, started));

        List<FeedItem> paused = DBReader.getPausedQueue(10);

        assertEquals(Arrays.asList("started", "untouched"), titles(paused));
        assertEquals(1, DBReader.getPausedQueue(1).size());
        assertEquals(started.getId(), ids(DBReader.getPausedQueue(1)).get(0).longValue());
    }
}
