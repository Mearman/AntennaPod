package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DbReaderEpisodeListTest extends DatabaseTestBase {
    private Feed alpha;
    private Feed beta;
    private Feed gamma;
    private FeedItem newItem;
    private FeedItem queued;
    private FeedItem favouriteDownloaded;
    private FeedItem paused;
    private FeedItem withoutMedia;
    private FeedItem archivedItem;
    private FeedItem notSubscribedItem;

    @Before
    public void createEpisodes() {
        alpha = storeFeed("Alpha");
        beta = storeFeed("Beta");
        gamma = storeFeed("Gamma");
        beta.setState(Feed.STATE_ARCHIVED);
        gamma.setState(Feed.STATE_NOT_SUBSCRIBED);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setFeedState(beta.getId(), Feed.STATE_ARCHIVED);
        adapter.setFeedState(gamma.getId(), Feed.STATE_NOT_SUBSCRIBED);
        adapter.close();

        newItem = storeItem(alpha, "a1", 1000, FeedItem.NEW);
        configureMedia(newItem, 100, 10, 0);
        queued = storeItem(alpha, "a2", 2000, FeedItem.UNPLAYED);
        configureMedia(queued, 200, 20, 0);
        favouriteDownloaded = storeItem(alpha, "a3", 3000, FeedItem.PLAYED);
        configureMedia(favouriteDownloaded, 300, 30, 0);
        favouriteDownloaded.getMedia().setLocalFileUrl("/downloads/a3.mp3");
        favouriteDownloaded.getMedia().setDownloaded(true, 9);
        favouriteDownloaded.getMedia().setLastPlayedTimeHistory(new Date(7000));
        await(DBWriter.setFeedMedia(favouriteDownloaded.getMedia()));
        paused = storeItem(alpha, "a4", 4000, FeedItem.UNPLAYED);
        configureMedia(paused, 400, 40, 500);
        withoutMedia = storeItemWithoutMedia(alpha, "a5", 5000);
        archivedItem = storeItem(beta, "b1", 6000, FeedItem.UNPLAYED);
        notSubscribedItem = storeItem(gamma, "c1", 7000, FeedItem.UNPLAYED);

        await(DBWriter.addQueueItem(context, queued));
        await(DBWriter.addFavoriteItems(Collections.singletonList(favouriteDownloaded)));
        events.clear();
    }

    private void configureMedia(FeedItem item, int duration, long size, int position) {
        item.getMedia().setDuration(duration);
        item.getMedia().setSize(size);
        item.getMedia().setPosition(position);
        await(DBWriter.setFeedMedia(item.getMedia()));
    }

    private static List<String> titles(List<FeedItem> items) {
        List<String> result = new ArrayList<>();
        for (FeedItem item : items) {
            result.add(item.getTitle());
        }
        return result;
    }

    private static List<String> episodeTitles(FeedItemFilter filter) {
        return titles(DBReader.getEpisodes(0, Integer.MAX_VALUE, filter, SortOrder.EPISODE_TITLE_A_Z));
    }

    private static List<String> episodeTitles(String... filterProperties) {
        return episodeTitles(new FeedItemFilter(filterProperties));
    }

    @Test
    public void unfilteredEpisodesOnlyContainSubscribedFeeds() {
        assertEquals(Arrays.asList("a1", "a2", "a3", "a4", "a5"), episodeTitles(FeedItemFilter.unfiltered()));
    }

    @Test
    public void includingAllFeedStatesAddsArchivedAndNotSubscribedFeeds() {
        assertEquals(Arrays.asList("a1", "a2", "a3", "a4", "a5", "b1", "c1"),
                episodeTitles(FeedItemFilter.INCLUDE_ALL_FEED_STATES));
    }

    @Test
    public void feedStateFiltersSelectEachStateSeparately() {
        assertEquals(Collections.singletonList("b1"), episodeTitles(FeedItemFilter.INCLUDE_ARCHIVED));
        assertEquals(Collections.singletonList("c1"), episodeTitles(FeedItemFilter.INCLUDE_NOT_SUBSCRIBED));
        assertEquals(Arrays.asList("a1", "a2", "a3", "a4", "a5", "b1"),
                episodeTitles(FeedItemFilter.INCLUDE_SUBSCRIBED, FeedItemFilter.INCLUDE_ARCHIVED));
    }

    @Test
    public void playStateFiltersSelectMatchingEpisodes() {
        assertEquals(Collections.singletonList("a3"), episodeTitles(FeedItemFilter.PLAYED));
        assertEquals(Arrays.asList("a1", "a2", "a4", "a5"), episodeTitles(FeedItemFilter.UNPLAYED));
        assertEquals(Collections.singletonList("a1"), episodeTitles(FeedItemFilter.NEW));
    }

    @Test
    public void pausedFiltersSelectEpisodesByPlaybackPosition() {
        assertEquals(Collections.singletonList("a4"), episodeTitles(FeedItemFilter.PAUSED));
        assertEquals(Arrays.asList("a1", "a2", "a3", "a5"), episodeTitles(FeedItemFilter.NOT_PAUSED));
    }

    @Test
    public void queueFiltersSelectEpisodesByQueueMembership() {
        assertEquals(Collections.singletonList("a2"), episodeTitles(FeedItemFilter.QUEUED));
        assertEquals(Arrays.asList("a1", "a3", "a4", "a5"), episodeTitles(FeedItemFilter.NOT_QUEUED));
    }

    @Test
    public void downloadFiltersSelectEpisodesByDownloadState() {
        assertEquals(Collections.singletonList("a3"), episodeTitles(FeedItemFilter.DOWNLOADED));
        assertEquals(Arrays.asList("a1", "a2", "a4"),
                episodeTitles(FeedItemFilter.NOT_DOWNLOADED, FeedItemFilter.HAS_MEDIA));
    }

    @Test
    public void mediaFiltersSelectEpisodesByPresenceOfMedia() {
        assertEquals(Arrays.asList("a1", "a2", "a3", "a4"), episodeTitles(FeedItemFilter.HAS_MEDIA));
        assertEquals(Collections.singletonList("a5"), episodeTitles(FeedItemFilter.NO_MEDIA));
    }

    @Test
    public void favouriteFiltersSelectEpisodesByFavouriteMark() {
        assertEquals(Collections.singletonList("a3"), episodeTitles(FeedItemFilter.IS_FAVORITE));
        assertEquals(Arrays.asList("a1", "a2", "a4", "a5"), episodeTitles(FeedItemFilter.NOT_FAVORITE));
    }

    @Test
    public void historyFilterSelectsEpisodesWithCompletionDate() {
        assertEquals(Collections.singletonList("a3"), episodeTitles(FeedItemFilter.IS_IN_HISTORY));
    }

    @Test
    public void combinedFiltersMustAllMatch() {
        assertEquals(Collections.singletonList("a4"),
                episodeTitles(FeedItemFilter.UNPLAYED, FeedItemFilter.PAUSED, FeedItemFilter.NOT_QUEUED));
        assertTrue(episodeTitles(FeedItemFilter.PLAYED, FeedItemFilter.QUEUED).isEmpty());
    }

    @Test
    public void episodesSortByPublicationDate() {
        assertEquals(Arrays.asList("a5", "a4", "a3", "a2", "a1"),
                titles(DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD)));
        assertEquals(Arrays.asList("a1", "a2", "a3", "a4", "a5"),
                titles(DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(), SortOrder.DATE_OLD_NEW)));
    }

    @Test
    public void episodesSortByTitle() {
        assertEquals(Arrays.asList("a5", "a4", "a3", "a2", "a1"),
                titles(DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(), SortOrder.EPISODE_TITLE_Z_A)));
    }

    @Test
    public void episodesSortByDuration() {
        FeedItemFilter withMedia = new FeedItemFilter(FeedItemFilter.HAS_MEDIA);
        assertEquals(Arrays.asList("a1", "a2", "a3", "a4"),
                titles(DBReader.getEpisodes(0, 10, withMedia, SortOrder.DURATION_SHORT_LONG)));
        assertEquals(Arrays.asList("a4", "a3", "a2", "a1"),
                titles(DBReader.getEpisodes(0, 10, withMedia, SortOrder.DURATION_LONG_SHORT)));
    }

    @Test
    public void episodesSortBySize() {
        FeedItemFilter withMedia = new FeedItemFilter(FeedItemFilter.HAS_MEDIA);
        assertEquals(Arrays.asList("a1", "a2", "a3", "a4"),
                titles(DBReader.getEpisodes(0, 10, withMedia, SortOrder.SIZE_SMALL_LARGE)));
        assertEquals(Arrays.asList("a4", "a3", "a2", "a1"),
                titles(DBReader.getEpisodes(0, 10, withMedia, SortOrder.SIZE_LARGE_SMALL)));
    }

    @Test
    public void episodesSortByFileNameFollowsEpisodeLink() {
        assertEquals(Arrays.asList("a1", "a2", "a3", "a4", "a5"),
                titles(DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(), SortOrder.EPISODE_FILENAME_A_Z)));
        assertEquals(Arrays.asList("a5", "a4", "a3", "a2", "a1"),
                titles(DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(), SortOrder.EPISODE_FILENAME_Z_A)));
    }

    @Test
    public void episodesSortByCompletionDateListsMostRecentlyCompletedFirst() {
        FeedItemFilter history = new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY);
        await(DBWriter.addItemToPlaybackHistory(newItem.getMedia(), new Date(9000)));

        assertEquals(Arrays.asList("a1", "a3"),
                titles(DBReader.getEpisodes(0, 10, history, SortOrder.COMPLETION_DATE_NEW_OLD)));
    }

    @Test
    public void episodesWithoutSortOrderUseGlobalDefault() {
        assertEquals(Arrays.asList("a5", "a4", "a3", "a2", "a1"),
                titles(DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(), null)));

        UserPreferences.setPrefGlobalSortedOrder(SortOrder.EPISODE_TITLE_A_Z);

        assertEquals(Arrays.asList("a1", "a2", "a3", "a4", "a5"),
                titles(DBReader.getEpisodes(0, 10, FeedItemFilter.unfiltered(), SortOrder.GLOBAL_DEFAULT)));
    }

    @Test
    public void episodesRespectOffsetAndLimit() {
        assertEquals(Arrays.asList("a4", "a3"),
                titles(DBReader.getEpisodes(1, 2, FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD)));
    }

    @Test
    public void episodesKnowTheirFeed() {
        List<FeedItem> episodes = DBReader.getEpisodes(0, 10, new FeedItemFilter(FeedItemFilter.INCLUDE_ARCHIVED),
                SortOrder.DATE_NEW_OLD);

        assertEquals(beta.getId(), episodes.get(0).getFeed().getId());
        assertEquals("Beta", episodes.get(0).getFeed().getTitle());
    }

    @Test
    public void episodeCountsFollowTheFilter() {
        assertEquals(5, DBReader.getTotalEpisodeCount(FeedItemFilter.unfiltered()));
        assertEquals(7, DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES)));
        assertEquals(1, DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.NEW)));
        assertEquals(5, DBReader.getFeedEpisodeCount(alpha.getId(), FeedItemFilter.unfiltered()));
        assertEquals(1, DBReader.getFeedEpisodeCount(beta.getId(), FeedItemFilter.unfiltered()));
        assertEquals(1, DBReader.getFeedEpisodeCount(alpha.getId(), new FeedItemFilter(FeedItemFilter.PAUSED)));
        assertEquals(0, DBReader.getFeedEpisodeCount(alpha.getId() + 100, FeedItemFilter.unfiltered()));
    }

    @Test
    public void feedItemListAppliesFilterSortOffsetAndLimit() {
        List<FeedItem> items = DBReader.getFeedItemList(alpha, new FeedItemFilter(FeedItemFilter.HAS_MEDIA),
                SortOrder.DATE_NEW_OLD, 1, 2);

        assertEquals(Arrays.asList("a3", "a2"), titles(items));
        assertEquals(items, alpha.getItems());
        assertEquals(alpha, items.get(0).getFeed());
    }

    @Test
    public void feedItemListOfArchivedFeedIsNotRestrictedByFeedState() {
        assertEquals(Collections.singletonList("b1"),
                titles(DBReader.getFeedItemList(beta, FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD, 0, 10)));
    }

    @Test
    public void getFeedLoadsItemsAndAppliesFeedItemFilterOnRequest() {
        Feed all = DBReader.getFeed(alpha.getId(), false, 0, Integer.MAX_VALUE);
        assertEquals(5, all.getItems().size());
        assertEquals(alpha, all.getItems().get(0).getFeed());

        await(DBWriter.setFeedItemsFilter(alpha.getId(), Collections.singleton(FeedItemFilter.HAS_MEDIA)));
        Feed filtered = DBReader.getFeed(alpha.getId(), true, 0, Integer.MAX_VALUE);
        assertEquals(4, filtered.getItems().size());
        assertEquals(5, DBReader.getFeed(alpha.getId(), false, 0, Integer.MAX_VALUE).getItems().size());
    }

    @Test
    public void getFeedOfUnknownIdIsNull() {
        assertNull(DBReader.getFeed(alpha.getId() + 100, false, 0, 10));
    }

    @Test
    public void getFeedItemLoadsFeedAndMedia() {
        FeedItem item = DBReader.getFeedItem(favouriteDownloaded.getId());

        assertEquals("a3", item.getTitle());
        assertEquals(alpha, item.getFeed());
        assertEquals(favouriteDownloaded.getMedia().getId(), item.getMedia().getId());
        assertEquals("/downloads/a3.mp3", item.getMedia().getLocalFileUrl());
        assertTrue(item.isTagged(FeedItem.TAG_FAVORITE));
        assertTrue(DBReader.getFeedItem(queued.getId()).isTagged(FeedItem.TAG_QUEUE));
        assertNull(DBReader.getFeedItem(queued.getId() + 1000));
    }

    @Test
    public void feedItemByGuidPrefersGuidOverEpisodeUrl() {
        FeedItem byGuid = DBReader.getFeedItemByGuidOrEpisodeUrl("guid-a2", "https://example.com/unrelated.mp3");
        assertEquals(queued.getId(), byGuid.getId());
    }

    @Test
    public void feedItemByEpisodeUrlIsUsedWhenNoGuidIsGiven() {
        FeedItem byUrl = DBReader.getFeedItemByGuidOrEpisodeUrl(null, queued.getMedia().getDownloadUrl());
        assertEquals(queued.getId(), byUrl.getId());
        assertNull(DBReader.getFeedItemByGuidOrEpisodeUrl(null, "https://example.com/missing.mp3"));
        assertNull(DBReader.getFeedItemByGuidOrEpisodeUrl("missing-guid", "https://example.com/missing.mp3"));
    }

    @Test
    public void feedItemsWithUrlAreOrderedByLastPlayback() {
        List<FeedItem> items = DBReader.getFeedItemsWithUrl(Arrays.asList(
                favouriteDownloaded.getMedia().getDownloadUrl(), queued.getMedia().getDownloadUrl(),
                "https://example.com/it's-unknown.mp3"));

        assertEquals(Arrays.asList("a3", "a2"), titles(items));
        assertEquals(alpha, items.get(0).getFeed());
    }

    @Test
    public void feedItemsWithTooManyUrlsAreRejected() {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < 801; i++) {
            urls.add("https://example.com/" + i + ".mp3");
        }

        assertThrows(IllegalArgumentException.class, () -> DBReader.getFeedItemsWithUrl(urls));
    }

    @Test
    public void feedMediaIsLoadedByMediaId() {
        assertEquals(paused.getMedia().getId(), DBReader.getFeedMedia(paused.getMedia().getId()).getId());
        assertEquals(500, DBReader.getFeedMedia(paused.getMedia().getId()).getPosition());
        assertEquals("a4", DBReader.getFeedMedia(paused.getMedia().getId()).getItem().getTitle());
        assertNull(DBReader.getFeedMedia(paused.getMedia().getId() + 1000));
    }

    @Test
    public void descriptionIsOnlyLoadedOnDemand() {
        Feed feed = storeFeed("Described");
        FeedItem item = new FeedItem(0, "described", "guid", "link", new Date(10), FeedItem.UNPLAYED, feed);
        item.setDescriptionIfLonger("A long description of the episode");
        await(DBWriter.setFeedItem(item, false));

        FeedItem loaded = DBReader.getFeedItem(item.getId());
        assertNull(loaded.getDescription());

        DBReader.loadDescriptionOfFeedItem(loaded);

        assertEquals("A long description of the episode", loaded.getDescription());
    }

    @Test
    public void feedDataIsAttachedToLoadedItemsAndUnknownFeedsGetPlaceholder() {
        FeedItem known = new FeedItem();
        known.setFeedId(alpha.getId());
        FeedItem orphan = new FeedItem();
        orphan.setFeedId(alpha.getId() + 1000);

        DBReader.loadFeedDataOfFeedItemList(Arrays.asList(known, orphan));

        assertEquals("Alpha", known.getFeed().getTitle());
        assertEquals("Error: Item without feed", orphan.getFeed().getTitle());
    }

    @Test
    public void feedListIsSortedAlphabeticallyIgnoringCase() {
        storeFeed("aardvark");

        List<Feed> feeds = DBReader.getFeedList();

        assertEquals(Arrays.asList("aardvark", "Alpha", "Beta", "Gamma"),
                Arrays.asList(feeds.get(0).getTitle(), feeds.get(1).getTitle(), feeds.get(2).getTitle(),
                        feeds.get(3).getTitle()));
    }

    @Test
    public void feedDownloadUrlsCanBeRestrictedToSubscribedFeedsAndSkipLocalFeeds() {
        Feed local = new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://folder", null, "Local");
        local.setItems(new ArrayList<>());
        await(DBWriter.setCompleteFeed(local));

        List<String> subscribed = DBReader.getFeedListDownloadUrls(true);
        List<String> all = DBReader.getFeedListDownloadUrls(false);

        assertEquals(Collections.singletonList(alpha.getDownloadUrl()), subscribed);
        assertEquals(Arrays.asList(alpha.getDownloadUrl(), beta.getDownloadUrl(), gamma.getDownloadUrl()), all);
    }

    @Test
    public void autoDownloadCandidatesAreNewEpisodesOfFeedsAllowedToDownload() {
        assertEquals(Collections.singletonList("a1"), titles(DBReader.getAutoDownloadCandidates(true, false)));
        assertTrue(DBReader.getAutoDownloadCandidates(false, false).isEmpty());

        setAutoDownload(alpha, FeedPreferences.AutoDownloadSetting.ENABLED);
        assertEquals(Collections.singletonList("a1"), titles(DBReader.getAutoDownloadCandidates(false, false)));

        setAutoDownload(alpha, FeedPreferences.AutoDownloadSetting.DISABLED);
        assertTrue(DBReader.getAutoDownloadCandidates(true, false).isEmpty());
    }

    @Test
    public void autoDownloadCandidatesCanIncludeQueuedEpisodes() {
        assertEquals(Arrays.asList("a2", "a1"), titles(DBReader.getAutoDownloadCandidates(true, true)));
    }

    @Test
    public void autoDownloadCandidatesExcludeEpisodesWithAutoDownloadDisabled() {
        newItem.disableAutoDownload();
        await(DBWriter.setFeedItem(newItem, false));

        assertTrue(DBReader.getAutoDownloadCandidates(true, false).isEmpty());
    }

    @Test
    public void autoDownloadCandidatesExcludeAlreadyDownloadedAndLocalEpisodes() {
        newItem.getMedia().setLocalFileUrl("/downloads/a1.mp3");
        newItem.getMedia().setDownloaded(true, 5);
        await(DBWriter.setFeedMedia(newItem.getMedia()));
        assertTrue(DBReader.getAutoDownloadCandidates(true, false).isEmpty());

        Feed local = new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://folder", null, "Local");
        local.setItems(new ArrayList<>());
        await(DBWriter.setCompleteFeed(local));
        storeItem(local, "local new", 100, FeedItem.NEW);
        assertTrue(DBReader.getAutoDownloadCandidates(true, false).isEmpty());
    }

    @Test
    public void randomEpisodesContainOnlyUnplayedSubscribedEpisodesWithOnePerFeed() {
        Feed other = storeFeed("Other");
        long recent = System.currentTimeMillis() - 1000;
        for (Feed feed : Arrays.asList(alpha, other)) {
            storeItem(feed, feed.getTitle() + " fresh", recent, FeedItem.UNPLAYED);
        }

        List<FeedItem> random = DBReader.getRandomEpisodes(10, 7);

        assertEquals(2, random.size());
        List<Long> feedIds = new ArrayList<>();
        for (FeedItem item : random) {
            assertTrue(item.getPlayState() != FeedItem.PLAYED);
            assertNotNull(item.getFeed());
            feedIds.add(item.getFeed().getId());
        }
        assertTrue(feedIds.containsAll(Arrays.asList(alpha.getId(), other.getId())));
        assertEquals(titles(random), titles(DBReader.getRandomEpisodes(10, 7)));
        assertEquals(1, DBReader.getRandomEpisodes(1, 7).size());
    }

    private void setAutoDownload(Feed feed, FeedPreferences.AutoDownloadSetting setting) {
        Feed stored = DBReader.getFeed(feed.getId(), false, 0, 1);
        stored.getPreferences().setAutoDownload(setting);
        await(DBWriter.setFeedPreferences(stored.getPreferences()));
    }
}
