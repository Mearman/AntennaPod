package de.danoeh.antennapod.net.sync.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.SyncServiceEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.common.RedirectChecker;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.testsupport.FakeHttpClient;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class SyncServiceTest {
    private static final String LOCAL_FEED = "http://local.example/rss";
    private static final String SUBSCRIPTIONS_UNCHANGED = "{\"add\": [], \"remove\": [], \"timestamp\": 100}";
    private static final String UPLOAD_ACCEPTED = "{\"timestamp\": 150, \"update_urls\": []}";
    private static final String NO_EPISODE_ACTIONS = "{\"timestamp\": 200, \"actions\": []}";

    public static class MessageCollector {
        final List<String> messages = new ArrayList<>();

        @Subscribe
        public void onMessage(MessageEvent event) {
            messages.add(event.message);
        }
    }

    private Context context;
    private FakeHttpClient http;
    private SynchronizationQueueStorage storage;
    private int runAttemptCount;
    private List<String> localSubscriptions;
    private MockedStatic<AntennapodHttpClient> httpClientStatic;
    private MockedStatic<DBReader> dbReader;
    private MockedStatic<DBWriter> dbWriter;
    private MockedStatic<FeedDatabaseWriter> feedDatabaseWriter;
    private MockedStatic<RedirectChecker> redirectChecker;
    private MockedStatic<UserPreferences> userPreferences;
    private FeedUpdateManager feedUpdateManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        SynchronizationSettings.init(context);
        SynchronizationCredentials.init(context);
        SynchronizationSettings.setSelectedSyncProvider("GPODDER_NET");
        SynchronizationCredentials.setHosturl("gpodder.example");
        SynchronizationCredentials.setDeviceId("device");
        SynchronizationCredentials.setUsername("user");
        SynchronizationCredentials.setPassword("secret");
        storage = new SynchronizationQueueStorage(context);
        http = new FakeHttpClient();
        runAttemptCount = 0;
        localSubscriptions = new ArrayList<>();

        httpClientStatic = mockStatic(AntennapodHttpClient.class);
        httpClientStatic.when(AntennapodHttpClient::getHttpClient).thenReturn(http.client());
        dbReader = mockStatic(DBReader.class);
        dbReader.when(() -> DBReader.getFeedListDownloadUrls(true)).thenAnswer(invocation -> localSubscriptions);
        dbReader.when(DBReader::getFeedList).thenReturn(new ArrayList<>());
        dbReader.when(() -> DBReader.getEpisodes(anyInt(), anyInt(), any(), any())).thenReturn(new ArrayList<>());
        dbWriter = mockStatic(DBWriter.class);
        feedDatabaseWriter = mockStatic(FeedDatabaseWriter.class);
        redirectChecker = mockStatic(RedirectChecker.class);
        redirectChecker.when(() -> RedirectChecker.getFinalUrl(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        userPreferences = mockStatic(UserPreferences.class);
        userPreferences.when(UserPreferences::getSmartMarkAsPlayedSecs).thenReturn(30);
        userPreferences.when(UserPreferences::gpodnetNotificationsEnabled).thenReturn(true);
        feedUpdateManager = mock(FeedUpdateManager.class);
        FeedUpdateManager.setInstance(feedUpdateManager);
        EventBus.getDefault().removeAllStickyEvents();
    }

    @After
    public void tearDown() {
        httpClientStatic.close();
        dbReader.close();
        dbWriter.close();
        feedDatabaseWriter.close();
        redirectChecker.close();
        userPreferences.close();
        FeedUpdateManager.setInstance(null);
        EventBus.getDefault().removeAllStickyEvents();
    }

    private SyncService createWorker() {
        WorkerParameters parameters = mock(WorkerParameters.class);
        when(parameters.getRunAttemptCount()).thenAnswer(invocation -> runAttemptCount);
        return new SyncService(context, parameters);
    }

    private ListenableWorker.Result doWork() {
        return createWorker().doWork();
    }

    private void assertSyncSucceeds() {
        assertEquals(ListenableWorker.Result.success(), doWork());
    }

    private void enqueueLogin() {
        http.enqueue(200, "");
    }

    private void enqueueUnchangedSubscriptions() {
        http.enqueue(200, SUBSCRIPTIONS_UNCHANGED);
    }

    private static int lastEventMessage() {
        return EventBus.getDefault().getStickyEvent(SyncServiceEvent.class).getMessageResId();
    }

    private static EpisodeAction playAction(String episode, String guid, long timestamp, int position) {
        return new EpisodeAction.Builder("http://podcast.example", episode, EpisodeAction.PLAY)
                .guid(guid)
                .timestamp(new Date(timestamp))
                .started(0)
                .position(position)
                .total(1000)
                .build();
    }

    private static String remotePlayActionJson(String episode, String guid, String timestamp, int position) {
        return "{\"podcast\": \"http://podcast.example\", \"episode\": \"" + episode + "\", \"guid\": \"" + guid
                + "\", \"action\": \"play\", \"timestamp\": \"" + timestamp + "\", \"started\": 0, \"position\": "
                + position + ", \"total\": 1000}";
    }

    private static FeedItem itemWithMedia(long id, int durationMs, int positionMs) {
        Feed feed = new Feed("http://podcast.example", null, "Podcast");
        FeedItem item = new FeedItem(id, "Episode " + id, "guid-" + id, "http://link.example", new Date(), 0, feed);
        FeedMedia media = new FeedMedia(item, "http://podcast.example/" + id + ".mp3", 0, "audio/mp3");
        media.setDuration(durationMs);
        media.setPosition(positionMs);
        item.setMedia(media);
        return item;
    }

    @Test
    public void nothingHappensWithoutSelectedSyncProvider() {
        SynchronizationSettings.setSelectedSyncProvider(null);

        assertEquals(ListenableWorker.Result.success(), doWork());

        assertEquals(0, http.requests().size());
        assertEquals(0, SynchronizationSettings.getLastSyncAttempt());
    }

    @Test
    public void nothingHappensForUnknownSyncProvider() {
        SynchronizationSettings.setSelectedSyncProvider("SOMETHING_ELSE");

        assertEquals(ListenableWorker.Result.success(), doWork());

        assertEquals(0, http.requests().size());
    }

    @Test
    public void syncWithoutChangesLogsInAndDownloadsBothKindsOfChanges() {
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertEquals(ListenableWorker.Result.success(), doWork());

        assertEquals(3, http.requests().size());
        assertEquals("/api/2/auth/user/login.json", http.request(0).request.url().encodedPath());
        assertEquals("/api/2/subscriptions/user/device.json", http.request(1).request.url().encodedPath());
        assertEquals("/api/2/episodes/user.json", http.request(2).request.url().encodedPath());
    }

    @Test
    public void successfulSyncRecordsTimestampsAndOutcome() {
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        assertEquals(100, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(200, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
        assertTrue(SynchronizationSettings.isLastSyncSuccessful());
        assertTrue(SynchronizationSettings.getLastSyncAttempt() > 0);
        assertEquals(R.string.sync_status_success, lastEventMessage());
    }

    @Test
    public void laterSyncRequestsOnlyChangesSinceTheStoredTimestamps() {
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(60);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(70);
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        assertEquals("since=60", http.request(1).request.url().encodedQuery());
        assertEquals("since=70", http.request(2).request.url().encodedQuery());
    }

    @Test
    public void firstSyncUploadsAllLocalSubscriptions() throws Exception {
        localSubscriptions.add(LOCAL_FEED);
        localSubscriptions.add("http://other.example/rss");
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, UPLOAD_ACCEPTED);
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        JSONObject upload = new JSONObject(http.request(2).body);
        assertEquals("POST", http.request(2).request.method());
        assertEquals(new JSONArray(Arrays.asList(LOCAL_FEED, "http://other.example/rss")).toString(),
                upload.getJSONArray("add").toString());
        assertEquals(0, upload.getJSONArray("remove").length());
        assertEquals(150, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
    }

    @Test
    public void firstSyncDoesNotUploadSubscriptionsTheServerJustReported() throws Exception {
        localSubscriptions.add(LOCAL_FEED);
        localSubscriptions.add("http://other.example/rss");
        enqueueLogin();
        http.enqueue(200, "{\"add\": [\"" + LOCAL_FEED + "\"], \"remove\": [], \"timestamp\": 100}");
        http.enqueue(200, UPLOAD_ACCEPTED);
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        JSONObject upload = new JSONObject(http.request(2).body);
        assertEquals(new JSONArray(Collections.singletonList("http://other.example/rss")).toString(),
                upload.getJSONArray("add").toString());
    }

    @Test
    public void queuedSubscriptionChangesAreUploadedAndClearedAfterwards() throws Exception {
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(60);
        storage.enqueueFeedAdded("http://new.example/rss");
        storage.enqueueFeedRemoved("http://old.example/rss");
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, UPLOAD_ACCEPTED);
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        JSONObject upload = new JSONObject(http.request(2).body);
        assertEquals("http://new.example/rss", upload.getJSONArray("add").getString(0));
        assertEquals("http://old.example/rss", upload.getJSONArray("remove").getString(0));
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
        assertEquals(150, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
    }

    @Test
    public void emptyFeedQueuesAreClearedWithoutUploading() {
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(60);
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        assertEquals(3, http.requests().size());
        assertEquals("GET", http.request(1).request.method());
        assertEquals("GET", http.request(2).request.method());
    }

    @Test
    public void remoteSubscriptionsAreAddedAsPlaceholderFeeds() {
        enqueueLogin();
        http.enqueue(200, "{\"add\": [\"http://new.example/rss\"], \"remove\": [], \"timestamp\": 100}");
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        ArgumentCaptor<Feed> feed = ArgumentCaptor.forClass(Feed.class);
        feedDatabaseWriter.verify(() -> FeedDatabaseWriter.updateFeed(eq(context), feed.capture(), eq(false)));
        assertEquals("http://new.example/rss", feed.getValue().getDownloadUrl());
        assertEquals("Unknown podcast", feed.getValue().getTitle());
        assertTrue(feed.getValue().getItems().isEmpty());
    }

    @Test
    public void remoteSubscriptionsThatAreAlreadyKnownOrUnsupportedAreNotAdded() {
        localSubscriptions.add(LOCAL_FEED);
        storage.enqueueFeedRemoved("http://removed.example/rss");
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(60);
        enqueueLogin();
        http.enqueue(200, "{\"add\": [\"" + LOCAL_FEED + "\", \"ftp://old.example/rss\","
                + "\"http://removed.example/rss\"], \"remove\": [], \"timestamp\": 100}");
        http.enqueue(200, UPLOAD_ACCEPTED);
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        feedDatabaseWriter.verify(() -> FeedDatabaseWriter.updateFeed(any(), any(), anyBoolean()), never());
    }

    @Test
    public void remoteSubscriptionsRedirectingToKnownFeedsAreNotAdded() {
        localSubscriptions.add(LOCAL_FEED);
        redirectChecker.when(() -> RedirectChecker.getNewUrlIfPermanentRedirect("http://moved.example/rss"))
                .thenReturn(LOCAL_FEED);
        redirectChecker.when(() -> RedirectChecker.getFinalUrl("http://chain.example/rss")).thenReturn(LOCAL_FEED);
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(60);
        enqueueLogin();
        http.enqueue(200, "{\"add\": [\"http://moved.example/rss\", \"http://chain.example/rss\"],"
                + "\"remove\": [], \"timestamp\": 100}");
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        feedDatabaseWriter.verify(() -> FeedDatabaseWriter.updateFeed(any(), any(), anyBoolean()), never());
    }

    @Test
    public void remoteSubscriptionsRedirectingToFeedsQueuedForRemovalAreNotAdded() {
        storage.enqueueFeedRemoved("http://removed.example/rss");
        redirectChecker.when(() -> RedirectChecker.getNewUrlIfPermanentRedirect("http://moved.example/rss"))
                .thenReturn("http://removed.example/rss");
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(60);
        enqueueLogin();
        http.enqueue(200, "{\"add\": [\"http://moved.example/rss\"], \"remove\": [], \"timestamp\": 100}");
        http.enqueue(200, UPLOAD_ACCEPTED);
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        feedDatabaseWriter.verify(() -> FeedDatabaseWriter.updateFeed(any(), any(), anyBoolean()), never());
    }

    @Test
    public void remoteUnsubscriptionsRemoveLocalFeedsUnlessJustSubscribedAgain() {
        storage.enqueueFeedAdded("http://again.example/rss");
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(60);
        enqueueLogin();
        http.enqueue(200, "{\"add\": [], \"remove\": [\"http://gone.example/rss\", \"http://again.example/rss\"],"
                + "\"timestamp\": 100}");
        http.enqueue(200, UPLOAD_ACCEPTED);
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        dbWriter.verify(() -> DBWriter.removeFeedWithDownloadUrl(context, "http://gone.example/rss"));
        dbWriter.verify(() -> DBWriter.removeFeedWithDownloadUrl(eq(context), eq("http://again.example/rss")), never());
    }

    @Test
    public void remoteEpisodePositionIsAppliedToKnownEpisode() {
        FeedItem item = itemWithMedia(5, 1000000, 0);
        dbReader.when(() -> DBReader.getFeedItemByGuidOrEpisodeUrl("guid-5", "http://podcast.example/5.mp3"))
                .thenReturn(item);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(70);
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, "{\"timestamp\": 200, \"actions\": ["
                + remotePlayActionJson("http://podcast.example/5.mp3", "guid-5", "2021-01-01T08:00:00", 300) + "]}");

        assertSyncSucceeds();

        assertEquals(300000, item.getMedia().getPosition());
        assertFalse(item.isPlayed());
        dbWriter.verify(() -> DBWriter.setItemList(Collections.singletonList(item)));
        dbWriter.verify(() -> DBWriter.removeQueueItem(context, false));
    }

    @Test
    public void remoteEpisodePositionNearTheEndMarksEpisodePlayedAndRemovesItFromQueue() {
        FeedItem item = itemWithMedia(6, 1000000, 0);
        dbReader.when(() -> DBReader.getFeedItemByGuidOrEpisodeUrl("guid-6", "http://podcast.example/6.mp3"))
                .thenReturn(item);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(70);
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, "{\"timestamp\": 200, \"actions\": ["
                + remotePlayActionJson("http://podcast.example/6.mp3", "guid-6", "2021-01-01T08:00:00", 980) + "]}");

        assertSyncSucceeds();

        assertTrue(item.isPlayed());
        assertEquals(0, item.getMedia().getPosition());
        dbWriter.verify(() -> DBWriter.removeQueueItem(context, false, 6L));
        dbWriter.verify(() -> DBWriter.setItemList(Collections.singletonList(item)));
    }

    @Test
    public void remoteEpisodeActionsWithInvalidGuidLookUpEpisodeByUrlOnly() {
        FeedItem item = itemWithMedia(7, 1000000, 0);
        dbReader.when(() -> DBReader.getFeedItemByGuidOrEpisodeUrl(null, "http://podcast.example/7.mp3"))
                .thenReturn(item);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(70);
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, "{\"timestamp\": 200, \"actions\": ["
                + remotePlayActionJson("http://podcast.example/7.mp3", "null", "2021-01-01T08:00:00", 100) + "]}");

        assertSyncSucceeds();

        assertEquals(100000, item.getMedia().getPosition());
    }

    @Test
    public void remoteEpisodeActionsForUnknownEpisodesOrEpisodesWithoutMediaAreIgnored() {
        FeedItem withoutMedia = new FeedItem(8, "No media", "guid-8", "http://link.example", new Date(), 0, null);
        dbReader.when(() -> DBReader.getFeedItemByGuidOrEpisodeUrl("guid-8", "http://podcast.example/8.mp3"))
                .thenReturn(withoutMedia);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(70);
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, "{\"timestamp\": 200, \"actions\": ["
                + remotePlayActionJson("http://podcast.example/8.mp3", "guid-8", "2021-01-01T08:00:00", 100) + ","
                + remotePlayActionJson("http://podcast.example/unknown.mp3", "guid-x", "2021-01-01T08:00:00", 100)
                + "]}");

        assertEquals(ListenableWorker.Result.success(), doWork());

        dbWriter.verify(() -> DBWriter.setItemList(Collections.emptyList()));
    }

    @Test
    public void newerLocalPlaybackIsNotOverwrittenByOlderRemotePlayback() {
        FeedItem item = itemWithMedia(9, 1000000, 500000);
        dbReader.when(() -> DBReader.getFeedItemByGuidOrEpisodeUrl(anyString(), anyString())).thenReturn(item);
        storage.enqueueEpisodeAction(playAction("http://podcast.example/9.mp3", "guid-9", 1609495200000L, 500));
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(70);
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, "{\"timestamp\": 200, \"actions\": ["
                + remotePlayActionJson("http://podcast.example/9.mp3", "guid-9", "2021-01-01T08:00:00", 100) + "]}");
        http.enqueue(200, UPLOAD_ACCEPTED);

        assertSyncSucceeds();

        assertEquals(500000, item.getMedia().getPosition());
        dbWriter.verify(() -> DBWriter.setItemList(Collections.emptyList()));
    }

    @Test
    public void queuedEpisodeActionsAreUploadedAndClearedAfterwards() throws Exception {
        storage.enqueueEpisodeAction(playAction("http://podcast.example/1.mp3", "guid-1", 1609488000000L, 10));
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(70);
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);
        http.enqueue(200, "{\"timestamp\": 250, \"update_urls\": []}");

        assertSyncSucceeds();

        JSONArray upload = new JSONArray(http.request(3).body);
        assertEquals(1, upload.length());
        assertEquals("http://podcast.example/1.mp3", upload.getJSONObject(0).getString("episode"));
        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        assertEquals(250, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void failedEpisodeActionUploadKeepsQueueForTheNextAttempt() {
        storage.enqueueEpisodeAction(playAction("http://podcast.example/1.mp3", "guid-1", 1609488000000L, 10));
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(70);
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);
        http.enqueue(500, "");

        assertEquals(ListenableWorker.Result.retry(), doWork());

        assertEquals(1, storage.getQueuedEpisodeActions().size());
        assertEquals(70, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void firstEpisodeSyncUploadsPlayedStateOfAllPlayedEpisodes() throws Exception {
        FeedItem played = itemWithMedia(10, 90000, 0);
        FeedItem withoutMedia = new FeedItem(11, "No media", "guid-11", "http://link.example", new Date(), 0, null);
        dbReader.when(() -> DBReader.getEpisodes(anyInt(), anyInt(), any(), any()))
                .thenReturn(new ArrayList<>(Arrays.asList(played, withoutMedia)));
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);
        http.enqueue(200, "{\"timestamp\": 300, \"update_urls\": []}");

        assertSyncSucceeds();

        JSONArray upload = new JSONArray(http.request(3).body);
        assertEquals(1, upload.length());
        JSONObject action = upload.getJSONObject(0);
        assertEquals("play", action.getString("action"));
        assertEquals("http://podcast.example/10.mp3", action.getString("episode"));
        assertEquals("guid-10", action.getString("guid"));
        assertEquals(90, action.getInt("started"));
        assertEquals(90, action.getInt("position"));
        assertEquals(90, action.getInt("total"));
        assertEquals(300, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void syncWaitsForFirstRefreshOfNewSubscriptionsBeforeSyncingEpisodeActions() {
        Feed neverRefreshed = new Feed("http://new.example/rss", null, "New", "user", "pass");
        dbReader.when(DBReader::getFeedList).thenReturn(new ArrayList<>(Collections.singletonList(neverRefreshed)));
        enqueueLogin();
        enqueueUnchangedSubscriptions();

        assertEquals(ListenableWorker.Result.success(), doWork());

        assertEquals(2, http.requests().size());
        verify(feedUpdateManager).runOnce(context);
        assertEquals(R.string.sync_status_wait_for_downloads, lastEventMessage());
        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
        assertEquals(0, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void syncContinuesWhenAllFeedsWereRefreshedOrAreNotKeptUpdated() {
        Feed refreshed = new Feed("http://a.example/rss", null, "A", "user", "pass");
        refreshed.setLastRefreshAttempt(1);
        Feed notUpdated = new Feed("http://b.example/rss", null, "B", "user", "pass");
        notUpdated.getPreferences().setKeepUpdated(false);
        dbReader.when(DBReader::getFeedList).thenReturn(new ArrayList<>(Arrays.asList(refreshed, notUpdated)));
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertEquals(ListenableWorker.Result.success(), doWork());

        assertEquals(3, http.requests().size());
        verify(feedUpdateManager, never()).runOnce(any(Context.class));
    }

    private static boolean isSleeping(Thread thread) {
        SyncServiceEvent event = EventBus.getDefault().getStickyEvent(SyncServiceEvent.class);
        if (event == null || event.getMessageResId() != R.string.sync_status_wait_for_downloads) {
            return false;
        }
        for (StackTraceElement frame : thread.getStackTrace()) {
            if (frame.getClassName().equals(Thread.class.getName()) && frame.getMethodName().startsWith("sleep")) {
                return true;
            }
        }
        return false;
    }

    private Thread actWhenSleeping(Thread sleeper, Runnable action) {
        Thread observer = new Thread(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!isSleeping(sleeper) && System.nanoTime() < deadline) {
                Thread.yield();
            }
            action.run();
        });
        observer.start();
        return observer;
    }

    @Test
    public void syncWaitsWhileFeedUpdateIsRunningAndContinuesAfterwards() throws Exception {
        EventBus.getDefault().postSticky(new FeedUpdateRunningEvent(true));
        Thread syncThread = Thread.currentThread();
        Thread observer = actWhenSleeping(syncThread,
                () -> EventBus.getDefault().postSticky(new FeedUpdateRunningEvent(false)));
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        observer.join();
        assertEquals(3, http.requests().size());
        assertTrue(SynchronizationSettings.isLastSyncSuccessful());
    }

    @Test
    public void interruptedWaitForFeedUpdateDoesNotAbortTheSync() throws Exception {
        EventBus.getDefault().postSticky(new FeedUpdateRunningEvent(true));
        Thread syncThread = Thread.currentThread();
        Thread observer = actWhenSleeping(syncThread, syncThread::interrupt);
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        observer.join();
        assertEquals(3, http.requests().size());
    }

    @Test
    public void nextcloudProviderSyncsThroughItsOwnEndpoints() {
        SynchronizationSettings.setSelectedSyncProvider("NEXTCLOUD_GPODDER");
        SynchronizationCredentials.setHosturl("https://cloud.example");
        http.enqueue(200, SUBSCRIPTIONS_UNCHANGED);
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertEquals(ListenableWorker.Result.success(), doWork());

        assertEquals(2, http.requests().size());
        assertEquals("/index.php/apps/gpoddersync/subscriptions", http.request(0).request.url().encodedPath());
        assertEquals("/index.php/apps/gpoddersync/episode_action", http.request(1).request.url().encodedPath());
    }

    @Test
    public void serverErrorFailsSyncWithRetryAndReportsError() {
        enqueueLogin();
        http.enqueue(503, "");

        assertEquals(ListenableWorker.Result.retry(), doWork());

        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
        assertEquals(R.string.sync_status_error, lastEventMessage());
    }

    @Test
    public void wrongCredentialsFailSyncWithRetry() {
        http.enqueue(401, "");

        assertEquals(ListenableWorker.Result.retry(), doWork());

        assertEquals(1, http.requests().size());
        assertEquals(R.string.sync_status_error, lastEventMessage());
    }

    @Test
    public void failedSubscriptionUploadRetriesLaterAndReportsError() {
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(60);
        storage.enqueueFeedAdded("http://new.example/rss");
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(500, "");

        assertEquals(ListenableWorker.Result.retry(), doWork());

        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
        assertEquals(60, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
    }

    @Test
    public void unexpectedExceptionFailsSyncWithoutRetry() {
        dbReader.when(() -> DBReader.getFeedListDownloadUrls(true)).thenThrow(new IllegalStateException("broken"));
        enqueueLogin();

        assertEquals(ListenableWorker.Result.failure(), doWork());

        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
        assertEquals(R.string.sync_status_error, lastEventMessage());
    }

    @Test
    public void unexpectedExceptionIsShownToTheUserAsMessage() {
        MessageCollector collector = new MessageCollector();
        EventBus.getDefault().register(collector);
        try {
            dbReader.when(() -> DBReader.getFeedListDownloadUrls(true)).thenThrow(new IllegalStateException("broken"));
            enqueueLogin();

            doWork();

            assertEquals(1, collector.messages.size());
            assertTrue(collector.messages.get(0).endsWith("broken"));
        } finally {
            EventBus.getDefault().unregister(collector);
        }
    }

    @Test
    public void unexpectedExceptionIsNotShownWhenNotificationsAreDisabled() {
        MessageCollector collector = new MessageCollector();
        EventBus.getDefault().register(collector);
        try {
            userPreferences.when(UserPreferences::gpodnetNotificationsEnabled).thenReturn(false);
            dbReader.when(() -> DBReader.getFeedListDownloadUrls(true)).thenThrow(new IllegalStateException("broken"));
            enqueueLogin();

            doWork();

            assertTrue(collector.messages.isEmpty());
        } finally {
            EventBus.getDefault().unregister(collector);
        }
    }

    @Test
    public void retriableErrorIsOnlyShownOnEveryThirdAttempt() {
        MessageCollector collector = new MessageCollector();
        EventBus.getDefault().register(collector);
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                runAttemptCount = attempt;
                enqueueLogin();
                http.enqueue(503, "");
                doWork();
                assertEquals(attempt == 2 ? 1 : 0, collector.messages.size());
            }
        } finally {
            EventBus.getDefault().unregister(collector);
        }
    }

    @Test
    public void errorIsPostedAsNotificationWhenNoMessageSubscriberExists() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        dbReader.when(() -> DBReader.getFeedListDownloadUrls(true)).thenThrow(new IllegalStateException("broken"));
        enqueueLogin();

        doWork();

        NotificationManager notificationManager = (NotificationManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = shadowOf(notificationManager).getNotification(R.id.notification_gpodnet_sync_error);
        assertNotNull(notification);
        assertTrue(shadowOf(notification).getContentText().toString().endsWith("broken"));
    }

    @Test
    public void successfulSyncRemovesEarlierErrorNotifications() {
        NotificationManager notificationManager = (NotificationManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(R.id.notification_gpodnet_sync_error,
                new Notification.Builder(RuntimeEnvironment.getApplication(), "channel").build());
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertSyncSucceeds();

        assertNull(shadowOf(notificationManager).getNotification(R.id.notification_gpodnet_sync_error));
    }

    @Test
    public void syncRequestedWhileAnotherSyncIsRunningDoesNotStartASecondSync() {
        AtomicReference<ListenableWorker.Result> nestedResult = new AtomicReference<>();
        AtomicBoolean activeDuringRequest = new AtomicBoolean(false);
        int[] requestsSeenByNestedWorker = new int[1];
        http.beforeEachReply(() -> {
            if (nestedResult.get() == null && http.requests().size() == 1) {
                activeDuringRequest.set(SyncService.isCurrentlyActive());
                nestedResult.set(createWorker().doWork());
                requestsSeenByNestedWorker[0] = http.requests().size();
            }
        });
        enqueueLogin();
        enqueueUnchangedSubscriptions();
        http.enqueue(200, NO_EPISODE_ACTIONS);

        assertEquals(ListenableWorker.Result.success(), doWork());

        assertTrue(activeDuringRequest.get());
        assertEquals(ListenableWorker.Result.success(), nestedResult.get());
        assertEquals(1, requestsSeenByNestedWorker[0]);
        assertEquals(3, http.requests().size());
        assertFalse(SyncService.isCurrentlyActive());
    }

    @Test
    public void syncScheduledWhileSyncIsRunningIsDelayedByTwoMinutes() {
        WorkManager workManager = mock(WorkManager.class);
        AtomicReference<OneTimeWorkRequest> scheduled = new AtomicReference<>();
        try (MockedStatic<WorkManager> workManagerStatic = mockStatic(WorkManager.class)) {
            workManagerStatic.when(() -> WorkManager.getInstance(context)).thenReturn(workManager);
            when(workManager.enqueueUniqueWork(anyString(), any(ExistingWorkPolicy.class),
                    any(OneTimeWorkRequest.class))).thenAnswer(invocation -> {
                        scheduled.set(invocation.getArgument(2));
                        return null;
                    });
            http.beforeEachReply(() -> {
                if (scheduled.get() == null) {
                    new SynchronizationQueueImpl(context).sync();
                }
            });
            enqueueLogin();
            enqueueUnchangedSubscriptions();
            http.enqueue(200, NO_EPISODE_ACTIONS);

            assertSyncSucceeds();

            assertEquals(TimeUnit.MINUTES.toMillis(2), scheduled.get().getWorkSpec().initialDelay);
        }
    }
}
