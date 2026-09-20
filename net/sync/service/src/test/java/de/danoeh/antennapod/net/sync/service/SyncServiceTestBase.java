package de.danoeh.antennapod.net.sync.service;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.event.SyncServiceEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import okhttp3.mockwebserver.MockResponse;
import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class SyncServiceTestBase {
    static final String USERNAME = "alice";
    static final String PASSWORD = "secret";
    static final String DEVICE_ID = "device1";
    static final String GPODDER_LOGIN_PATH = "/api/2/auth/alice/login.json";
    static final String GPODDER_SUBSCRIPTIONS_PATH = "/api/2/subscriptions/alice/device1.json";
    static final String GPODDER_EPISODES_PATH = "/api/2/episodes/alice.json";
    static final String NEXTCLOUD_SUBSCRIPTIONS_PATH = "/index.php/apps/gpoddersync/subscriptions";
    static final String NEXTCLOUD_SUBSCRIPTION_UPLOAD_PATH =
            "/index.php/apps/gpoddersync/subscription_change/create";
    static final String NEXTCLOUD_EPISODES_PATH = "/index.php/apps/gpoddersync/episode_action";
    static final String NEXTCLOUD_EPISODE_UPLOAD_PATH = "/index.php/apps/gpoddersync/episode_action/create";
    static final int EPISODE_DURATION_MILLIS = 100_000;

    Context context;
    FakeSyncServer server;
    RecordingFeedUpdateManager feedUpdateManager;
    SynchronizationQueueStorage queueStorage;

    @Before
    public void setUpEnvironment() throws Exception {
        context = RuntimeEnvironment.getApplication();
        AntennapodHttpClient.setCacheDirectory(new File(context.getCacheDir(), "sync-service-test"));
        AntennapodHttpClient.setProxyConfig(null);
        AntennapodHttpClient.reinit();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        SynchronizationSettings.init(context);
        SynchronizationCredentials.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
        feedUpdateManager = new RecordingFeedUpdateManager();
        FeedUpdateManager.setInstance(feedUpdateManager);
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        queueStorage = new SynchronizationQueueStorage(context);
        queueStorage.clearQueue();
        server = new FakeSyncServer();
        server.start();
        SynchronizationCredentials.setHosturl(server.hostUrl());
        SynchronizationCredentials.setUsername(USERNAME);
        SynchronizationCredentials.setPassword(PASSWORD);
        SynchronizationCredentials.setDeviceId(DEVICE_ID);
    }

    @After
    public void tearDownEnvironment() throws Exception {
        server.stop();
        EventBus.getDefault().removeAllStickyEvents();
        FeedUpdateManager.setInstance(null);
        DownloadServiceInterface.setImpl(null);
        DBWriter.tearDownTests();
        PodDBAdapter.tearDownTests();
    }

    void useGpodder() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.getIdentifier());
        server.route("POST", GPODDER_LOGIN_PATH, new MockResponse());
    }

    void useNextcloud() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.NEXTCLOUD_GPODDER.getIdentifier());
    }

    void serveGpodderChanges(long subscriptionTimestamp, String subscriptionAddJson, String subscriptionRemoveJson,
                             long episodeTimestamp, String episodeActionsJson) {
        server.routeJson("GET", GPODDER_SUBSCRIPTIONS_PATH, "{\"timestamp\":" + subscriptionTimestamp
                + ",\"add\":" + subscriptionAddJson + ",\"remove\":" + subscriptionRemoveJson + "}");
        server.routeJson("GET", GPODDER_EPISODES_PATH,
                "{\"timestamp\":" + episodeTimestamp + ",\"actions\":" + episodeActionsJson + "}");
    }

    void serveGpodderUploads(long subscriptionUploadTimestamp, long episodeUploadTimestamp) {
        server.routeJson("POST", GPODDER_SUBSCRIPTIONS_PATH,
                "{\"timestamp\":" + subscriptionUploadTimestamp + ",\"update_urls\":[]}");
        server.routeJson("POST", GPODDER_EPISODES_PATH,
                "{\"timestamp\":" + episodeUploadTimestamp + ",\"update_urls\":[]}");
    }

    void markSyncedBefore(long subscriptionTimestamp, long episodeTimestamp) {
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(subscriptionTimestamp);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(episodeTimestamp);
    }

    SyncService newService(int runAttemptCount) {
        WorkerParameters parameters = mock(WorkerParameters.class);
        when(parameters.getRunAttemptCount()).thenReturn(runAttemptCount);
        return new SyncService(context, parameters);
    }

    Result runSync() {
        return newService(0).doWork();
    }

    Feed storeFeed(String feedUrl, String guid, String episodeUrl, boolean played) {
        Feed feed = new Feed(feedUrl, null, "Feed " + feedUrl);
        feed.setItems(new ArrayList<>());
        feed.setLastRefreshAttempt(System.currentTimeMillis());
        FeedItem item = new FeedItem();
        item.setItemIdentifier(guid);
        item.setTitle("Episode " + guid);
        item.setFeed(feed);
        FeedMedia media = new FeedMedia(item, episodeUrl, 1000, "audio/mpeg");
        media.setDuration(EPISODE_DURATION_MILLIS);
        item.setMedia(media);
        item.setPlayed(played);
        feed.getItems().add(item);
        return FeedDatabaseWriter.updateFeed(context, feed, false);
    }

    List<String> storedFeedUrls() {
        List<String> urls = new ArrayList<>();
        for (Feed feed : DBReader.getFeedList()) {
            urls.add(feed.getDownloadUrl());
        }
        return urls;
    }

    int lastStatusMessage() {
        return EventBus.getDefault().getStickyEvent(SyncServiceEvent.class).getMessageResId();
    }

    static class RecordingFeedUpdateManager extends FeedUpdateManager {
        int runOnceCount = 0;
        RuntimeException failure;

        @Override
        public void restartUpdateAlarm(Context context, boolean replace) {
        }

        @Override
        public void runOnce(Context context) {
            runOnceCount++;
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void runOnce(Context context, Feed feed) {
        }

        @Override
        public void runOnce(Context context, Feed feed, boolean nextPage) {
        }

        @Override
        public void runOnceOrAsk(@NonNull Context context) {
        }

        @Override
        public void runOnceOrAsk(@NonNull Context context, @Nullable Feed feed) {
        }
    }
}
