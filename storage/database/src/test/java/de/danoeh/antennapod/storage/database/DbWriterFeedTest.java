package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.event.FeedEvent;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DbWriterFeedTest extends DatabaseTestBase {

    private static Feed load(Feed feed) {
        return DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
    }

    @Test
    public void addNewFeedStoresFeedWithItemsAndQueuesSubscriptionForSync() {
        Feed feed = new Feed("https://example.com/new.xml", null, "New feed");
        FeedItem item = new FeedItem(0, "episode", "guid", "link", new Date(5000), FeedItem.NEW, feed);
        feed.setItems(Collections.singletonList(item));

        await(DBWriter.addNewFeed(context, feed));

        Feed stored = load(feed);
        assertEquals("New feed", stored.getTitle());
        assertEquals(1, stored.getItems().size());
        assertEquals("episode", stored.getItems().get(0).getTitle());
        assertEquals(Collections.singletonList("https://example.com/new.xml"), synchronizationQueue.getAddedFeeds());
    }

    @Test
    public void addNewFeedDoesNotSyncFeedsThatAreNotSubscribed() {
        Feed feed = new Feed("https://example.com/browse.xml", null, "Browsed");
        feed.setItems(Collections.emptyList());
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);

        await(DBWriter.addNewFeed(context, feed));

        assertEquals(Feed.STATE_NOT_SUBSCRIBED, load(feed).getState());
        assertTrue(synchronizationQueue.getAddedFeeds().isEmpty());
    }

    @Test
    public void addNewFeedDoesNotSyncLocalFeeds() {
        Feed feed = new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://folder", null, "Local");
        feed.setItems(Collections.emptyList());

        await(DBWriter.addNewFeed(context, feed));

        assertEquals(1, DBReader.getFeedList().size());
        assertTrue(synchronizationQueue.getAddedFeeds().isEmpty());
    }

    @Test
    public void setCompleteFeedStoresPreferencesAlongWithFeed() {
        Feed feed = new Feed("https://example.com/prefs.xml", null, "With prefs");
        feed.setItems(Collections.emptyList());
        feed.setPreferences(new FeedPreferences(0, FeedPreferences.AutoDownloadSetting.ENABLED,
                FeedPreferences.AutoDeleteAction.ALWAYS, VolumeAdaptionSetting.HEAVY_REDUCTION,
                FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, "user", "secret"));

        await(DBWriter.setCompleteFeed(feed));

        FeedPreferences stored = load(feed).getPreferences();
        assertEquals(FeedPreferences.AutoDownloadSetting.ENABLED, stored.getAutoDownload());
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, stored.getAutoDeleteAction());
        assertEquals(VolumeAdaptionSetting.HEAVY_REDUCTION, stored.getVolumeAdaptionSetting());
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, stored.getNewEpisodesAction());
        assertEquals("user", stored.getUsername());
        assertEquals("secret", stored.getPassword());
    }

    @Test
    public void setFeedPreferencesPersistsEveryPreferenceAndPostsUpdate() {
        Feed feed = storeFeed("feed");
        FeedPreferences preferences = new FeedPreferences(feed.getId(),
                FeedPreferences.AutoDownloadSetting.DISABLED, false, FeedPreferences.AutoDeleteAction.NEVER,
                VolumeAdaptionSetting.LIGHT_REDUCTION, "name", "pass", new FeedFilter("include", "exclude", 30),
                1.5f, 10, 20, FeedPreferences.SkipSilence.AGGRESSIVE, true,
                FeedPreferences.NewEpisodesAction.NOTHING, new HashSet<>(Arrays.asList("news", "tech")));

        await(DBWriter.setFeedPreferences(preferences));

        FeedPreferences stored = load(feed).getPreferences();
        assertEquals(FeedPreferences.AutoDownloadSetting.DISABLED, stored.getAutoDownload());
        assertFalse(stored.getKeepUpdated());
        assertEquals(FeedPreferences.AutoDeleteAction.NEVER, stored.getAutoDeleteAction());
        assertEquals(VolumeAdaptionSetting.LIGHT_REDUCTION, stored.getVolumeAdaptionSetting());
        assertEquals("name", stored.getUsername());
        assertEquals("pass", stored.getPassword());
        assertEquals("include", stored.getFilter().getIncludeFilterRaw());
        assertEquals("exclude", stored.getFilter().getExcludeFilterRaw());
        assertEquals(30, stored.getFilter().getMinimalDurationFilter());
        assertEquals(1.5f, stored.getFeedPlaybackSpeed(), 0.0001f);
        assertEquals(10, stored.getFeedSkipIntro());
        assertEquals(20, stored.getFeedSkipEnding());
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, stored.getFeedSkipSilence());
        assertTrue(stored.getShowEpisodeNotification());
        assertEquals(FeedPreferences.NewEpisodesAction.NOTHING, stored.getNewEpisodesAction());
        assertEquals(new HashSet<>(Arrays.asList("news", "tech")), stored.getTags());
        assertTrue(events.eventsOfType(FeedListUpdateEvent.class).get(0).contains(feed));
    }

    @Test
    public void storingPreferencesWithoutFeedIdIsRejected() {
        FeedPreferences preferences = new FeedPreferences(0, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, null, null);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        assertThrows(IllegalArgumentException.class, () -> adapter.setFeedPreferences(preferences));
        adapter.close();
    }

    @Test
    public void deleteFeedRemovesFeedItemsAndQueueEntries() {
        Feed feed = storeFeed("doomed");
        Feed other = storeFeed("other");
        FeedItem doomedItem = storeItem(feed, "doomed item");
        FeedItem otherItem = storeItem(other, "other item");
        await(DBWriter.addQueueItem(context, doomedItem, otherItem));
        events.clear();

        await(DBWriter.deleteFeed(context, feed.getId()));

        List<Feed> remaining = DBReader.getFeedList();
        assertEquals(1, remaining.size());
        assertEquals(other.getId(), remaining.get(0).getId());
        assertNull(DBReader.getFeedItem(doomedItem.getId()));
        assertNotNull(DBReader.getFeedItem(otherItem.getId()));
        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(1, queue.size());
        assertEquals(otherItem.getId(), queue.get(0).getId());
        assertEquals(Collections.singletonList(feed.getDownloadUrl()), synchronizationQueue.getRemovedFeeds());
        assertTrue(events.eventsOfType(FeedListUpdateEvent.class).get(0).contains(feed));
        QueueEvent queueEvent = events.eventsOfType(QueueEvent.class).get(0);
        assertEquals(QueueEvent.Action.IRREVERSIBLE_REMOVED, queueEvent.action);
        assertEquals(doomedItem.getId(), queueEvent.item.getId());
    }

    @Test
    public void deleteFeedThatIsNotSubscribedIsNotReportedForSync() {
        Feed feed = storeFeed("browsed");
        await(DBWriter.setFeedState(context, load(feed), Feed.STATE_NOT_SUBSCRIBED));

        await(DBWriter.deleteFeed(context, feed.getId()));

        assertTrue(DBReader.getFeedList().isEmpty());
        assertTrue(synchronizationQueue.getRemovedFeeds().isEmpty());
    }

    @Test
    public void deleteFeedWithUnknownIdChangesNothing() {
        Feed feed = storeFeed("kept");

        await(DBWriter.deleteFeed(context, feed.getId() + 100));

        assertEquals(1, DBReader.getFeedList().size());
        assertTrue(synchronizationQueue.getRemovedFeeds().isEmpty());
        assertTrue(events.eventsOfType(FeedListUpdateEvent.class).isEmpty());
    }

    @Test
    public void removeFeedWithDownloadUrlDeletesOnlyThatFeed() {
        Feed feed = storeFeed("first");
        Feed other = storeFeed("second");

        DBWriter.removeFeedWithDownloadUrl(context, feed.getDownloadUrl());

        List<Feed> remaining = DBReader.getFeedList();
        assertEquals(1, remaining.size());
        assertEquals(other.getId(), remaining.get(0).getId());
    }

    @Test
    public void removeFeedWithUnknownDownloadUrlKeepsAllFeeds() {
        storeFeed("first");
        storeFeed("second");

        DBWriter.removeFeedWithDownloadUrl(context, "https://example.com/unknown.xml");

        assertEquals(2, DBReader.getFeedList().size());
    }

    @Test
    public void updateFeedDownloadUrlReplacesOnlyMatchingUrl() {
        Feed feed = storeFeed("moved");
        Feed other = storeFeed("stays");
        String originalOtherUrl = other.getDownloadUrl();

        await(DBWriter.updateFeedDownloadURL(feed.getDownloadUrl(), "https://example.com/moved-new.xml"));

        assertEquals("https://example.com/moved-new.xml", load(feed).getDownloadUrl());
        assertEquals(originalOtherUrl, load(other).getDownloadUrl());
    }

    @Test
    public void setFeedCustomTitleOverridesDisplayedTitleUntilCleared() {
        Feed feed = storeFeed("original");
        feed.setCustomTitle("custom");

        await(DBWriter.setFeedCustomTitle(feed));

        assertEquals("custom", load(feed).getTitle());
        assertEquals("original", load(feed).getFeedTitle());
        assertTrue(events.eventsOfType(FeedListUpdateEvent.class).get(0).contains(feed));

        feed.setCustomTitle(null);
        await(DBWriter.setFeedCustomTitle(feed));

        assertEquals("original", load(feed).getTitle());
    }

    @Test
    public void setFeedLastUpdateFailedRecordsFailureAndRefreshAttempt() {
        Feed feed = storeFeed("flaky");
        long before = System.currentTimeMillis();

        await(DBWriter.setFeedLastUpdateFailed(feed.getId(), true));

        Feed stored = load(feed);
        assertTrue(stored.hasLastUpdateFailed());
        assertTrue(stored.getLastRefreshAttempt() >= before);

        await(DBWriter.setFeedLastUpdateFailed(feed.getId(), false));

        assertFalse(load(feed).hasLastUpdateFailed());
    }

    @Test
    public void resubscribingFeedEnablesUpdatesAndReportsPlayedEpisodesForSync() {
        Feed feed = storeFeed("browsed");
        FeedItem played = storeItem(feed, "played", 1000, FeedItem.PLAYED);
        storeItem(feed, "unplayed", 2000, FeedItem.UNPLAYED);
        await(DBWriter.setFeedState(context, load(feed), Feed.STATE_NOT_SUBSCRIBED));
        Feed notSubscribed = load(feed);
        notSubscribed.getPreferences().setKeepUpdated(false);
        assertEquals(Feed.STATE_NOT_SUBSCRIBED, notSubscribed.getState());

        await(DBWriter.setFeedState(context, notSubscribed, Feed.STATE_SUBSCRIBED));

        Feed stored = load(feed);
        assertEquals(Feed.STATE_SUBSCRIBED, stored.getState());
        assertTrue(stored.getPreferences().getKeepUpdated());
        assertEquals(Collections.singletonList(notSubscribed), feedUpdateManager.getAskedFeeds());
        assertEquals(Collections.singletonList(feed.getDownloadUrl()), synchronizationQueue.getAddedFeeds());
        assertEquals(1, synchronizationQueue.getPlayedMedia().size());
        assertEquals(played.getMedia().getId(), synchronizationQueue.getPlayedMedia().get(0).getId());
    }

    @Test
    public void archivingFeedOnlyChangesItsState() {
        Feed feed = storeFeed("feed");

        await(DBWriter.setFeedState(context, load(feed), Feed.STATE_ARCHIVED));

        assertEquals(Feed.STATE_ARCHIVED, load(feed).getState());
        assertTrue(feedUpdateManager.getAskedFeeds().isEmpty());
        assertTrue(synchronizationQueue.getAddedFeeds().isEmpty());
        assertTrue(events.eventsOfType(FeedListUpdateEvent.class).get(0).contains(feed));
    }

    @Test
    public void setFeedItemsFilterHidesItemsOfFilteredFeedView() {
        Feed feed = storeFeed("feed");
        storeItem(feed, "played", 1000, FeedItem.PLAYED);
        storeItem(feed, "unplayed", 2000, FeedItem.UNPLAYED);

        await(DBWriter.setFeedItemsFilter(feed.getId(), new HashSet<>(Collections.singletonList(FeedItemFilter.PLAYED))));

        Feed filtered = DBReader.getFeed(feed.getId(), true, 0, Integer.MAX_VALUE);
        assertEquals(1, filtered.getItems().size());
        assertEquals("played", filtered.getItems().get(0).getTitle());
        assertEquals(2, load(feed).getItems().size());
        FeedEvent event = events.eventsOfType(FeedEvent.class).get(0);
        assertEquals(feed.getId(), event.feedId);
    }

    @Test
    public void setFeedItemSortOrderControlsItemOrderAndCanBeReset() {
        Feed feed = storeFeed("feed");
        storeItem(feed, "b", 1000, FeedItem.UNPLAYED);
        storeItem(feed, "a", 2000, FeedItem.UNPLAYED);

        await(DBWriter.setFeedItemSortOrder(feed.getId(), SortOrder.EPISODE_TITLE_A_Z));

        Feed sorted = load(feed);
        assertEquals(SortOrder.EPISODE_TITLE_A_Z, sorted.getSortOrder());
        assertEquals("a", sorted.getItems().get(0).getTitle());
        assertEquals("b", sorted.getItems().get(1).getTitle());

        await(DBWriter.setFeedItemSortOrder(feed.getId(), null));

        assertNull(load(feed).getSortOrder());
    }

    @Test
    public void resetPagedFeedPageRestoresFirstPageLink() {
        Feed feed = new Feed("https://example.com/paged.xml", null, "Paged");
        feed.setItems(Collections.emptyList());
        feed.setPaged(true);
        feed.setNextPageLink("https://example.com/paged.xml?page=7");
        await(DBWriter.setCompleteFeed(feed));
        assertEquals("https://example.com/paged.xml?page=7", load(feed).getNextPageLink());

        await(DBWriter.resetPagedFeedPage(feed));

        assertEquals("https://example.com/paged.xml", load(feed).getNextPageLink());
    }
}
