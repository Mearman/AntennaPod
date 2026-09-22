package de.test.antennapod.storage.database;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedOrder;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.feed.SubscriptionsFilter;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateManagerImpl;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.NavDrawerData;
import de.danoeh.antennapod.storage.database.NonSubscribedFeedsCleaner;
import de.test.antennapod.service.download.DownloadTestFixture;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FeedManagementDatabaseTest {
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);
    private static final String NO_EPISODE_URL = "http://example.com/no-such-episode.mp3";

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
    }

    @After
    public void tearDown() throws Exception {
        WorkManager.getInstance(context).cancelAllWorkByTag(FeedUpdateManagerImpl.WORK_TAG_FEED_UPDATE);
        fixture.tearDown();
    }

    private Feed reload(Feed feed) {
        return DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
    }

    private List<String> titles(List<Feed> feeds) {
        List<String> titles = new ArrayList<>();
        for (Feed feed : feeds) {
            titles.add(feed.getTitle());
        }
        return titles;
    }

    private Feed subscribeNotSubscribed(String title, long lastRefreshAttempt, int episodes) throws Exception {
        Feed feed = fixture.newFeed(title, episodes);
        feed.setDownloadUrl(fixture.hostFeed(feed));
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
        feed.setLastRefreshAttempt(lastRefreshAttempt);
        return fixture.subscribe(feed);
    }

    @Test
    public void deletingAFeedRemovesEverythingBelongingToIt() throws Exception {
        Feed feed = fixture.subscribe("Doomed", 3);
        Feed survivor = fixture.subscribe("Survivor", 1);
        fixture.markDownloaded(feed.getItemAtIndex(0));
        File downloaded = new File(DBReader.getFeedMedia(feed.getItemAtIndex(0).getMedia().getId()).getLocalFileUrl());
        DBWriter.addQueueItem(context, feed.getItemAtIndex(1), survivor.getItemAtIndex(0)).get();
        DBWriter.addFavoriteItems(Collections.singletonList(feed.getItemAtIndex(2))).get();
        DBWriter.addDownloadStatus(new DownloadResult(0, "Doomed", feed.getId(), Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, new Date(), null)).get();

        DBWriter.deleteFeed(context, feed.getId()).get();

        assertNull(reload(feed));
        assertFalse(downloaded.exists());
        assertEquals(1, DBReader.getQueue().size());
        assertEquals(survivor.getItemAtIndex(0).getId(), DBReader.getQueue().get(0).getId());
        assertEquals(0, DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.IS_FAVORITE)));
        assertEquals(1, DBReader.getFeedList().size());
        assertNotNull(reload(survivor));
    }

    @Test
    public void deletingAFeedThatDoesNotExistChangesNothing() throws Exception {
        fixture.subscribe("Kept", 1);

        DBWriter.deleteFeed(context, 12345).get();

        assertEquals(1, DBReader.getFeedList().size());
    }

    @Test
    public void feedCanBeFoundAndDeletedByItsDownloadUrl() throws Exception {
        Feed feed = fixture.subscribe("By url", 1);
        fixture.subscribe("Other", 1);

        DBWriter.removeFeedWithDownloadUrl(context, "http://example.com/unknown");
        assertEquals(2, DBReader.getFeedList().size());
        DBWriter.removeFeedWithDownloadUrl(context, feed.getDownloadUrl());

        assertEquals(1, DBReader.getFeedList().size());
        assertEquals("Other", DBReader.getFeedList().get(0).getTitle());
    }

    @Test
    public void downloadUrlsOfSubscribedFeedsCanBeListed() throws Exception {
        Feed subscribed = fixture.subscribe("Subscribed", 1);
        Feed other = subscribeNotSubscribed("Not subscribed", System.currentTimeMillis(), 1);

        List<String> subscribedOnly = DBReader.getFeedListDownloadUrls(true);
        List<String> all = DBReader.getFeedListDownloadUrls(false);

        assertEquals(Collections.singletonList(subscribed.getDownloadUrl()), subscribedOnly);
        assertEquals(2, all.size());
        assertTrue(all.contains(other.getDownloadUrl()));
    }

    @Test
    public void downloadUrlOfAFeedCanBeChanged() throws Exception {
        Feed feed = fixture.subscribe("Moved", 1);

        DBWriter.updateFeedDownloadURL(feed.getDownloadUrl(), "http://example.com/new.xml").get();

        assertEquals("http://example.com/new.xml", reload(feed).getDownloadUrl());
    }

    @Test
    public void feedStateCanBeChangedBetweenSubscribedArchivedAndNotSubscribed() throws Exception {
        Feed feed = fixture.subscribe("States", 2);

        DBWriter.setFeedState(context, feed, Feed.STATE_ARCHIVED).get();
        assertEquals(Feed.STATE_ARCHIVED, reload(feed).getState());

        DBWriter.setFeedState(context, reload(feed), Feed.STATE_NOT_SUBSCRIBED).get();
        assertEquals(Feed.STATE_NOT_SUBSCRIBED, reload(feed).getState());
        assertEquals(0, DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE,
                Feed.STATE_SUBSCRIBED).feeds.size());
    }

    @Test
    public void subscribingToAFeedAgainEnablesItsRefreshes() throws Exception {
        Feed feed = subscribeNotSubscribed("Resubscribed", System.currentTimeMillis(), 2);
        feed.getPreferences().setKeepUpdated(false);
        DBWriter.setFeedPreferences(feed.getPreferences()).get();
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(reload(feed).getItemAtIndex(0)))
                .get();

        UserPreferences.setAllowMobileFeedRefresh(true);
        DBWriter.setFeedState(context, reload(feed), Feed.STATE_SUBSCRIBED).get();

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> reload(feed).getPreferences().getKeepUpdated());
        assertEquals(Feed.STATE_SUBSCRIBED, reload(feed).getState());
    }

    @Test
    public void customTitleReplacesTheFeedTitle() throws Exception {
        Feed feed = fixture.subscribe("Original", 1);
        feed.setCustomTitle("My title");

        DBWriter.setFeedCustomTitle(feed).get();

        assertEquals("My title", reload(feed).getTitle());
        assertEquals("Original", reload(feed).getFeedTitle());
    }

    @Test
    public void feedPreferencesAreStored() throws Exception {
        Feed feed = fixture.subscribe("Preferences", 1);
        FeedPreferences preferences = feed.getPreferences();
        preferences.setUsername("user");
        preferences.setPassword("password");
        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED);
        preferences.setAutoDeleteAction(FeedPreferences.AutoDeleteAction.ALWAYS);
        preferences.setVolumeAdaptionSetting(VolumeAdaptionSetting.HEAVY_REDUCTION);
        preferences.setNewEpisodesAction(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE);
        preferences.setFeedPlaybackSpeed(1.5f);
        preferences.setFeedSkipIntro(15);
        preferences.setFeedSkipEnding(20);
        preferences.setFeedSkipSilence(FeedPreferences.SkipSilence.AGGRESSIVE);
        preferences.setShowEpisodeNotification(true);
        preferences.setKeepUpdated(false);
        preferences.setFilter(new FeedFilter("include", "exclude", 60));
        preferences.getTags().add("News");
        preferences.getTags().add("Daily");

        DBWriter.setFeedPreferences(preferences).get();

        FeedPreferences loaded = reload(feed).getPreferences();
        assertEquals("user", loaded.getUsername());
        assertEquals("password", loaded.getPassword());
        assertEquals(FeedPreferences.AutoDownloadSetting.ENABLED, loaded.getAutoDownload());
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, loaded.getAutoDeleteAction());
        assertEquals(VolumeAdaptionSetting.HEAVY_REDUCTION, loaded.getVolumeAdaptionSetting());
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, loaded.getNewEpisodesAction());
        assertEquals(1.5f, loaded.getFeedPlaybackSpeed(), 0.001f);
        assertEquals(15, loaded.getFeedSkipIntro());
        assertEquals(20, loaded.getFeedSkipEnding());
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, loaded.getFeedSkipSilence());
        assertTrue(loaded.getShowEpisodeNotification());
        assertFalse(loaded.getKeepUpdated());
        assertEquals("include", loaded.getFilter().getIncludeFilterRaw());
        assertEquals("exclude", loaded.getFilter().getExcludeFilterRaw());
        assertEquals(60, loaded.getFilter().getMinimalDurationFilter());
        assertEquals(new HashSet<>(Arrays.asList("News", "Daily", FeedPreferences.TAG_ROOT)), loaded.getTags());
    }

    @Test
    public void feedPreferencesWithoutAFeedAreRejected() throws Exception {
        Feed feed = fixture.subscribe("Rejected", 1);
        FeedPreferences preferences = feed.getPreferences();
        preferences.setFeedID(0);
        boolean rejected = false;

        try {
            DBWriter.setFeedPreferences(preferences).get();
        } catch (ExecutionException e) {
            rejected = e.getCause() instanceof IllegalArgumentException;
        }

        assertTrue(rejected);
    }

    @Test
    public void tagsAreCollectedAcrossFeeds() throws Exception {
        Feed news = fixture.subscribe("News feed", 1);
        Feed music = fixture.subscribe("Music feed", 1);
        fixture.subscribe("Untagged", 1);
        news.getPreferences().getTags().add("News");
        music.getPreferences().getTags().add("Music");
        music.getPreferences().getTags().add("News");
        DBWriter.setFeedPreferences(news.getPreferences()).get();
        DBWriter.setFeedPreferences(music.getPreferences()).get();

        List<NavDrawerData.TagItem> tags = DBReader.getAllTags(Feed.STATE_SUBSCRIBED);

        assertEquals(FeedPreferences.TAG_ROOT, tags.get(0).getTitle());
        assertEquals(3, tags.get(0).getFeeds().size());
        assertEquals("Music", tags.get(1).getTitle());
        assertEquals("News", tags.get(2).getTitle());
        assertEquals(2, tags.get(2).getFeeds().size());
        assertEquals(FeedPreferences.TAG_UNTAGGED, tags.get(3).getTitle());
        assertEquals(1, tags.get(3).getFeeds().size());
    }

    private void createCounterFeeds() throws Exception {
        Feed busy = fixture.subscribe("Busy", 3, FeedItem.NEW);
        Feed calm = fixture.subscribe("Calm", 2, FeedItem.UNPLAYED);
        Feed done = fixture.subscribe("Done", 2, FeedItem.PLAYED);
        fixture.markDownloaded(calm.getItemAtIndex(0));
        fixture.markDownloaded(done.getItemAtIndex(0));
        DBWriter.addQueueItem(context, calm.getItemAtIndex(1)).get();
        assertEquals(3, busy.getItems().size());
    }

    private List<String> drawerOrder(FeedOrder order, FeedCounter counter) {
        return titles(DBReader.getNavDrawerData(null, order, counter, Feed.STATE_SUBSCRIBED).feeds);
    }

    private Feed subscribeWithNewestEpisodeAt(String title, int episodes, int playState, long newestMillis)
            throws Exception {
        Feed hosted = fixture.newFeed(title, episodes, playState);
        for (int i = 0; i < episodes; i++) {
            hosted.getItemAtIndex(i).setPubDate(new Date(newestMillis - i * DAY_MILLIS));
        }
        hosted.setDownloadUrl(fixture.hostFeed(hosted));
        return fixture.subscribe(hosted);
    }

    @Test
    public void navigationDrawerOrdersFeedsByTheChosenCriterion() throws Exception {
        long now = System.currentTimeMillis();
        subscribeWithNewestEpisodeAt("Alpha", 1, FeedItem.NEW, now - 10 * DAY_MILLIS);
        subscribeWithNewestEpisodeAt("Bravo", 2, FeedItem.PLAYED, now);
        Feed charlie = subscribeWithNewestEpisodeAt("Charlie", 3, FeedItem.NEW, now - 20 * DAY_MILLIS);
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(charlie.getItemAtIndex(0))).get();

        assertEquals(Arrays.asList("Alpha", "Bravo", "Charlie"),
                drawerOrder(FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE));
        assertEquals(Arrays.asList("Charlie", "Alpha", "Bravo"), drawerOrder(FeedOrder.COUNTER, FeedCounter.SHOW_NEW));
        assertEquals(Arrays.asList("Bravo", "Charlie", "Alpha"),
                drawerOrder(FeedOrder.MOST_PLAYED, FeedCounter.SHOW_NONE));
        assertEquals(Arrays.asList("Bravo", "Alpha", "Charlie"),
                drawerOrder(FeedOrder.MOST_RECENT_EPISODE, FeedCounter.SHOW_NONE));
    }

    @Test
    public void navigationDrawerCountsEpisodesAccordingToTheCounterSetting() throws Exception {
        createCounterFeeds();

        NavDrawerData data = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NEW,
                Feed.STATE_SUBSCRIBED);
        assertEquals(3, data.numNewItems);
        assertEquals(2, data.numDownloadedItems);
        assertEquals(1, data.queueSize);
        long busyId = data.feeds.get(0).getId();
        assertEquals(3, (int) data.feedCounters.get(busyId));

        NavDrawerData unplayed = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL,
                FeedCounter.SHOW_UNPLAYED, Feed.STATE_SUBSCRIBED);
        assertEquals(2, (int) unplayed.feedCounters.get(unplayed.feeds.get(1).getId()));

        NavDrawerData downloaded = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL,
                FeedCounter.SHOW_DOWNLOADED, Feed.STATE_SUBSCRIBED);
        assertEquals(1, (int) downloaded.feedCounters.get(downloaded.feeds.get(2).getId()));

        NavDrawerData downloadedUnplayed = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL,
                FeedCounter.SHOW_DOWNLOADED_UNPLAYED, Feed.STATE_SUBSCRIBED);
        assertEquals(1, (int) downloadedUnplayed.feedCounters.get(downloadedUnplayed.feeds.get(1).getId()));
        assertNull(downloadedUnplayed.feedCounters.get(downloadedUnplayed.feeds.get(2).getId()));

        NavDrawerData none = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE,
                Feed.STATE_SUBSCRIBED);
        assertTrue(none.feedCounters.isEmpty());
    }

    @Test
    public void navigationDrawerFiltersFeedsBySettings() throws Exception {
        createCounterFeeds();
        Feed busy = null;
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getTitle().equals("Busy")) {
                busy = feed;
            }
        }
        busy.getPreferences().setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED);
        busy.getPreferences().setShowEpisodeNotification(true);
        busy.getPreferences().setKeepUpdated(false);
        DBWriter.setFeedPreferences(busy.getPreferences()).get();

        assertEquals(Collections.singletonList("Busy"), filteredDrawer(SubscriptionsFilter.ENABLED_AUTO_DOWNLOAD));
        assertEquals(2, filteredDrawer(SubscriptionsFilter.DISABLED_AUTO_DOWNLOAD).size());
        assertEquals(Collections.singletonList("Busy"), filteredDrawer(SubscriptionsFilter.DISABLED_UPDATES));
        assertEquals(2, filteredDrawer(SubscriptionsFilter.ENABLED_UPDATES).size());
        assertEquals(Collections.singletonList("Busy"),
                filteredDrawer(SubscriptionsFilter.EPISODE_NOTIFICATION_ENABLED));
        assertEquals(2, filteredDrawer(SubscriptionsFilter.EPISODE_NOTIFICATION_DISABLED).size());
        assertEquals(Collections.singletonList("Busy"), filteredDrawer(SubscriptionsFilter.COUNTER_GREATER_ZERO));
    }

    private List<String> filteredDrawer(String filter) {
        return titles(DBReader.getNavDrawerData(new SubscriptionsFilter(filter), FeedOrder.ALPHABETICAL,
                FeedCounter.SHOW_NEW, Feed.STATE_SUBSCRIBED).feeds);
    }

    @Test
    public void navigationDrawerHidesFeedsThatAreNotSubscribedUnlessAsked() throws Exception {
        fixture.subscribe("Subscribed", 1);
        subscribeNotSubscribed("Visitor", System.currentTimeMillis(), 1);

        assertEquals(Collections.singletonList("Subscribed"), titles(DBReader.getNavDrawerData(
                new SubscriptionsFilter(""), FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NONE,
                Feed.STATE_SUBSCRIBED).feeds));
        assertEquals(Collections.singletonList("Visitor"), titles(DBReader.getNavDrawerData(
                new SubscriptionsFilter(SubscriptionsFilter.SHOW_NON_SUBSCRIBED_FEEDS), FeedOrder.ALPHABETICAL,
                FeedCounter.SHOW_NONE, Feed.STATE_NOT_SUBSCRIBED).feeds));
    }

    @Test
    public void navigationDrawerGroupsFeedsByTag() throws Exception {
        Feed tagged = fixture.subscribe("Tagged", 2, FeedItem.NEW);
        fixture.subscribe("Plain", 1);
        tagged.getPreferences().getTags().add("Favourites");
        DBWriter.setFeedPreferences(tagged.getPreferences()).get();

        NavDrawerData data = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NEW,
                Feed.STATE_SUBSCRIBED);

        assertEquals(FeedPreferences.TAG_UNTAGGED, data.tags.get(0).getTitle());
        assertEquals(1, data.tags.get(0).getFeeds().size());
        NavDrawerData.TagItem favourites = null;
        for (NavDrawerData.TagItem tag : data.tags) {
            if (tag.getTitle().equals("Favourites")) {
                favourites = tag;
            }
        }
        assertNotNull(favourites);
        assertEquals(1, favourites.getFeeds().size());
        assertEquals(2, favourites.getCounter());
    }

    @Test
    public void itemFilterAndSortOrderOfAFeedArePersisted() throws Exception {
        Feed feed = fixture.subscribe("Filtered feed", 4);
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Arrays.asList(feed.getItemAtIndex(0),
                feed.getItemAtIndex(1))).get();
        Set<String> filter = new HashSet<>(Collections.singletonList(FeedItemFilter.UNPLAYED));

        DBWriter.setFeedItemsFilter(feed.getId(), filter).get();
        DBWriter.setFeedItemSortOrder(feed.getId(), SortOrder.EPISODE_TITLE_Z_A).get();

        Feed loaded = DBReader.getFeed(feed.getId(), true, 0, Integer.MAX_VALUE);
        assertEquals(SortOrder.EPISODE_TITLE_Z_A, loaded.getSortOrder());
        assertEquals(2, loaded.getItems().size());
        assertEquals("Filtered feed episode 3", loaded.getItems().get(0).getTitle());
        assertEquals(2, DBReader.getFeedEpisodeCount(feed.getId(), new FeedItemFilter(FeedItemFilter.UNPLAYED)));
        assertEquals(4, reload(feed).getItems().size());

        DBWriter.setFeedItemSortOrder(feed.getId(), null).get();
        assertNull(reload(feed).getSortOrder());
    }

    @Test
    public void pagedFeedCanBeResetToItsFirstPage() throws Exception {
        Feed feed = fixture.newFeed("Paged", 1);
        feed.setDownloadUrl("http://example.com/page1.xml");
        feed.setPaged(true);
        feed.setNextPageLink("http://example.com/page2.xml");
        Feed saved = fixture.subscribe(feed);
        assertEquals("http://example.com/page2.xml", reload(saved).getNextPageLink());

        DBWriter.resetPagedFeedPage(saved).get();

        assertEquals("http://example.com/page1.xml", reload(saved).getNextPageLink());
    }

    @Test
    public void lastUpdateFailureIsStored() throws Exception {
        Feed feed = fixture.subscribe("Failing", 1);

        DBWriter.setFeedLastUpdateFailed(feed.getId(), true).get();
        assertTrue(reload(feed).hasLastUpdateFailed());

        DBWriter.setFeedLastUpdateFailed(feed.getId(), false).get();
        assertFalse(reload(feed).hasLastUpdateFailed());
    }

    @Test
    public void unsubscribedFeedsAreCleanedUpWhenNobodyUsesThem() throws Exception {
        long now = System.currentTimeMillis();
        subscribeNotSubscribed("Recent", now, 1);
        subscribeNotSubscribed("Expired", now - 200 * DAY_MILLIS, 1);
        Feed queued = subscribeNotSubscribed("Expired but queued", now - 200 * DAY_MILLIS, 1);
        Feed playedRecently = subscribeNotSubscribed("Played recently", now - 10 * DAY_MILLIS, 1);
        Feed playedLongAgo = subscribeNotSubscribed("Played long ago", now - 100 * DAY_MILLIS, 1);
        fixture.subscribe("Subscribed", 1);
        DBWriter.addQueueItem(context, reload(queued).getItemAtIndex(0)).get();
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false,
                Collections.singletonList(reload(playedRecently).getItemAtIndex(0))).get();
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false,
                Collections.singletonList(reload(playedLongAgo).getItemAtIndex(0))).get();

        NonSubscribedFeedsCleaner.deleteOldNonSubscribedFeeds(context);

        List<String> remaining = titles(DBReader.getFeedList());
        assertEquals(4, remaining.size());
        assertTrue(remaining.containsAll(Arrays.asList("Recent", "Expired but queued", "Played recently",
                "Subscribed")));
    }

    @Test
    public void refreshRemovesEpisodesThatDisappearedFromTheFeed() throws Exception {
        Feed feed = fixture.subscribe("Shrinking", 3);
        Feed refreshed = fixture.newFeed("Shrinking", 2);
        refreshed.setId(feed.getId());
        refreshed.setDownloadUrl(feed.getDownloadUrl());

        FeedDatabaseWriter.updateFeed(context, refreshed, true);

        assertEquals(2, reload(feed).getItems().size());
        assertNull(DBReader.getFeedItemByGuidOrEpisodeUrl("Shrinking-episode-2", NO_EPISODE_URL));
    }

    @Test
    public void refreshKeepsUnlistedEpisodesWhenTheListIsNotComplete() throws Exception {
        Feed feed = fixture.subscribe("Paged list", 3);
        Feed refreshed = fixture.newFeed("Paged list", 2);
        refreshed.setId(feed.getId());
        refreshed.setDownloadUrl(feed.getDownloadUrl());

        FeedDatabaseWriter.updateFeed(context, refreshed, false);

        assertEquals(3, reload(feed).getItems().size());
    }

    @Test
    public void newEpisodesAreQueuedWhenTheFeedIsSetToQueueThem() throws Exception {
        Feed feed = fixture.newFeed("Queueing", 3);
        feed.setDownloadUrl(fixture.hostFeed(feed));
        feed.getItems().remove(0);
        Feed saved = fixture.subscribe(feed);
        saved.getPreferences().setNewEpisodesAction(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE);
        DBWriter.setFeedPreferences(saved.getPreferences()).get();
        Feed refreshed = fixture.newFeed("Queueing", 3);
        refreshed.setId(saved.getId());
        refreshed.setDownloadUrl(saved.getDownloadUrl());

        FeedDatabaseWriter.updateFeed(context, refreshed, false);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> DBReader.getQueue().size() == 1);
        assertEquals("Queueing episode 0", DBReader.getQueue().get(0).getTitle());
    }

    @Test
    public void episodesThatTheFeedRepublishedUnderANewIdentifierAreRepaired() throws Exception {
        Feed feed = fixture.subscribe("Republished", 2);
        FeedItem original = feed.getItemAtIndex(0);
        Feed refreshed = new Feed(0, null, "Republished", "http://example.com/Republished", "Description", null,
                "Author", "en", Feed.TYPE_RSS2, "Republished-identifier", null, null, feed.getDownloadUrl(), 0);
        refreshed.setId(feed.getId());
        FeedItem replacement = new FeedItem(0, original.getTitle(), "brand-new-identifier", original.getLink(),
                original.getPubDate(), FeedItem.UNPLAYED, refreshed);
        replacement.setMedia(new FeedMedia(replacement, "http://example.com/other-file.mp3", 0,
                original.getMedia().getMimeType()));
        refreshed.setItems(new ArrayList<>(Collections.singletonList(replacement)));

        FeedDatabaseWriter.updateFeed(context, refreshed, false);

        assertEquals(2, reload(feed).getItems().size());
        assertNotNull(DBReader.getFeedItemByGuidOrEpisodeUrl("brand-new-identifier", NO_EPISODE_URL));
        assertNull(DBReader.getFeedItemByGuidOrEpisodeUrl("Republished-episode-0", NO_EPISODE_URL));
        boolean logged = false;
        for (DownloadResult result : DBReader.getFeedDownloadLog(feed.getId(), Integer.MAX_VALUE)) {
            logged |= result.getReason() == DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE;
        }
        assertTrue(logged);
    }

    @Test
    public void episodesThatAppearTwiceInTheFeedAreOnlyAddedOnce() throws Exception {
        Feed feed = fixture.subscribe("Doubled", 1);
        Feed refreshed = new Feed(0, null, "Doubled", "http://example.com/Doubled", "Description", null, "Author",
                "en", Feed.TYPE_RSS2, "Doubled-identifier", null, null, feed.getDownloadUrl(), 0);
        refreshed.setId(feed.getId());
        Date published = new Date();
        List<FeedItem> items = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            FeedItem item = new FeedItem(0, "Same episode", "identifier-" + i, "http://example.com/" + i, published,
                    FeedItem.UNPLAYED, refreshed);
            item.setMedia(new FeedMedia(item, "http://example.com/file-" + i + ".mp3", 0, "audio/mpeg"));
            items.add(item);
        }
        refreshed.setItems(items);

        FeedDatabaseWriter.updateFeed(context, refreshed, false);

        assertEquals(2, reload(feed).getItems().size());
        boolean logged = false;
        for (DownloadResult result : DBReader.getFeedDownloadLog(feed.getId(), Integer.MAX_VALUE)) {
            logged |= result.getReason() == DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE;
        }
        assertTrue(logged);
    }
}
