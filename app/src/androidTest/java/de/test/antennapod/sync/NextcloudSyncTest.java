package de.test.antennapod.sync;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.SyncServiceEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.ui.UITestUtils;
import de.test.antennapod.util.sync.NextcloudTestServer;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickPreference;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class NextcloudSyncTest {
    private static final long SYNC_WAIT_SECONDS = 120;

    private NextcloudTestServer server;
    private UITestUtils uiTestUtils;
    private Instrumentation.ActivityMonitor browserMonitor;
    private volatile int lastSyncEventMessage = -1;

    @Rule
    public ActivityTestRule<PreferenceActivity> activityTestRule =
            new ActivityTestRule<>(PreferenceActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.cancelPendingSyncWork();
        EspressoTestUtils.clearDatabase();
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.enableSyncOverAnyConnection();
        server = new NextcloudTestServer();
        server.start();
        uiTestUtils = new UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext());
        uiTestUtils.setup();
        IntentFilter browserFilter = new IntentFilter(Intent.ACTION_VIEW);
        browserFilter.addDataScheme("http");
        browserFilter.addDataScheme("https");
        browserMonitor = new Instrumentation.ActivityMonitor(browserFilter, null, true);
        InstrumentationRegistry.getInstrumentation().addMonitor(browserMonitor);
        EventBus.getDefault().removeStickyEvent(SyncServiceEvent.class);
        EventBus.getDefault().register(this);
        activityTestRule.launchActivity(new Intent());
    }

    @After
    public void tearDown() throws Exception {
        EventBus.getDefault().unregister(this);
        EspressoTestUtils.cancelPendingSyncWork();
        InstrumentationRegistry.getInstrumentation().removeMonitor(browserMonitor);
        activityTestRule.finishActivity();
        SynchronizationSettings.setSelectedSyncProvider(null);
        SynchronizationCredentials.clear();
        uiTestUtils.tearDown();
        server.stop();
    }

    private void openNextcloudLoginDialog() {
        clickPreference(R.string.synchronization_pref);
        onView(withText(R.string.synchronization_choose_title)).perform(click());
        onView(allOf(withText(R.string.synchronization_summary_nextcloud),
                isDescendantOfA(withId(R.id.provider_list))))
                .perform(scrollTo(), click());
        onView(withId(R.id.serverUrlText)).check(matches(isDisplayed()));
    }

    private void connectProvider(String username, String password) {
        SynchronizationSettings.resetTimestamps();
        SynchronizationSettings.setSelectedSyncProvider(
                SynchronizationProvider.NEXTCLOUD_GPODDER.getIdentifier());
        SynchronizationCredentials.setHosturl(server.getBaseUrl());
        SynchronizationCredentials.setUsername(username);
        SynchronizationCredentials.setPassword(password);
    }

    private boolean syncScreenOpen = false;

    private void clickSyncNow() {
        if (syncScreenOpen) {
            pressBack();
        }
        clickPreference(R.string.synchronization_pref);
        clickPreference(R.string.synchronization_sync_changes_title);
        syncScreenOpen = true;
    }

    private void markAllFeedsRefreshed() {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        for (Feed feed : DBReader.getFeedList()) {
            adapter.setFeedLastUpdateFailed(feed.getId(), false);
        }
        adapter.close();
    }

    @Test
    public void testLoginApprovesPollAndStartsFullSync() {
        openNextcloudLoginDialog();
        onView(withId(R.id.serverUrlText)).perform(replaceText(server.getBaseUrl()));
        onView(withId(R.id.chooseHostButton)).perform(click());

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> server.hasRequest("/index.php/login/v2"));
        server.approveLogin.set(true);

        await().atMost(20, TimeUnit.SECONDS)
                .until(SynchronizationSettings::isProviderConnected);
        assertEquals(NextcloudTestServer.USERNAME, SynchronizationCredentials.getUsername());
        assertEquals(NextcloudTestServer.APP_PASSWORD, SynchronizationCredentials.getPassword());
        assertEquals(server.getBaseUrl(), SynchronizationCredentials.getHosturl());
        assertTrue(server.hasRequest("/index.php/login/v2/poll"));
        assertTrue(browserMonitor.getHits() >= 1);

        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> server.hasRequest("/index.php/apps/gpoddersync/subscriptions"));
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> server.hasRequest("/index.php/apps/gpoddersync/episode_action"));
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(SynchronizationSettings::isLastSyncSuccessful);
    }

    @Test
    public void testLoginFlowErrorWhenServerUnreachable() {
        server.stop();
        openNextcloudLoginDialog();
        onView(withId(R.id.serverUrlText)).perform(replaceText("http://127.0.0.1:1"));
        onView(withId(R.id.chooseHostButton)).perform(click());

        String genericError = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getString(R.string.nextcloud_login_error_generic);
        waitForViewGlobally(withText(containsString(genericError)), 20000);
        assertFalse(SynchronizationSettings.isProviderConnected());
    }

    @Test
    public void testSubscriptionAndEpisodeSyncBothDirections() throws Exception {
        connectProvider(NextcloudTestServer.USERNAME, NextcloudTestServer.APP_PASSWORD);
        uiTestUtils.addLocalFeedData(false);
        markAllFeedsRefreshed();

        Feed extraFeed = new Feed(0, null, "Nextcloud synced feed", "http://example.com/ncfeed",
                "Description", "http://example.com/pay", "author", "en", Feed.TYPE_RSS2,
                "ncfeed", null, null, "http://example.com/nc/src", System.currentTimeMillis());
        extraFeed.setItems(new ArrayList<>());
        String extraUrl = uiTestUtils.hostFeed(extraFeed);
        server.setSubscriptionChanges(Arrays.asList(extraUrl), Arrays.asList());

        Feed firstLocalFeed = DBReader.getFeedList().get(0);
        List<FeedItem> localItems = DBReader.getFeedItemList(firstLocalFeed,
                FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        FeedItem firstItem = localItems.get(0);
        FeedItem playedItem = localItems.get(2);
        playedItem.setPlayed(true);
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(playedItem));
        JSONArray actions = new JSONArray();
        actions.put(new JSONObject()
                .put("podcast", firstItem.getFeed().getDownloadUrl())
                .put("episode", firstItem.getMedia().getDownloadUrl())
                .put("action", "play")
                .put("position", 100)
                .put("started", 0)
                .put("total", 300)
                .put("timestamp", "2026-01-01T10:00:00"));
        server.setEpisodeActions(actions);

        clickSyncNow();
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> server.hasRequest("/index.php/apps/gpoddersync/subscription_change/create"));
        await().atMost(60, TimeUnit.SECONDS).until(() -> server.subscriptionAdded.size()
                >= uiTestUtils.hostedFeeds.size());

        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> !server.uploadedEpisodeActions.isEmpty());
        JSONObject uploaded = server.uploadedEpisodeActions.get(0);
        assertEquals("play", uploaded.optString("action"));

        await().atMost(60, TimeUnit.SECONDS)
                .until(() -> DBReader.getFeedItem(firstItem.getId()).getMedia().getPosition() == 100000);
        boolean extraFeedFound = false;
        for (Feed feed : DBReader.getFeedList()) {
            if (extraUrl.equals(feed.getDownloadUrl())) {
                extraFeedFound = true;
            }
        }
        assertTrue(extraFeedFound);
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(SynchronizationSettings::isLastSyncSuccessful);
    }

    @Subscribe
    public void onSyncServiceEvent(SyncServiceEvent event) {
        lastSyncEventMessage = event.getMessageResId();
    }

    @Test
    public void testWrongAppPasswordFailsSync() throws Exception {
        connectProvider(NextcloudTestServer.USERNAME, "wrong-password");
        uiTestUtils.addLocalFeedData(false);
        markAllFeedsRefreshed();

        lastSyncEventMessage = -1;
        clickSyncNow();
        await().atMost(SYNC_WAIT_SECONDS, TimeUnit.SECONDS)
                .until(() -> lastSyncEventMessage == R.string.sync_status_error);
        assertFalse(server.hasRequest("/index.php/apps/gpoddersync/episode_action"));
    }
}
