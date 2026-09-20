package de.danoeh.antennapod.net.sync.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.impl.WorkManagerImpl;
import de.danoeh.antennapod.event.SyncServiceEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class SynchronizationQueueImplTest {
    private static final String SETTINGS_PREFERENCES = "synchronization";
    private static final String WORK_ID = "SyncServiceWorkId";
    private static final String FEED_URL = "https://a.example/feed.xml";
    private static final String EPISODE_URL = "https://a.example/1.mp3";
    private Context context;
    private SynchronizationQueueImpl queue;
    private SynchronizationQueueStorage storage;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        WorkManagerImpl.setDelegate(null);
        WorkManager.initialize(context, new Configuration.Builder().setExecutor(Runnable::run).build());
        WorkManager.getInstance(context).cancelAllWork().getResult().get();
        WorkManager.getInstance(context).pruneWork().getResult().get();
        UserPreferences.init(context);
        SynchronizationSettings.init(context);
        NetworkUtils.init(context);
        storage = new SynchronizationQueueStorage(context);
        storage.clearQueue();
        queue = new SynchronizationQueueImpl(context);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().removeAllStickyEvents();
    }

    private void connectProvider() {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.getIdentifier());
    }

    private void lastAttemptWasMinutesAgo(int minutes) {
        long attempt = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutes);
        context.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putLong(SynchronizationSettings.LAST_SYNC_ATTEMPT_TIMESTAMP, attempt)
                .commit();
    }

    private List<WorkInfo> syncWork() throws Exception {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_ID).get();
    }

    private WorkInfo singleSyncWork() throws Exception {
        List<WorkInfo> work = syncWork();
        assertEquals(1, work.size());
        return work.get(0);
    }

    private static FeedMedia playedMedia(int startPosition, int position, int duration) {
        Feed feed = new Feed(FEED_URL, null, "Feed");
        feed.setState(Feed.STATE_SUBSCRIBED);
        FeedItem item = new FeedItem();
        item.setItemIdentifier("guid-1");
        item.setFeed(feed);
        FeedMedia media = new FeedMedia(item, EPISODE_URL, 1000, "audio/mpeg");
        item.setMedia(media);
        media.setPosition(startPosition);
        media.onPlaybackStart();
        media.setPosition(position);
        media.setDuration(duration);
        return media;
    }

    @Test
    public void queueingChangesIsIgnoredWhileNoProviderIsConnected() throws Exception {
        queue.enqueueFeedAdded(FEED_URL);
        queue.enqueueFeedRemoved(EPISODE_URL);
        queue.enqueueEpisodeAction(new EpisodeAction.Builder(FEED_URL, EPISODE_URL, EpisodeAction.PLAY)
                .currentTimestamp().build());

        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        assertTrue(syncWork().isEmpty());
    }

    @Test
    public void enqueuedFeedChangesArePersistedAndScheduleASync() throws Exception {
        connectProvider();

        queue.enqueueFeedAdded(FEED_URL);
        queue.enqueueFeedRemoved("https://b.example/feed.xml");

        assertEquals(Collections.singletonList(FEED_URL), storage.getQueuedAddedFeeds());
        assertEquals(Collections.singletonList("https://b.example/feed.xml"), storage.getQueuedRemovedFeeds());
        assertEquals(WorkInfo.State.ENQUEUED, singleSyncWork().getState());
    }

    @Test
    public void enqueuedEpisodeActionIsPersistedAndSchedulesASync() throws Exception {
        connectProvider();
        EpisodeAction action = new EpisodeAction.Builder(FEED_URL, EPISODE_URL, EpisodeAction.PLAY)
                .currentTimestamp().started(1).position(2).total(3).build();

        queue.enqueueEpisodeAction(action);

        List<EpisodeAction> queued = storage.getQueuedEpisodeActions();
        assertEquals(1, queued.size());
        assertEquals(EPISODE_URL, queued.get(0).getEpisode());
        assertEquals(2, queued.get(0).getPosition());
        assertEquals(WorkInfo.State.ENQUEUED, singleSyncWork().getState());
    }

    @Test
    public void syncIsScheduledWithDelayAndUnmeteredNetworkConstraintByDefault() throws Exception {
        queue.sync();

        WorkInfo work = singleSyncWork();
        Constraints constraints = work.getConstraints();
        assertEquals(NetworkType.UNMETERED, constraints.getRequiredNetworkType());
        assertEquals(TimeUnit.SECONDS.toMillis(20), work.getInitialDelayMillis());
    }

    @Test
    public void syncAllowsAnyConnectedNetworkWhenMobileSyncIsEnabled() throws Exception {
        UserPreferences.setAllowMobileSync(true);

        queue.sync();

        assertEquals(NetworkType.CONNECTED, singleSyncWork().getConstraints().getRequiredNetworkType());
    }

    @Test
    public void syncImmediatelyRunsWithoutInitialDelay() throws Exception {
        queue.syncImmediately();

        assertEquals(0, singleSyncWork().getInitialDelayMillis());
    }

    @Test
    public void schedulingAgainReplacesThePendingSync() throws Exception {
        queue.sync();
        queue.syncImmediately();

        WorkInfo work = singleSyncWork();
        assertEquals(0, work.getInitialDelayMillis());
        assertEquals(1, syncWork().size());
    }

    @Test
    public void syncIfNotSyncedRecentlyDoesNothingWithinTenMinutesOfTheLastAttempt() throws Exception {
        lastAttemptWasMinutesAgo(9);

        queue.syncIfNotSyncedRecently();

        assertTrue(syncWork().isEmpty());
    }

    @Test
    public void syncIfNotSyncedRecentlySchedulesASyncOnceTenMinutesHavePassedSinceTheLastAttempt() throws Exception {
        lastAttemptWasMinutesAgo(11);

        queue.syncIfNotSyncedRecently();

        assertEquals(1, syncWork().size());
    }

    @Test
    public void syncIfNotSyncedRecentlySchedulesASyncWhenNoAttemptWasEverMade() throws Exception {
        queue.syncIfNotSyncedRecently();

        assertEquals(1, syncWork().size());
    }

    private void connect(int networkType, int transport) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        ShadowConnectivityManager shadowConnectivityManager = Shadow.extract(connectivityManager);
        shadowConnectivityManager.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED, networkType, 0, true, NetworkInfo.State.CONNECTED));
        NetworkCapabilities capabilities = ShadowNetworkCapabilities.newInstance();
        ShadowNetworkCapabilities shadowCapabilities = Shadow.extract(capabilities);
        shadowCapabilities.addTransportType(transport);
        shadowConnectivityManager.setNetworkCapabilities(connectivityManager.getActiveNetwork(), capabilities);
    }

    private int announcedStatus() {
        return EventBus.getDefault().getStickyEvent(SyncServiceEvent.class).getMessageResId();
    }

    @Test
    public void syncAnnouncesItsStartWhenTheNetworkIsUnrestricted() {
        connect(ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI);

        queue.sync();

        assertEquals(R.string.sync_status_started, announcedStatus());
    }

    @Test
    public void syncAnnouncesWaitingForWifiWhenOnlyMobileDataIsAvailableAndNotAllowed() {
        connect(ConnectivityManager.TYPE_MOBILE, NetworkCapabilities.TRANSPORT_CELLULAR);

        queue.sync();

        assertEquals(R.string.sync_status_wait_for_wifi, announcedStatus());
    }

    @Test
    public void syncAnnouncesItsStartOnMobileDataWhenMobileSyncIsAllowed() {
        connect(ConnectivityManager.TYPE_MOBILE, NetworkCapabilities.TRANSPORT_CELLULAR);
        UserPreferences.setAllowMobileSync(true);

        queue.sync();

        assertEquals(R.string.sync_status_started, announcedStatus());
    }

    @Test
    public void fullSyncResetsTimestampsAndSyncsImmediately() throws Exception {
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(111);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(222);

        queue.fullSync();

        assertEquals(0, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
        assertEquals(0, singleSyncWork().getInitialDelayMillis());
    }

    @Test
    public void clearEmptiesTheQueuesAndResetsTimestamps() {
        connectProvider();
        queue.enqueueFeedAdded(FEED_URL);
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(111);

        queue.clear();

        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertEquals(0, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
    }

    @Test
    public void completedPlaybackIsQueuedWithFullDurationAsPosition() {
        connectProvider();

        queue.enqueueEpisodePlayed(playedMedia(10_000, 20_000, 100_000), true);

        List<EpisodeAction> queued = storage.getQueuedEpisodeActions();
        assertEquals(1, queued.size());
        EpisodeAction action = queued.get(0);
        assertEquals(EpisodeAction.PLAY, action.getAction());
        assertEquals(FEED_URL, action.getPodcast());
        assertEquals(EPISODE_URL, action.getEpisode());
        assertEquals("guid-1", action.getGuid());
        assertEquals(10, action.getStarted());
        assertEquals(100, action.getPosition());
        assertEquals(100, action.getTotal());
    }

    @Test
    public void partialPlaybackIsQueuedWithCurrentPosition() {
        connectProvider();

        queue.enqueueEpisodePlayed(playedMedia(10_000, 45_000, 100_000), false);

        List<EpisodeAction> queued = storage.getQueuedEpisodeActions();
        assertEquals(1, queued.size());
        assertEquals(10, queued.get(0).getStarted());
        assertEquals(45, queued.get(0).getPosition());
        assertEquals(100, queued.get(0).getTotal());
    }

    @Test
    public void playbackThatDidNotAdvanceIsNotQueuedUnlessItCompleted() {
        connectProvider();

        queue.enqueueEpisodePlayed(playedMedia(30_000, 30_000, 100_000), false);
        assertTrue(storage.getQueuedEpisodeActions().isEmpty());

        queue.enqueueEpisodePlayed(playedMedia(30_000, 30_000, 100_000), true);
        assertEquals(1, storage.getQueuedEpisodeActions().size());
    }

    @Test
    public void playbackOfEpisodeThatWasNeverStartedIsNotQueued() {
        connectProvider();
        FeedMedia media = playedMedia(10_000, 20_000, 100_000);
        FeedItem item = media.getItem();
        FeedMedia neverStarted = new FeedMedia(item, EPISODE_URL, 1000, "audio/mpeg");
        item.setMedia(neverStarted);
        neverStarted.setPosition(20_000);

        queue.enqueueEpisodePlayed(neverStarted, true);

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
    }

    @Test
    public void playbackOfUnsubscribedOrLocalFeedsIsNotQueued() {
        connectProvider();
        FeedMedia unsubscribed = playedMedia(10_000, 20_000, 100_000);
        unsubscribed.getItem().getFeed().setState(Feed.STATE_NOT_SUBSCRIBED);
        FeedMedia local = playedMedia(10_000, 20_000, 100_000);
        local.getItem().getFeed().setDownloadUrl(Feed.PREFIX_LOCAL_FOLDER + "folder");

        queue.enqueueEpisodePlayed(unsubscribed, true);
        queue.enqueueEpisodePlayed(local, true);

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
    }

    @Test
    public void playbackOfMediaWithoutEpisodeIsNotQueued() {
        connectProvider();
        FeedMedia orphan = new FeedMedia(null, EPISODE_URL, 1000, "audio/mpeg");

        queue.enqueueEpisodePlayed(orphan, true);

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
    }
}
