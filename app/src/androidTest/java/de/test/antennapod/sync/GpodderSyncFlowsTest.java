package de.test.antennapod.sync;

import android.content.Intent;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.SyncServiceEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.ui.UITestUtils;
import de.test.antennapod.util.sync.GpodderTestServer;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.hamcrest.Matcher;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickBottomNavOverflow;
import static de.test.antennapod.EspressoTestUtils.clickPreference;
import static de.test.antennapod.EspressoTestUtils.waitForView;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class GpodderSyncFlowsTest {
    private static final long SYNC_WAIT_SECONDS = 120;

    private GpodderTestServer server;
    private UITestUtils uiTestUtils;
    private volatile int lastSyncEventMessage = -1;

    @Rule
    public ActivityTestRule<PreferenceActivity> preferenceActivityRule =
            new ActivityTestRule<>(PreferenceActivity.class, false, false);

    @Rule
    public ActivityTestRule<MainActivity> mainActivityRule =
            new ActivityTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.cancelPendingSyncWork();
        EspressoTestUtils.clearDatabase();
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.enableSyncOverAnyConnection();
        server = new GpodderTestServer();
        server.start();
        uiTestUtils = new UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext());
        uiTestUtils.setup();
        EventBus.getDefault().removeStickyEvent(SyncServiceEvent.class);
        EventBus.getDefault().register(this);
        lastSyncEventMessage = -1;
    }

    @After
    public void tearDown() throws Exception {
        EspressoTestUtils.cancelPendingSyncWork();
        EventBus.getDefault().unregister(this);
        preferenceActivityRule.finishActivity();
        mainActivityRule.finishActivity();
        SynchronizationSettings.setSelectedSyncProvider(null);
        SynchronizationCredentials.clear();
        uiTestUtils.tearDown();
        server.stop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onSyncServiceEvent(SyncServiceEvent event) {
        lastSyncEventMessage = event.getMessageResId();
    }

    private void connectProvider() {
        SynchronizationSettings.resetTimestamps();
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.getIdentifier());
        SynchronizationCredentials.setHosturl(server.getBaseUrl());
        SynchronizationCredentials.setUsername(GpodderTestServer.USERNAME);
        SynchronizationCredentials.setPassword(GpodderTestServer.PASSWORD);
        SynchronizationCredentials.setDeviceId("device1");
    }

    private void markAllFeedsRefreshed() {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        for (Feed feed : DBReader.getFeedList()) {
            adapter.setFeedLastUpdateFailed(feed.getId(), false);
        }
        adapter.close();
    }

    private void giveMediaADuration() {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        for (Feed feed : DBReader.getFeedList()) {
            for (FeedItem item : DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                    SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE)) {
                if (item.getMedia() == null || item.getMedia().getId() == 0) {
                    continue;
                }
                FeedMedia media = item.getMedia();
                media.setDuration(300000);
                if (media.getLastPlayedTimeHistory() == null) {
                    media.setLastPlayedTimeHistory(new Date(0));
                }
                adapter.setFeedMediaPlaybackInformation(media);
            }
        }
        adapter.close();
    }

    private boolean syncScreenOpen = false;

    private void clickSyncNow() {
        if (syncScreenOpen) {
            pressBack();
        }
        lastSyncEventMessage = -1;
        clickPreference(R.string.synchronization_pref);
        clickPreference(R.string.synchronization_sync_changes_title);
        syncScreenOpen = true;
    }

    private void waitForSyncToFinish() {
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> lastSyncEventMessage == R.string.sync_status_success
                        || lastSyncEventMessage == R.string.sync_status_error);
    }

    private boolean feedUrlExists(String downloadUrl) {
        for (Feed feed : DBReader.getFeedList()) {
            if (downloadUrl.equals(feed.getDownloadUrl())) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testFirstSyncUploadsAllLocalSubscriptions() throws Exception {
        connectProvider();
        uiTestUtils.addLocalFeedData(false);
        markAllFeedsRefreshed();

        preferenceActivityRule.launchActivity(new Intent());
        clickSyncNow();
        waitForSyncToFinish();

        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> server.uploadedAddedFeeds.size() >= uiTestUtils.hostedFeeds.size());
        for (Feed feed : uiTestUtils.hostedFeeds) {
            assertTrue(server.uploadedAddedFeeds.contains(feed.getDownloadUrl()));
        }
        assertTrue(SynchronizationSettings.isLastSyncSuccessful());
        assertTrue(server.hasRequest("GET", "/api/2/subscriptions/"));
        assertTrue(server.hasRequest("GET", "/api/2/episodes/"));
        assertTrue(SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp() > 0);
        assertTrue(SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp() > 0);
    }

    @Test
    public void testSubscriptionChangesFromServerAreAppliedBothWays() throws Exception {
        connectProvider();
        uiTestUtils.addLocalFeedData(false);
        markAllFeedsRefreshed();
        Feed extraFeed = new Feed(0, null, "Synced feed title", "http://example.com/syncedfeed",
                "Description", "http://example.com/pay", "author", "en", Feed.TYPE_RSS2,
                "syncedfeed", null, null, "http://example.com/synced/src", System.currentTimeMillis());
        List<FeedItem> extraItems = new ArrayList<>();
        for (int j = 0; j < 2; j++) {
            extraItems.add(new FeedItem(0, "Synced feed item " + j, "synceditem" + j,
                    "http://example.com/syncedfeed/item/" + j, new Date(), FeedItem.UNPLAYED,
                    extraFeed));
        }
        extraFeed.setItems(extraItems);
        String extraUrl = uiTestUtils.hostFeed(extraFeed);
        server.setSubscriptionChanges(Arrays.asList(extraUrl), Arrays.asList());

        preferenceActivityRule.launchActivity(new Intent());
        clickSyncNow();

        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS).until(() -> feedUrlExists(extraUrl));
        assertFalse(server.uploadedAddedFeeds.contains(extraUrl));

        List<Feed> feeds = DBReader.getFeedList();
        long syncedFeedId = 0;
        for (Feed feed : feeds) {
            if (extraUrl.equals(feed.getDownloadUrl())) {
                syncedFeedId = feed.getId();
            }
        }
        final long feedId = syncedFeedId;
        await().atMost(90, TimeUnit.SECONDS)
                .until(() -> !DBReader.getFeedItemList(DBReader.getFeed(feedId, false, 0, Integer.MAX_VALUE),
                        FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE).isEmpty());

        server.setSubscriptionChanges(Arrays.asList(), Arrays.asList(extraUrl));
        server.clearRecordedRequests();
        clickSyncNow();
        waitForSyncToFinish();
        await().atMost(60, TimeUnit.SECONDS).until(() -> !feedUrlExists(extraUrl));
    }

    @Test
    public void testEpisodeActionFromServerMarksPlayedAndSetsPosition() throws Exception {
        connectProvider();
        uiTestUtils.addLocalFeedData(false);
        markAllFeedsRefreshed();
        giveMediaADuration();

        Feed feed = DBReader.getFeedList().get(0);
        List<FeedItem> feedItems = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        FeedItem finishedItem = feedItems.get(0);
        FeedItem progressItem = feedItems.get(1);
        long finishedItemId = finishedItem.getId();

        JSONArray actions = new JSONArray();
        actions.put(new JSONObject()
                .put("podcast", feed.getDownloadUrl())
                .put("episode", finishedItem.getMedia().getDownloadUrl())
                .put("action", "play")
                .put("position", 299)
                .put("started", 0)
                .put("total", 300)
                .put("timestamp", "2026-01-01T10:00:00"));
        actions.put(new JSONObject()
                .put("podcast", feed.getDownloadUrl())
                .put("episode", progressItem.getMedia().getDownloadUrl())
                .put("action", "play")
                .put("position", 42)
                .put("started", 10)
                .put("total", 300)
                .put("timestamp", "2026-01-01T11:00:00"));
        actions.put(new JSONObject()
                .put("podcast", "http://example.com/unknown")
                .put("episode", "http://example.com/unknown/episode")
                .put("action", "play")
                .put("position", 10)
                .put("started", 0)
                .put("total", 300)
                .put("timestamp", "2026-01-01T12:00:00"));
        actions.put(new JSONObject()
                .put("podcast", feed.getDownloadUrl())
                .put("episode", progressItem.getMedia().getDownloadUrl())
                .put("action", "new")
                .put("timestamp", "2026-01-01T13:00:00"));
        actions.put(new JSONObject()
                .put("podcast", feed.getDownloadUrl())
                .put("episode", progressItem.getMedia().getDownloadUrl())
                .put("action", "delete")
                .put("timestamp", "2026-01-01T14:00:00"));
        server.setEpisodeActions(actions);
        SynchronizationQueue.getInstance().enqueueEpisodeAction(
                new EpisodeAction.Builder(finishedItem, EpisodeAction.PLAY)
                        .timestamp(new Date(0))
                        .started(50).position(50).total(300).build());

        preferenceActivityRule.launchActivity(new Intent());
        clickSyncNow();
        waitForSyncToFinish();

        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> DBReader.getFeedItem(finishedItemId).isPlayed());
        assertEquals(0, DBReader.getFeedItem(finishedItemId).getMedia().getPosition());
        assertFalse(DBReader.getQueueIDList().contains(finishedItemId));

        FeedItem storedProgressItem = DBReader.getFeedItem(progressItem.getId());
        assertEquals(42000, storedProgressItem.getMedia().getPosition());
        assertFalse(storedProgressItem.isPlayed());
        assertTrue(SynchronizationSettings.isLastSyncSuccessful());
        await().atMost(30, TimeUnit.SECONDS).until(() ->
                server.uploadedEpisodeActions.stream().anyMatch(action ->
                        finishedItem.getMedia().getDownloadUrl().equals(action.optString("episode"))
                                && "play".equals(action.optString("action"))));
    }

    @Test
    public void testMarkingEpisodePlayedInUiUploadsPlayAction() throws Exception {
        connectProvider();
        uiTestUtils.addLocalFeedData(false);
        markAllFeedsRefreshed();
        giveMediaADuration();

        mainActivityRule.launchActivity(new Intent());
        clickBottomNavOverflow(R.string.episodes_label);
        Matcher<View> episodesList = allOf(withId(R.id.recyclerView),
                isDisplayed(), hasMinimumChildCount(2));
        onView(isRoot()).perform(waitForView(episodesList, 10000));
        onView(episodesList).perform(actionOnItemAtPosition(0, longClick()));
        waitForViewGlobally(withText(R.string.mark_as_played_label), 10000);
        onView(withText(R.string.mark_as_played_label)).perform(click());

        List<FeedItem> episodes = DBReader.getEpisodes(0, 1,
                FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD);
        FeedItem markedItem = episodes.get(0);
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> DBReader.getFeedItem(markedItem.getId()).isPlayed());

        preferenceActivityRule.launchActivity(new Intent());
        clickSyncNow();
        waitForSyncToFinish();

        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS).until(() ->
                server.uploadedEpisodeActions.stream().anyMatch(action ->
                        markedItem.getMedia().getDownloadUrl().equals(action.optString("episode"))
                                && "play".equals(action.optString("action"))
                                && action.optInt("position") == 300
                                && "device1".equals(action.optString("device"))));
        assertTrue(SynchronizationSettings.isLastSyncSuccessful());
    }

    @Test
    public void testServerErrorMarksLastSyncFailed() throws Exception {
        connectProvider();
        uiTestUtils.addLocalFeedData(false);
        markAllFeedsRefreshed();
        server.setSubscriptionDownloadStatus(500);

        preferenceActivityRule.launchActivity(new Intent());
        clickSyncNow();
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> lastSyncEventMessage == R.string.sync_status_error);
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> !SynchronizationSettings.isLastSyncSuccessful());
        assertFalse(server.hasRequest("GET", "/api/2/episodes/"));
    }

    @Test
    public void testForceFullSyncRestartsFromBeginning() throws Exception {
        connectProvider();
        uiTestUtils.addLocalFeedData(false);
        markAllFeedsRefreshed();

        preferenceActivityRule.launchActivity(new Intent());
        clickSyncNow();
        waitForSyncToFinish();
        assertTrue(SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp() > 0);

        server.clearRecordedRequests();
        pressBack();
        lastSyncEventMessage = -1;
        clickPreference(R.string.synchronization_pref);
        clickPreference(R.string.synchronization_full_sync_title);
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> server.countRequests("GET", "/api/2/subscriptions/") >= 1);
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS).until(() ->
                server.getRequests("GET", "/api/2/subscriptions/")
                        .stream().anyMatch(request -> request.path.contains("since=0")));
        waitForSyncToFinish();
    }

    @Test
    public void testSyncWaitsForFeedRefreshOfNewSubscription() throws Exception {
        connectProvider();
        uiTestUtils.addHostedFeedData();
        Feed neverRefreshedFeed = new Feed(0, null, "Existing feed", "http://example.com/existing",
                "Description", "http://example.com/pay", "author", "en", Feed.TYPE_RSS2,
                "existingfeed", null, null, uiTestUtils.hostedFeeds.get(4).getDownloadUrl(), 0);
        neverRefreshedFeed.setItems(new ArrayList<>());
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(neverRefreshedFeed);
        adapter.close();

        long unrefreshedFeedId = neverRefreshedFeed.getId();
        preferenceActivityRule.launchActivity(new Intent());
        clickSyncNow();
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> server.uploadedAddedFeeds
                        .contains(uiTestUtils.hostedFeeds.get(4).getDownloadUrl()));
        await().atMost(90, TimeUnit.SECONDS).until(() ->
                DBReader.getFeed(unrefreshedFeedId, false, 0, Integer.MAX_VALUE)
                        .getLastRefreshAttempt() > 0);
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> server.hasRequest("GET", "/api/2/episodes/"));
        await().atMost(90, TimeUnit.SECONDS).until(() ->
                !DBReader.getFeedItemList(DBReader.getFeed(unrefreshedFeedId, false, 0,
                        Integer.MAX_VALUE), FeedItemFilter.unfiltered(),
                        SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE).isEmpty());
        waitForSyncToFinish();
        assertTrue(SynchronizationSettings.isLastSyncSuccessful());
    }
}
