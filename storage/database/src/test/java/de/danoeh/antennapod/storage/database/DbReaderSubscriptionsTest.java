package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedOrder;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SubscriptionsFilter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DbReaderSubscriptionsTest extends DatabaseTestBase {
    private Feed alpha;
    private Feed bravo;
    private Feed charlie;
    private Feed delta;

    @Before
    public void createSubscriptions() {
        alpha = storeFeed("Alpha");
        bravo = storeFeed("Bravo");
        charlie = storeFeed("Charlie");
        delta = storeFeed("Delta");
        delta.setState(Feed.STATE_NOT_SUBSCRIBED);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setFeedState(delta.getId(), Feed.STATE_NOT_SUBSCRIBED);
        adapter.close();

        storeItem(alpha, "alpha new", 1000, FeedItem.NEW);
        FeedItem alphaDownloaded = storeItem(alpha, "alpha downloaded", 2000, FeedItem.UNPLAYED);
        alphaDownloaded.getMedia().setLocalFileUrl("/downloads/alpha.mp3");
        alphaDownloaded.getMedia().setDownloaded(true, 10);
        await(DBWriter.setFeedMedia(alphaDownloaded.getMedia()));
        storeItem(alpha, "alpha played", 3000, FeedItem.PLAYED);
        for (int i = 0; i < 3; i++) {
            storeItem(bravo, "bravo " + i, 5000 + i, FeedItem.UNPLAYED);
        }
        storeItem(charlie, "charlie one", 4000, FeedItem.PLAYED);
        storeItem(charlie, "charlie two", 4001, FeedItem.PLAYED);
        storeItem(delta, "delta", 9000, FeedItem.UNPLAYED);

        updatePreferences(bravo, preferences -> {
            preferences.getTags().clear();
            preferences.getTags().addAll(Arrays.asList("tech", "news"));
        });
        updatePreferences(charlie, preferences -> preferences.getTags().add(FeedPreferences.TAG_ROOT));
        await(DBWriter.addQueueItem(context, alphaDownloaded));
    }

    private void updatePreferences(Feed feed, Consumer<FeedPreferences> change) {
        Feed stored = DBReader.getFeed(feed.getId(), false, 0, 1);
        change.accept(stored.getPreferences());
        await(DBWriter.setFeedPreferences(stored.getPreferences()));
    }

    private static List<String> feedTitles(NavDrawerData data) {
        List<String> result = new ArrayList<>();
        for (Feed feed : data.feeds) {
            result.add(feed.getTitle());
        }
        return result;
    }

    private static List<String> tagTitles(List<NavDrawerData.TagItem> tags) {
        List<String> result = new ArrayList<>();
        for (NavDrawerData.TagItem tag : tags) {
            result.add(tag.getTitle());
        }
        return result;
    }

    private NavDrawerData navDrawer(String filter, FeedOrder order, FeedCounter counter) {
        return DBReader.getNavDrawerData(new SubscriptionsFilter(filter), order, counter, Feed.STATE_SUBSCRIBED);
    }

    @Test
    public void navDrawerListsOnlyFeedsOfRequestedState() {
        NavDrawerData subscribed = navDrawer("", FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE);
        NavDrawerData browsed = DBReader.getNavDrawerData(new SubscriptionsFilter(SubscriptionsFilter.SHOW_NON_SUBSCRIBED_FEEDS),
                FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE, Feed.STATE_NOT_SUBSCRIBED);

        assertEquals(Arrays.asList("Alpha", "Bravo", "Charlie"), feedTitles(subscribed));
        assertEquals(Collections.singletonList("Delta"), feedTitles(browsed));
    }

    @Test
    public void navDrawerWithoutFilterBehavesLikeEmptyFilter() {
        NavDrawerData data = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE,
                Feed.STATE_SUBSCRIBED);

        assertEquals(Arrays.asList("Alpha", "Bravo", "Charlie"), feedTitles(data));
    }

    @Test
    public void navDrawerHidesNotSubscribedFeedsUnlessFilterShowsThem() {
        NavDrawerData hidden = DBReader.getNavDrawerData(new SubscriptionsFilter(""), FeedOrder.ALPHABETICAL,
                FeedCounter.SHOW_NONE, Feed.STATE_NOT_SUBSCRIBED);

        assertTrue(hidden.feeds.isEmpty());
    }

    @Test
    public void navDrawerReportsQueueNewAndDownloadedTotals() {
        NavDrawerData data = navDrawer("", FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE);

        assertEquals(1, data.queueSize);
        assertEquals(1, data.numNewItems);
        assertEquals(1, data.numDownloadedItems);
    }

    @Test
    public void newCounterCountsNewEpisodesPerSubscribedFeed() {
        NavDrawerData data = navDrawer("", FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NEW);

        assertEquals(1, data.feedCounters.size());
        assertEquals(1, (int) data.feedCounters.get(alpha.getId()));
    }

    @Test
    public void unplayedCounterCountsNewAndUnplayedEpisodes() {
        NavDrawerData data = navDrawer("", FeedOrder.ALPHABETICAL, FeedCounter.SHOW_UNPLAYED);

        assertEquals(2, (int) data.feedCounters.get(alpha.getId()));
        assertEquals(3, (int) data.feedCounters.get(bravo.getId()));
        assertFalse(data.feedCounters.containsKey(charlie.getId()));
        assertFalse(data.feedCounters.containsKey(delta.getId()));
    }

    @Test
    public void downloadedCountersCountDownloadedEpisodes() {
        NavDrawerData downloaded = navDrawer("", FeedOrder.ALPHABETICAL, FeedCounter.SHOW_DOWNLOADED);
        NavDrawerData downloadedUnplayed = navDrawer("", FeedOrder.ALPHABETICAL,
                FeedCounter.SHOW_DOWNLOADED_UNPLAYED);

        assertEquals(Collections.singletonMap(alpha.getId(), 1), downloaded.feedCounters);
        assertEquals(Collections.singletonMap(alpha.getId(), 1), downloadedUnplayed.feedCounters);
    }

    @Test
    public void noneCounterProducesNoCounters() {
        assertTrue(navDrawer("", FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE).feedCounters.isEmpty());
    }

    @Test
    public void counterOrderShowsFeedsWithMostEpisodesFirstAndFallsBackToTitle() {
        NavDrawerData data = navDrawer("", FeedOrder.COUNTER, FeedCounter.SHOW_UNPLAYED);

        assertEquals(Arrays.asList("Bravo", "Alpha", "Charlie"), feedTitles(data));
    }

    @Test
    public void counterOrderFallsBackToTitleWhenNoCountersAreShown() {
        NavDrawerData data = navDrawer("", FeedOrder.COUNTER, FeedCounter.SHOW_NONE);

        assertEquals(Arrays.asList("Alpha", "Bravo", "Charlie"), feedTitles(data));
    }

    @Test
    public void mostPlayedOrderFallsBackToTitleForFeedsWithSamePlayedCount() {
        storeFeed("Aardvark");

        NavDrawerData data = navDrawer("", FeedOrder.MOST_PLAYED, FeedCounter.SHOW_NONE);

        assertEquals(Arrays.asList("Charlie", "Alpha", "Aardvark", "Bravo"), feedTitles(data));
    }

    @Test
    public void alphabeticalOrderPutsFeedsWithoutTitleLast() {
        Feed untitled = new Feed("https://example.com/untitled.xml", null, null);
        untitled.setItems(new ArrayList<>());
        await(DBWriter.setCompleteFeed(untitled));

        NavDrawerData data = navDrawer("", FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE);

        assertEquals(4, data.feeds.size());
        assertEquals(Arrays.asList("Alpha", "Bravo", "Charlie"), feedTitles(data).subList(0, 3));
        assertEquals(untitled.getId(), data.feeds.get(3).getId());
    }

    @Test
    public void mostPlayedOrderShowsFeedsWithMostPlayedEpisodesFirst() {
        NavDrawerData data = navDrawer("", FeedOrder.MOST_PLAYED, FeedCounter.SHOW_NONE);

        assertEquals(Arrays.asList("Charlie", "Alpha", "Bravo"), feedTitles(data));
    }

    @Test
    public void mostRecentEpisodeOrderShowsFeedWithNewestEpisodeFirst() {
        NavDrawerData data = navDrawer("", FeedOrder.MOST_RECENT_EPISODE, FeedCounter.SHOW_NONE);

        assertEquals(Arrays.asList("Bravo", "Charlie", "Alpha"), feedTitles(data));
    }

    @Test
    public void navDrawerGroupsFeedsByTagWithUntaggedFeedsFirst() {
        NavDrawerData data = navDrawer("", FeedOrder.ALPHABETICAL, FeedCounter.SHOW_UNPLAYED);

        assertEquals(Arrays.asList(FeedPreferences.TAG_UNTAGGED, FeedPreferences.TAG_ROOT, "news", "tech"),
                tagTitles(data.tags));
        NavDrawerData.TagItem untagged = data.tags.get(0);
        assertEquals(2, untagged.getFeeds().size());
        assertEquals("Alpha", untagged.getFeeds().get(0).getTitle());
        assertEquals("Charlie", untagged.getFeeds().get(1).getTitle());
        assertEquals(0, untagged.getCounter());
        NavDrawerData.TagItem news = data.tags.get(2);
        assertEquals(Collections.singletonList("Bravo"), Arrays.asList(news.getFeeds().get(0).getTitle()));
        assertEquals(3, news.getCounter());
    }

    @Test
    public void navDrawerOmitsUntaggedGroupWhenEveryFeedHasATag() {
        updatePreferences(alpha, preferences -> preferences.getTags().add("audio"));
        updatePreferences(charlie, preferences -> preferences.getTags().add("audio"));

        NavDrawerData data = navDrawer("", FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE);

        assertFalse(tagTitles(data.tags).contains(FeedPreferences.TAG_UNTAGGED));
        assertEquals(Arrays.asList("#root", "audio", "news", "tech"), tagTitles(data.tags));
    }

    @Test
    public void allTagsStartWithRootTagContainingEveryFeedAndEndWithUntagged() {
        List<NavDrawerData.TagItem> tags = DBReader.getAllTags(Feed.STATE_SUBSCRIBED);

        assertEquals(Arrays.asList(FeedPreferences.TAG_ROOT, "news", "tech", FeedPreferences.TAG_UNTAGGED),
                tagTitles(tags));
        assertEquals(3, tags.get(0).getFeeds().size());
        assertEquals(Collections.singletonList("Bravo"), Arrays.asList(tags.get(1).getFeeds().get(0).getTitle()));
        assertEquals(2, tags.get(3).getFeeds().size());
    }

    @Test
    public void allTagsForNotSubscribedFeedsOnlyContainThoseFeeds() {
        List<NavDrawerData.TagItem> tags = DBReader.getAllTags(Feed.STATE_NOT_SUBSCRIBED);

        assertEquals(Arrays.asList(FeedPreferences.TAG_ROOT, FeedPreferences.TAG_UNTAGGED), tagTitles(tags));
        assertEquals("Delta", tags.get(0).getFeeds().get(0).getTitle());
    }

    @Test
    public void tagItemsAccumulateCounterAndOpenState() {
        NavDrawerData.TagItem tag = new NavDrawerData.TagItem("music");
        tag.addFeed(alpha, 2);
        tag.addFeed(bravo, 5);

        assertEquals(7, tag.getCounter());
        assertEquals(Arrays.asList(alpha, bravo), tag.getFeeds());
        assertFalse(tag.isOpen());
        tag.setOpen(true);
        assertTrue(tag.isOpen());
        assertTrue(tag.getId() > 0);
        assertNotEquals(new NavDrawerData.TagItem("podcasts").getId(), tag.getId());
        assertEquals(new NavDrawerData.TagItem("music").getId(), tag.getId());
    }

    @Test
    public void filterByCounterKeepsOnlyFeedsWithUnreadEpisodes() {
        NavDrawerData data = navDrawer(SubscriptionsFilter.COUNTER_GREATER_ZERO, FeedOrder.ALPHABETICAL,
                FeedCounter.SHOW_UNPLAYED);

        assertEquals(Arrays.asList("Alpha", "Bravo"), feedTitles(data));
    }

    @Test
    public void filterByUpdatesSeparatesFeedsThatAreKeptUpdated() {
        updatePreferences(bravo, preferences -> preferences.setKeepUpdated(false));

        assertEquals(Arrays.asList("Alpha", "Charlie"),
                feedTitles(navDrawer(SubscriptionsFilter.ENABLED_UPDATES, FeedOrder.ALPHABETICAL,
                        FeedCounter.SHOW_NONE)));
        assertEquals(Collections.singletonList("Bravo"),
                feedTitles(navDrawer(SubscriptionsFilter.DISABLED_UPDATES, FeedOrder.ALPHABETICAL,
                        FeedCounter.SHOW_NONE)));
    }

    @Test
    public void filterByAutoDownloadUsesFeedSettingWithGlobalFallback() {
        updatePreferences(alpha, preferences ->
                preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED));

        assertEquals(Collections.singletonList("Alpha"),
                feedTitles(navDrawer(SubscriptionsFilter.ENABLED_AUTO_DOWNLOAD, FeedOrder.ALPHABETICAL,
                        FeedCounter.SHOW_NONE)));
        assertEquals(Arrays.asList("Bravo", "Charlie"),
                feedTitles(navDrawer(SubscriptionsFilter.DISABLED_AUTO_DOWNLOAD, FeedOrder.ALPHABETICAL,
                        FeedCounter.SHOW_NONE)));
    }

    @Test
    public void filterByEpisodeNotificationSeparatesFeedsWithNotifications() {
        updatePreferences(charlie, preferences -> preferences.setShowEpisodeNotification(true));

        assertEquals(Collections.singletonList("Charlie"),
                feedTitles(navDrawer(SubscriptionsFilter.EPISODE_NOTIFICATION_ENABLED, FeedOrder.ALPHABETICAL,
                        FeedCounter.SHOW_NONE)));
        assertEquals(Arrays.asList("Alpha", "Bravo"),
                feedTitles(navDrawer(SubscriptionsFilter.EPISODE_NOTIFICATION_DISABLED, FeedOrder.ALPHABETICAL,
                        FeedCounter.SHOW_NONE)));
    }

    @Test
    public void combinedFiltersMustAllMatch() {
        updatePreferences(bravo, preferences -> preferences.setKeepUpdated(false));

        NavDrawerData data = navDrawer(SubscriptionsFilter.ENABLED_UPDATES + ","
                + SubscriptionsFilter.COUNTER_GREATER_ZERO, FeedOrder.ALPHABETICAL, FeedCounter.SHOW_UNPLAYED);

        assertEquals(Collections.singletonList("Alpha"), feedTitles(data));
    }

    @Test
    public void feedsWithoutStoredTagsAreInRootTagAndStoredTagsSurviveRoundTrip() {
        assertEquals(Collections.singleton(FeedPreferences.TAG_ROOT),
                DBReader.getFeed(alpha.getId(), false, 0, 1).getPreferences().getTags());
        FeedPreferences stored = DBReader.getFeed(bravo.getId(), false, 0, 1).getPreferences();

        assertEquals(new HashSet<>(Arrays.asList("tech", "news")), stored.getTags());
    }
}
