package de.danoeh.antennapod.net.sync.service;

import androidx.work.ListenableWorker.Result;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class SyncServiceSubscriptionsTest extends SyncServiceTestBase {

    private static String jsonArray(String... values) {
        List<String> quoted = new ArrayList<>();
        for (String value : values) {
            quoted.add("\"" + value + "\"");
        }
        return "[" + String.join(",", quoted) + "]";
    }

    private JSONObject subscriptionUpload() throws Exception {
        List<RecordedRequest> uploads = server.requests("POST", GPODDER_SUBSCRIPTIONS_PATH);
        assertEquals(1, uploads.size());
        return FakeSyncServer.bodyOf(uploads.get(0));
    }

    @Test
    public void syncWithoutSelectedProviderSucceedsWithoutContactingAnyServer() throws Exception {
        assertEquals(Result.success(), runSync());

        assertEquals(0, server.requests().size());
        assertEquals(0, SynchronizationSettings.getLastSyncAttempt());
    }

    @Test
    public void firstSyncUploadsAllLocalSubscriptionsAndStoresServerTimestamps() throws Exception {
        useGpodder();
        String localFeed = server.feedUrl("local");
        storeFeed(localFeed, "guid-1", "https://cdn.example/1.mp3", false);
        serveGpodderChanges(100, "[]", "[]", 300, "[]");
        serveGpodderUploads(200, 400);

        Result result = runSync();

        assertEquals(Result.success(), result);
        JSONObject upload = subscriptionUpload();
        assertEquals(Collections.singletonList(localFeed), FakeSyncServer.stringsOf(upload.getJSONArray("add")));
        assertEquals(0, upload.getJSONArray("remove").length());
        assertEquals(200, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(300, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
        assertTrue(SynchronizationSettings.isLastSyncSuccessful());
        assertTrue(SynchronizationSettings.getLastSyncAttempt() > 0);
        assertEquals(R.string.sync_status_success, lastStatusMessage());
        assertEquals(0, feedUpdateManager.runOnceCount);
    }

    @Test
    public void laterSyncOnlyRequestsChangesSinceTheStoredTimestamp() throws Exception {
        useGpodder();
        storeFeed(server.feedUrl("local"), "guid-1", "https://cdn.example/1.mp3", false);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160, "[]");

        assertEquals(Result.success(), runSync());

        assertEquals("/api/2/subscriptions/alice/device1.json?since=50",
                server.requests("GET", GPODDER_SUBSCRIPTIONS_PATH).get(0).getPath());
        assertEquals("/api/2/episodes/alice.json?since=60",
                server.requests("GET", GPODDER_EPISODES_PATH).get(0).getPath());
        assertEquals(0, server.requests("POST", GPODDER_SUBSCRIPTIONS_PATH).size());
        assertEquals(150, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(160, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void remoteAdditionCreatesFeedAndDefersEpisodeSyncUntilItWasRefreshed() throws Exception {
        useGpodder();
        String remoteFeed = server.feedUrl("remote");
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, jsonArray(remoteFeed), "[]", 160, "[]");

        Result result = runSync();

        assertEquals(Result.success(), result);
        assertEquals(Collections.singletonList(remoteFeed), storedFeedUrls());
        assertEquals(1, feedUpdateManager.runOnceCount);
        assertEquals(0, server.requests("GET", GPODDER_EPISODES_PATH).size());
        assertEquals(150, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(60, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
        assertEquals(R.string.sync_status_wait_for_downloads, lastStatusMessage());
    }

    @Test
    public void remoteAdditionMatchingLocalSubscriptionIsNotDuplicated() throws Exception {
        useGpodder();
        String localFeed = server.feedUrl("local");
        storeFeed(localFeed, "guid-1", "https://cdn.example/1.mp3", false);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, jsonArray(localFeed.replace("local.xml", "LOCAL.xml")), "[]", 160, "[]");

        assertEquals(Result.success(), runSync());

        assertEquals(Collections.singletonList(localFeed), storedFeedUrls());
        assertEquals(0, feedUpdateManager.runOnceCount);
    }

    @Test
    public void remoteAdditionRedirectingPermanentlyToLocalSubscriptionIsNotDuplicated() throws Exception {
        useGpodder();
        String localFeed = server.feedUrl("local");
        String oldAlias = server.feedUrl("old-alias");
        storeFeed(localFeed, "guid-1", "https://cdn.example/1.mp3", false);
        server.route("HEAD", "/feeds/old-alias.xml",
                new MockResponse().setResponseCode(301).addHeader("Location", "/feeds/local.xml"));
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, jsonArray(oldAlias), "[]", 160, "[]");

        assertEquals(Result.success(), runSync());

        assertEquals(Collections.singletonList(localFeed), storedFeedUrls());
    }

    @Test
    public void remoteAdditionRedirectingTemporarilyToLocalSubscriptionIsNotDuplicated() throws Exception {
        useGpodder();
        String localFeed = server.feedUrl("local");
        String alias = server.feedUrl("alias");
        storeFeed(localFeed, "guid-1", "https://cdn.example/1.mp3", false);
        server.route("HEAD", "/feeds/alias.xml",
                new MockResponse().setResponseCode(302).addHeader("Location", "/feeds/local.xml"));
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, jsonArray(alias), "[]", 160, "[]");

        assertEquals(Result.success(), runSync());

        assertEquals(Collections.singletonList(localFeed), storedFeedUrls());
    }

    @Test
    public void remoteAdditionWithoutHttpSchemeIsSkipped() throws Exception {
        useGpodder();
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, jsonArray("ftp://files.example/feed.xml"), "[]", 160, "[]");

        assertEquals(Result.success(), runSync());

        assertTrue(storedFeedUrls().isEmpty());
        assertEquals(0, feedUpdateManager.runOnceCount);
    }

    @Test
    public void remoteAdditionOfFeedRemovedLocallyIsSkippedAndRemovalIsUploaded() throws Exception {
        useGpodder();
        String feedUrl = server.feedUrl("removed");
        queueStorage.enqueueFeedRemoved(feedUrl);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, jsonArray(feedUrl), "[]", 160, "[]");
        serveGpodderUploads(250, 260);

        assertEquals(Result.success(), runSync());

        assertTrue(storedFeedUrls().isEmpty());
        JSONObject upload = subscriptionUpload();
        assertEquals(Collections.singletonList(feedUrl), FakeSyncServer.stringsOf(upload.getJSONArray("remove")));
        assertEquals(0, upload.getJSONArray("add").length());
        assertEquals(250, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
    }

    @Test
    public void remoteRemovalDeletesLocalFeed() throws Exception {
        useGpodder();
        String keptFeed = server.feedUrl("kept");
        String removedFeed = server.feedUrl("removed");
        storeFeed(keptFeed, "guid-1", "https://cdn.example/1.mp3", false);
        storeFeed(removedFeed, "guid-2", "https://cdn.example/2.mp3", false);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", jsonArray(removedFeed), 160, "[]");

        assertEquals(Result.success(), runSync());

        assertEquals(Collections.singletonList(keptFeed), storedFeedUrls());
        assertEquals(0, server.requests("POST", GPODDER_SUBSCRIPTIONS_PATH).size());
    }

    @Test
    public void remoteRemovalOfFeedThatWasAddedAgainLocallyKeepsFeedAndUploadsAddition() throws Exception {
        useGpodder();
        String feedUrl = server.feedUrl("resubscribed");
        storeFeed(feedUrl, "guid-1", "https://cdn.example/1.mp3", false);
        queueStorage.enqueueFeedAdded(feedUrl);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", jsonArray(feedUrl), 160, "[]");
        serveGpodderUploads(250, 260);

        assertEquals(Result.success(), runSync());

        assertEquals(Collections.singletonList(feedUrl), storedFeedUrls());
        assertEquals(Collections.singletonList(feedUrl),
                FakeSyncServer.stringsOf(subscriptionUpload().getJSONArray("add")));
    }

    @Test
    public void queuedLocalSubscriptionChangesAreUploadedAndTheQueueIsCleared() throws Exception {
        useGpodder();
        queueStorage.enqueueFeedAdded("https://new.example/feed.xml");
        queueStorage.enqueueFeedRemoved("https://old.example/feed.xml");
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160, "[]");
        serveGpodderUploads(250, 260);

        assertEquals(Result.success(), runSync());

        JSONObject upload = subscriptionUpload();
        assertEquals(Collections.singletonList("https://new.example/feed.xml"),
                FakeSyncServer.stringsOf(upload.getJSONArray("add")));
        assertEquals(Collections.singletonList("https://old.example/feed.xml"),
                FakeSyncServer.stringsOf(upload.getJSONArray("remove")));
        assertTrue(queueStorage.getQueuedAddedFeeds().isEmpty());
        assertTrue(queueStorage.getQueuedRemovedFeeds().isEmpty());
        assertEquals(250, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
    }

    @Test
    public void localChangesAlreadyKnownToTheServerAreNotUploadedAgain() throws Exception {
        useGpodder();
        String feedUrl = "https://both.example/feed.xml";
        queueStorage.enqueueFeedAdded(feedUrl);
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160, "[]");
        server.routeJson("GET", GPODDER_SUBSCRIPTIONS_PATH,
                "{\"timestamp\":150,\"add\":[\"" + feedUrl + "\"],\"remove\":[]}");

        assertEquals(Result.success(), runSync());

        assertEquals(0, server.requests("POST", GPODDER_SUBSCRIPTIONS_PATH).size());
        assertTrue(queueStorage.getQueuedAddedFeeds().isEmpty());
    }

    @Test
    public void failedSubscriptionUploadIsRetriedAndReportedAsError() throws Exception {
        useGpodder();
        queueStorage.enqueueFeedAdded("https://new.example/feed.xml");
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, "[]", "[]", 160, "[]");
        server.route("POST", GPODDER_SUBSCRIPTIONS_PATH, new MockResponse().setResponseCode(500));

        Result result = runSync();

        assertEquals(Result.retry(), result);
        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
        assertEquals(R.string.sync_status_error, lastStatusMessage());
        assertEquals(50, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(0, server.requests("GET", GPODDER_EPISODES_PATH).size());
    }

    @Test
    public void rejectedCredentialsFailLoginAndRetryWithoutSyncingAnything() throws Exception {
        useGpodder();
        server.route("POST", GPODDER_LOGIN_PATH, new MockResponse().setResponseCode(401));

        Result result = runSync();

        assertEquals(Result.retry(), result);
        assertEquals(R.string.sync_status_error, lastStatusMessage());
        assertEquals(0, server.requests("GET", GPODDER_SUBSCRIPTIONS_PATH).size());
        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
    }

    @Test
    public void errorMessageIsOnlyShownOnEveryThirdFailedAttempt() throws Exception {
        useGpodder();
        server.route("POST", GPODDER_LOGIN_PATH, new MockResponse().setResponseCode(500));
        MessageCollector collector = new MessageCollector();
        EventBus.getDefault().register(collector);
        try {
            assertEquals(Result.retry(), newService(0).doWork());
            assertEquals(Result.retry(), newService(1).doWork());
            assertTrue(collector.messages.isEmpty());

            assertEquals(Result.retry(), newService(2).doWork());
        } finally {
            EventBus.getDefault().unregister(collector);
        }

        assertEquals(1, collector.messages.size());
        assertTrue(collector.messages.get(0).startsWith(context.getString(R.string.gpodnetsync_error_descr)));
    }

    @Test
    public void unexpectedFailureIsReportedImmediatelyAndNotRetried() throws Exception {
        useGpodder();
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, jsonArray(server.feedUrl("remote")), "[]", 160, "[]");
        feedUpdateManager.failure = new IllegalStateException("refresh unavailable");
        MessageCollector collector = new MessageCollector();
        EventBus.getDefault().register(collector);
        Result result;
        try {
            result = runSync();
        } finally {
            EventBus.getDefault().unregister(collector);
        }

        assertEquals(Result.failure(), result);
        assertEquals(Arrays.asList(context.getString(R.string.gpodnetsync_error_descr) + "refresh unavailable"),
                collector.messages);
        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
    }

    @Test
    public void newFeedCreatedFromRemoteAdditionIsStoredAsSubscribedPlaceholder() throws Exception {
        useGpodder();
        String remoteFeed = server.feedUrl("remote");
        markSyncedBefore(50, 60);
        serveGpodderChanges(150, jsonArray(remoteFeed), "[]", 160, "[]");

        assertEquals(Result.success(), runSync());

        List<Feed> feeds = DBReader.getFeedList();
        assertEquals(1, feeds.size());
        Feed stored = feeds.get(0);
        assertEquals(remoteFeed, stored.getDownloadUrl());
        assertEquals("Unknown podcast", stored.getTitle());
        assertEquals(Feed.STATE_SUBSCRIBED, stored.getState());
        assertEquals(0, stored.getLastRefreshAttempt());
    }

    public static class MessageCollector {
        final List<String> messages = new ArrayList<>();

        @Subscribe
        public void onMessage(MessageEvent event) {
            messages.add(event.message);
        }
    }
}
