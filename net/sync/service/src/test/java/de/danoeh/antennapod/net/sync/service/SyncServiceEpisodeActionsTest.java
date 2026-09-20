package de.danoeh.antennapod.net.sync.service;

import android.content.Context;
import androidx.work.ListenableWorker.Result;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class SyncServiceEpisodeActionsTest extends SyncServiceTestBase {
    private static final String GUID = "guid-1";
    private static final String EPISODE_URL = "https://cdn.example/1.mp3";
    private static final long JANUARY_FIRST_2024 = 1_704_067_200_000L;
    private String feedUrl;

    @Before
    public void setUp() {
        feedUrl = server.feedUrl("local");
        useGpodder();
        AutoDownloadManager.setInstance(new NoOpAutoDownloadManager());
    }

    @After
    public void tearDown() {
        AutoDownloadManager.setInstance(null);
    }

    private String remotePlay(String episode, String guid, String timestamp, int position) {
        return "{\"podcast\":\"" + feedUrl + "\",\"episode\":\"" + episode + "\",\"guid\":\"" + guid
                + "\",\"action\":\"play\",\"timestamp\":\"" + timestamp
                + "\",\"started\":0,\"position\":" + position + ",\"total\":100}";
    }

    private String remoteAction(String action) {
        return "{\"podcast\":\"" + feedUrl + "\",\"episode\":\"" + EPISODE_URL + "\",\"guid\":\"" + GUID
                + "\",\"action\":\"" + action + "\",\"timestamp\":\"2024-01-01T00:00:00\"}";
    }

    private static String actions(String... actions) {
        return "[" + String.join(",", actions) + "]";
    }

    private FeedMedia storedMedia() {
        FeedItem item = DBReader.getFeedItemByGuidOrEpisodeUrl(GUID, EPISODE_URL);
        assertNotNull(item);
        return item.getMedia();
    }

    private EpisodeAction localPlay(int position, long timestampMillis) {
        return new EpisodeAction.Builder(feedUrl, EPISODE_URL, EpisodeAction.PLAY)
                .guid(GUID)
                .timestamp(new Date(timestampMillis))
                .started(0)
                .position(position)
                .total(100)
                .build();
    }

    private JSONArray episodeUpload() throws Exception {
        List<RecordedRequest> uploads = server.requests("POST", GPODDER_EPISODES_PATH);
        assertEquals(1, uploads.size());
        return new JSONArray(uploads.get(0).getBody().clone().readUtf8());
    }

    @Test
    public void firstSyncUploadsPlayedStateOfPlayedEpisodesWithMedia() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, true);
        storeFeedWithMedialessEpisode(server.feedUrl("other"), "guid-without-media");
        serveGpodderChanges(100, "[]", "[]", 300, "[]");
        serveGpodderUploads(200, 400);

        assertEquals(Result.success(), runSync());

        JSONArray upload = episodeUpload();
        assertEquals(1, upload.length());
        JSONObject action = upload.getJSONObject(0);
        assertEquals(feedUrl, action.getString("podcast"));
        assertEquals(EPISODE_URL, action.getString("episode"));
        assertEquals(GUID, action.getString("guid"));
        assertEquals("play", action.getString("action"));
        assertEquals(DEVICE_ID, action.getString("device"));
        assertEquals(EPISODE_DURATION_MILLIS / 1000, action.getInt("started"));
        assertEquals(EPISODE_DURATION_MILLIS / 1000, action.getInt("position"));
        assertEquals(EPISODE_DURATION_MILLIS / 1000, action.getInt("total"));
        assertEquals(400, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void firstSyncDoesNotUploadUnplayedEpisodes() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        serveGpodderChanges(100, "[]", "[]", 300, "[]");
        serveGpodderUploads(200, 400);

        assertEquals(Result.success(), runSync());

        assertEquals(0, server.requests("POST", GPODDER_EPISODES_PATH).size());
        assertEquals(300, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void remotePlayPositionIsStoredForKnownEpisode() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160,
                actions(remotePlay(EPISODE_URL, GUID, "2024-01-01T00:00:00", 40)));

        assertEquals(Result.success(), runSync());

        FeedMedia media = storedMedia();
        assertEquals(40_000, media.getPosition());
        assertFalse(media.getItem().isPlayed());
    }

    @Test
    public void remotePlayPositionWithinSmartMarkThresholdOfTheEndMarksEpisodeAsPlayed() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        markSyncedBefore(50, 60);
        int thresholdSeconds = UserPreferences.getSmartMarkAsPlayedSecs();
        int position = EPISODE_DURATION_MILLIS / 1000 - thresholdSeconds;
        serveGpodderChanges(150, "[]", "[]", 160,
                actions(remotePlay(EPISODE_URL, GUID, "2024-01-01T00:00:00", position)));

        assertEquals(Result.success(), runSync());

        FeedMedia media = storedMedia();
        assertTrue(media.getItem().isPlayed());
        assertEquals(0, media.getPosition());
    }

    @Test
    public void remotePlayPositionJustBeforeSmartMarkThresholdOnlyStoresPosition() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        markSyncedBefore(50, 60);
        int position = EPISODE_DURATION_MILLIS / 1000 - UserPreferences.getSmartMarkAsPlayedSecs() - 1;
        serveGpodderChanges(150, "[]", "[]", 160,
                actions(remotePlay(EPISODE_URL, GUID, "2024-01-01T00:00:00", position)));

        assertEquals(Result.success(), runSync());

        FeedMedia media = storedMedia();
        assertFalse(media.getItem().isPlayed());
        assertEquals(position * 1000, media.getPosition());
    }

    @Test
    public void remotePlayPositionThatMarksEpisodeAsPlayedRemovesItFromTheQueue() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        FeedItem queuedItem = DBReader.getFeedItemByGuidOrEpisodeUrl(GUID, EPISODE_URL);
        DBReader.loadFeedDataOfFeedItemList(Collections.singletonList(queuedItem));
        DBWriter.addQueueItem(context, queuedItem).get();
        assertEquals(1, DBReader.getQueue().size());
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160,
                actions(remotePlay(EPISODE_URL, GUID, "2024-01-01T00:00:00", 99)));

        assertEquals(Result.success(), runSync());

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> DBReader.getQueue().isEmpty() && storedMedia().getItem().isPlayed());
    }

    @Test
    public void remoteActionForUnknownEpisodeIsIgnored() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160,
                actions(remotePlay("https://cdn.example/unknown.mp3", "unknown-guid", "2024-01-01T00:00:00", 40)));

        assertEquals(Result.success(), runSync());

        assertEquals(0, storedMedia().getPosition());
        assertEquals(160, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void remoteActionWithPlaceholderGuidIsMatchedByEpisodeUrl() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160,
                actions(remotePlay(EPISODE_URL, "null", "2024-01-01T00:00:00", 25)));

        assertEquals(Result.success(), runSync());

        assertEquals(25_000, storedMedia().getPosition());
    }

    @Test
    public void remoteActionForEpisodeWithoutMediaIsIgnored() throws Exception {
        storeFeedWithMedialessEpisode(feedUrl, GUID);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160,
                actions(remotePlay("https://cdn.example/none.mp3", GUID, "2024-01-01T00:00:00", 25)));

        assertEquals(Result.success(), runSync());

        FeedItem item = DBReader.getFeedItemByGuidOrEpisodeUrl(GUID, "https://cdn.example/none.mp3");
        assertNotNull(item);
        assertNull(item.getMedia());
        assertEquals(160, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void remoteNewDownloadAndDeleteActionsDoNotChangeLocalEpisodes() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160,
                actions(remoteAction("new"), remoteAction("download"), remoteAction("delete")));

        assertEquals(Result.success(), runSync());

        FeedMedia media = storedMedia();
        assertEquals(0, media.getPosition());
        assertFalse(media.getItem().isPlayed());
    }

    @Test
    public void mostRecentRemotePlayActionWinsRegardlessOfOrder() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160, actions(
                remotePlay(EPISODE_URL, GUID, "2024-01-03T00:00:00", 30),
                remotePlay(EPISODE_URL, GUID, "2024-01-02T00:00:00", 10),
                remotePlay(EPISODE_URL, GUID, "2024-01-01T00:00:00", 20)));

        assertEquals(Result.success(), runSync());

        assertEquals(30_000, storedMedia().getPosition());
    }

    @Test
    public void newerQueuedLocalPlayActionIsNotOverriddenByOlderRemoteAction() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        queueStorage.enqueueEpisodeAction(localPlay(55, JANUARY_FIRST_2024 + 86_400_000L));
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160,
                actions(remotePlay(EPISODE_URL, GUID, "2024-01-01T00:00:00", 40)));
        serveGpodderUploads(250, 260);

        assertEquals(Result.success(), runSync());

        assertEquals(0, storedMedia().getPosition());
        assertEquals(55, episodeUpload().getJSONObject(0).getInt("position"));
    }

    @Test
    public void olderQueuedLocalPlayActionIsOverriddenByNewerRemoteAction() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        queueStorage.enqueueEpisodeAction(localPlay(55, JANUARY_FIRST_2024));
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160,
                actions(remotePlay(EPISODE_URL, GUID, "2024-01-02T00:00:00", 40)));
        serveGpodderUploads(250, 260);

        assertEquals(Result.success(), runSync());

        assertEquals(40_000, storedMedia().getPosition());
    }

    @Test
    public void queuedEpisodeActionsAreUploadedAndTheQueueIsCleared() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        queueStorage.enqueueEpisodeAction(localPlay(12, JANUARY_FIRST_2024));
        queueStorage.enqueueEpisodeAction(localPlay(34, JANUARY_FIRST_2024 + 1000));
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160, "[]");
        serveGpodderUploads(250, 260);

        assertEquals(Result.success(), runSync());

        JSONArray upload = episodeUpload();
        assertEquals(2, upload.length());
        assertEquals(12, upload.getJSONObject(0).getInt("position"));
        assertEquals(34, upload.getJSONObject(1).getInt("position"));
        assertTrue(queueStorage.getQueuedEpisodeActions().isEmpty());
        assertEquals(260, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
        assertEquals(R.string.sync_status_success, lastStatusMessage());
    }

    @Test
    public void failedEpisodeActionUploadKeepsTheQueueAndTimestampAndRetries() throws Exception {
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        queueStorage.enqueueEpisodeAction(localPlay(12, JANUARY_FIRST_2024));
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160, "[]");
        server.route("POST", GPODDER_EPISODES_PATH, new MockResponse().setResponseCode(500));

        Result result = runSync();

        assertEquals(Result.retry(), result);
        assertEquals(1, queueStorage.getQueuedEpisodeActions().size());
        assertEquals(60, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
        assertEquals(R.string.sync_status_error, lastStatusMessage());
    }

    @Test
    public void nextcloudSyncTransfersSubscriptionsAndEpisodeActionsWithBasicAuthentication() throws Exception {
        useNextcloud();
        storeFeed(feedUrl, GUID, EPISODE_URL, false);
        queueStorage.enqueueFeedRemoved("https://old.example/feed.xml");
        queueStorage.enqueueEpisodeAction(localPlay(12, JANUARY_FIRST_2024));
        markSyncedBefore(50, 60);
        server.routeJson("GET", NEXTCLOUD_SUBSCRIPTIONS_PATH, "{\"timestamp\":150,\"add\":[],\"remove\":[]}");
        server.routeJson("POST", NEXTCLOUD_SUBSCRIPTION_UPLOAD_PATH, "{}");
        server.routeJson("GET", NEXTCLOUD_EPISODES_PATH, "{\"timestamp\":160,\"actions\":["
                + remotePlay(EPISODE_URL, GUID, "2024-01-02T00:00:00", 40) + "]}");
        server.routeJson("POST", NEXTCLOUD_EPISODE_UPLOAD_PATH, "{}");
        long syncStartSeconds = System.currentTimeMillis() / 1000;

        assertEquals(Result.success(), runSync());

        assertEquals(0, server.requests("POST", GPODDER_LOGIN_PATH).size());
        List<RecordedRequest> requests = server.requests();
        assertEquals(4, requests.size());
        List<String> requestedPaths = new ArrayList<>();
        for (RecordedRequest request : requests) {
            assertEquals("Basic YWxpY2U6c2VjcmV0", request.getHeader("Authorization"));
            requestedPaths.add(request.getMethod() + " " + request.getPath().split("\\?")[0]);
        }
        assertTrue(requestedPaths.contains("GET " + NEXTCLOUD_SUBSCRIPTIONS_PATH));
        assertTrue(requestedPaths.contains("POST " + NEXTCLOUD_SUBSCRIPTION_UPLOAD_PATH));
        assertTrue(requestedPaths.contains("GET " + NEXTCLOUD_EPISODES_PATH));
        assertTrue(requestedPaths.contains("POST " + NEXTCLOUD_EPISODE_UPLOAD_PATH));
        assertEquals(40_000, storedMedia().getPosition());
        assertTrue(queueStorage.getQueuedEpisodeActions().isEmpty());
        assertTrue(queueStorage.getQueuedRemovedFeeds().isEmpty());
        assertTrue(SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp() >= syncStartSeconds);
        assertTrue(SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp() >= syncStartSeconds);
    }

    @Test
    public void nextcloudServerErrorMakesSyncRetry() throws Exception {
        useNextcloud();
        markSyncedBefore(50, 60);
        server.route("GET", NEXTCLOUD_SUBSCRIPTIONS_PATH, new MockResponse().setResponseCode(503));

        assertEquals(Result.retry(), runSync());

        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
    }

    private static class NoOpAutoDownloadManager extends AutoDownloadManager {
        @Override
        public Future<?> autodownloadUndownloadedItems(Context context) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void performAutoCleanup(Context context) {
        }
    }
}
