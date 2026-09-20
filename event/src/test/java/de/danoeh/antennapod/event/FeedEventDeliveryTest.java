package de.danoeh.antennapod.event;

import android.content.Context;
import de.danoeh.antennapod.model.download.DownloadStatus;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class FeedEventDeliveryTest {
    private Context context;
    private final EventRecorder recorder = new EventRecorder();
    private Feed feed;
    private List<FeedItem> items;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        EventTestDatabase.setUp(context);
        feed = EventTestDatabase.storeFeed(context, "http://example.com/feed", "Feed", 3);
        items = EventTestDatabase.itemsOf(feed);
        recorder.register();
    }

    @After
    public void tearDown() {
        recorder.unregister();
        EventTestDatabase.tearDown();
    }

    @Test
    public void markingItemsPlayedPublishesFeedItemEventFlaggedAsUnreadStatusChange()
            throws ExecutionException, InterruptedException {
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(items.get(0))).get();

        FeedItemEvent event = recorder.single(FeedItemEvent.class);
        assertTrue(event.unreadStatusChanged);
        assertEquals(1, event.items.size());
        assertEquals(items.get(0).getId(), event.items.get(0).getId());
        assertEquals(FeedItem.PLAYED, DBReader.getFeedItem(items.get(0).getId()).getPlayState());
    }

    @Test
    public void savingASingleItemPublishesFeedItemEventWithTheCallersUnreadStatusFlag()
            throws ExecutionException, InterruptedException {
        DBWriter.setFeedItem(items.get(1), false).get();

        FeedItemEvent event = recorder.single(FeedItemEvent.class);
        assertFalse(event.unreadStatusChanged);
        assertEquals(items.get(1).getId(), event.items.get(0).getId());
    }

    @Test
    public void indexOfItemWithIdLocatesThePersistedItemAndRejectsUnknownIds() {
        List<FeedItem> stored = DBReader.getFeed(feed.getId(), true, 0, Integer.MAX_VALUE).getItems();

        assertEquals(0, FeedItemEvent.indexOfItemWithId(stored, stored.get(0).getId()));
        assertEquals(2, FeedItemEvent.indexOfItemWithId(stored, stored.get(2).getId()));
        assertEquals(-1, FeedItemEvent.indexOfItemWithId(stored, Long.MAX_VALUE));
    }

    @Test
    public void indexOfItemWithIdSkipsNullEntries() {
        List<FeedItem> withHole = new ArrayList<>();
        withHole.add(null);
        withHole.add(items.get(0));

        assertEquals(1, FeedItemEvent.indexOfItemWithId(withHole, items.get(0).getId()));
    }

    @Test
    public void changingTheFeedFilterPublishesFilterChangedForThatFeedOnly()
            throws ExecutionException, InterruptedException {
        DBWriter.setFeedItemsFilter(feed.getId(), new HashSet<>(Collections.singletonList("played"))).get();

        FeedEvent event = recorder.single(FeedEvent.class);
        assertEquals(feed.getId(), event.feedId);
        assertEquals("FeedEvent{action=FILTER_CHANGED, feedId=" + feed.getId() + "}", event.toString());
    }

    @Test
    public void changingTheFeedSortOrderPublishesSortOrderChangedForThatFeed()
            throws ExecutionException, InterruptedException {
        DBWriter.setFeedItemSortOrder(feed.getId(), SortOrder.EPISODE_TITLE_Z_A).get();

        FeedEvent event = recorder.single(FeedEvent.class);
        assertEquals(feed.getId(), event.feedId);
        assertEquals("FeedEvent{action=SORT_ORDER_CHANGED, feedId=" + feed.getId() + "}", event.toString());
    }

    @Test
    public void feedListUpdateEventMatchesOnlyTheFeedsItWasBuiltFrom() {
        Feed other = EventTestDatabase.storeFeed(context, "http://example.com/other", "Other", 1);

        assertTrue(new FeedListUpdateEvent(feed).contains(feed));
        assertFalse(new FeedListUpdateEvent(feed).contains(other));
        assertTrue(new FeedListUpdateEvent(other.getId()).contains(other));
        FeedListUpdateEvent both = new FeedListUpdateEvent(Arrays.asList(feed, other));
        assertTrue(both.contains(feed));
        assertTrue(both.contains(other));
    }

    @Test
    public void feedListUpdateEventIsDeliveredToSubscribers() {
        EventBus.getDefault().post(new FeedListUpdateEvent(feed));

        assertTrue(recorder.single(FeedListUpdateEvent.class).contains(feed));
    }

    @Test
    public void episodeDownloadEventExposesTheUrlsItWasBuiltFrom() {
        Map<String, DownloadStatus> statuses = new HashMap<>();
        statuses.put(items.get(0).getMedia().getDownloadUrl(), new DownloadStatus(DownloadStatus.STATE_RUNNING, 42));
        statuses.put(items.get(1).getMedia().getDownloadUrl(), new DownloadStatus(DownloadStatus.STATE_QUEUED, 0));

        EpisodeDownloadEvent event = new EpisodeDownloadEvent(statuses);

        assertEquals(2, event.getUrls().size());
        assertTrue(event.getUrls().contains(items.get(0).getMedia().getDownloadUrl()));
        assertFalse(event.getUrls().contains(items.get(2).getMedia().getDownloadUrl()));
    }

    @Test
    public void indexOfItemWithDownloadUrlLocatesThePersistedMediaAndRejectsUnknownUrls() {
        List<FeedItem> stored = DBReader.getFeed(feed.getId(), true, 0, Integer.MAX_VALUE).getItems();

        assertEquals(1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(
                stored, stored.get(1).getMedia().getDownloadUrl()));
        assertEquals(-1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(stored, "http://example.com/unknown"));
    }

    @Test
    public void indexOfItemWithDownloadUrlSkipsItemsWithoutMedia() {
        List<FeedItem> mixed = new ArrayList<>();
        mixed.add(null);
        mixed.add(new FeedItem());
        mixed.add(items.get(0));

        assertEquals(2, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(
                mixed, items.get(0).getMedia().getDownloadUrl()));
    }
}
