package de.danoeh.antennapod.net.sync.service;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import de.danoeh.antennapod.event.SyncServiceEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class SynchronizationQueueImplTest {
    private static final String WORK_ID = "SyncServiceWorkId";
    private static final String FEED_URL = "http://feed.example/rss";

    private Context context;
    private SynchronizationQueueImpl queue;
    private SynchronizationQueueStorage storage;
    private WorkManager workManager;
    private MockedStatic<WorkManager> workManagerStatic;
    private MockedStatic<UserPreferences> userPreferences;
    private MockedStatic<NetworkUtils> networkUtils;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        SynchronizationSettings.init(context);
        storage = new SynchronizationQueueStorage(context);
        queue = new SynchronizationQueueImpl(context);
        workManager = mock(WorkManager.class);
        workManagerStatic = mockStatic(WorkManager.class);
        workManagerStatic.when(() -> WorkManager.getInstance(context)).thenReturn(workManager);
        userPreferences = mockStatic(UserPreferences.class);
        userPreferences.when(UserPreferences::isAllowMobileSync).thenReturn(false);
        networkUtils = mockStatic(NetworkUtils.class);
        networkUtils.when(NetworkUtils::isNetworkRestricted).thenReturn(false);
        EventBus.getDefault().removeAllStickyEvents();
    }

    @After
    public void tearDown() {
        workManagerStatic.close();
        userPreferences.close();
        networkUtils.close();
        EventBus.getDefault().removeAllStickyEvents();
    }

    private void connectProvider() {
        SynchronizationSettings.setSelectedSyncProvider("GPODDER_NET");
    }

    private OneTimeWorkRequest capturedWorkRequest() {
        ArgumentCaptor<OneTimeWorkRequest> request = ArgumentCaptor.forClass(OneTimeWorkRequest.class);
        verify(workManager).enqueueUniqueWork(eq(WORK_ID), eq(ExistingWorkPolicy.REPLACE), request.capture());
        return request.getValue();
    }

    private static FeedMedia playedMedia(Feed feed, int durationMs, int startMs, int positionMs) {
        FeedItem item = new FeedItem(1, "Episode", "guid-1", "http://link.example", new Date(), 0, feed);
        FeedMedia media = new FeedMedia(item, "http://feed.example/episode.mp3", 0, "audio/mp3");
        item.setMedia(media);
        media.setDuration(durationMs);
        media.setPosition(startMs);
        media.onPlaybackStart();
        media.setPosition(positionMs);
        return media;
    }

    private static Feed subscribedFeed() {
        Feed feed = new Feed(FEED_URL, null, "Feed");
        feed.setState(Feed.STATE_SUBSCRIBED);
        return feed;
    }

    @Test
    public void syncSchedulesUniqueWorkWithShortDelayOnUnmeteredNetworks() {
        queue.sync();

        OneTimeWorkRequest request = capturedWorkRequest();
        assertEquals(TimeUnit.SECONDS.toMillis(20), request.getWorkSpec().initialDelay);
        assertEquals(NetworkType.UNMETERED, request.getWorkSpec().constraints.getRequiredNetworkType());
        assertEquals(BackoffPolicy.EXPONENTIAL, request.getWorkSpec().backoffPolicy);
        assertEquals(TimeUnit.MINUTES.toMillis(10), request.getWorkSpec().backoffDelayDuration);
    }

    @Test
    public void syncAcceptsAnyConnectionWhenMobileSyncIsAllowed() {
        userPreferences.when(UserPreferences::isAllowMobileSync).thenReturn(true);

        queue.sync();

        assertEquals(NetworkType.CONNECTED, capturedWorkRequest().getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test
    public void syncAnnouncesStartAsStickyEvent() {
        queue.sync();

        SyncServiceEvent event = EventBus.getDefault().getStickyEvent(SyncServiceEvent.class);
        assertNotNull(event);
        assertEquals(R.string.sync_status_started, event.getMessageResId());
    }

    @Test
    public void syncAnnouncesWaitingForWifiWhenNetworkIsRestrictedAndMobileSyncIsNotAllowed() {
        networkUtils.when(NetworkUtils::isNetworkRestricted).thenReturn(true);

        queue.sync();

        assertEquals(R.string.sync_status_wait_for_wifi,
                EventBus.getDefault().getStickyEvent(SyncServiceEvent.class).getMessageResId());
    }

    @Test
    public void syncDoesNotWaitForWifiWhenMobileSyncIsAllowedOnRestrictedNetwork() {
        userPreferences.when(UserPreferences::isAllowMobileSync).thenReturn(true);
        networkUtils.when(NetworkUtils::isNetworkRestricted).thenReturn(true);

        queue.sync();

        assertEquals(R.string.sync_status_started,
                EventBus.getDefault().getStickyEvent(SyncServiceEvent.class).getMessageResId());
    }

    @Test
    public void syncImmediatelyRemovesTheInitialDelay() {
        queue.syncImmediately();

        assertEquals(0, capturedWorkRequest().getWorkSpec().initialDelay);
    }

    @Test
    public void syncIfNotSyncedRecentlySyncsAfterTenMinutesWithoutAttempt() {
        context.getSharedPreferences("synchronization", Context.MODE_PRIVATE).edit()
                .putLong(SynchronizationSettings.LAST_SYNC_ATTEMPT_TIMESTAMP,
                        System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(11))
                .apply();

        queue.syncIfNotSyncedRecently();

        verify(workManager).enqueueUniqueWork(eq(WORK_ID), eq(ExistingWorkPolicy.REPLACE),
                any(OneTimeWorkRequest.class));
    }

    @Test
    public void syncIfNotSyncedRecentlyDoesNothingRightAfterAnAttempt() {
        SynchronizationSettings.updateLastSynchronizationAttempt();

        queue.syncIfNotSyncedRecently();

        verifyNoInteractions(workManager);
    }

    @Test
    public void fullSyncResetsTimestampsAndSyncsImmediately() {
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(5);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(6);

        queue.fullSync();

        assertEquals(0, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
        assertEquals(0, capturedWorkRequest().getWorkSpec().initialDelay);
    }

    @Test
    public void clearEmptiesQueuesAndResetsTimestampsWithoutSyncing() {
        storage.enqueueFeedAdded(FEED_URL);
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(5);

        queue.clear();

        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertEquals(0, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        verifyNoInteractions(workManager);
    }

    @Test
    public void enqueueFeedAddedStoresFeedAndSchedulesSync() {
        connectProvider();

        queue.enqueueFeedAdded(FEED_URL);

        assertEquals(Collections.singletonList(FEED_URL), storage.getQueuedAddedFeeds());
        capturedWorkRequest();
    }

    @Test
    public void enqueueFeedRemovedStoresFeedAndSchedulesSync() {
        connectProvider();

        queue.enqueueFeedRemoved(FEED_URL);

        assertEquals(Collections.singletonList(FEED_URL), storage.getQueuedRemovedFeeds());
        capturedWorkRequest();
    }

    @Test
    public void enqueueEpisodeActionStoresActionAndSchedulesSync() {
        connectProvider();
        EpisodeAction action = new EpisodeAction.Builder(FEED_URL, "episode", EpisodeAction.DOWNLOAD)
                .currentTimestamp()
                .build();

        queue.enqueueEpisodeAction(action);

        assertEquals(1, storage.getQueuedEpisodeActions().size());
        assertEquals("episode", storage.getQueuedEpisodeActions().get(0).getEpisode());
        capturedWorkRequest();
    }

    @Test
    public void nothingIsQueuedWithoutConnectedSyncProvider() {
        EpisodeAction action = new EpisodeAction.Builder(FEED_URL, "episode", EpisodeAction.DOWNLOAD)
                .currentTimestamp()
                .build();

        queue.enqueueFeedAdded(FEED_URL);
        queue.enqueueFeedRemoved(FEED_URL);
        queue.enqueueEpisodeAction(action);
        queue.enqueueEpisodePlayed(playedMedia(subscribedFeed(), 100000, 0, 50000), false);

        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        verifyNoInteractions(workManager);
    }

    @Test
    public void playedEpisodeIsQueuedAsPlayActionInSeconds() {
        connectProvider();

        queue.enqueueEpisodePlayed(playedMedia(subscribedFeed(), 100000, 10000, 50000), false);

        List<EpisodeAction> actions = storage.getQueuedEpisodeActions();
        assertEquals(1, actions.size());
        assertEquals(EpisodeAction.PLAY, actions.get(0).getAction());
        assertEquals(FEED_URL, actions.get(0).getPodcast());
        assertEquals("http://feed.example/episode.mp3", actions.get(0).getEpisode());
        assertEquals("guid-1", actions.get(0).getGuid());
        assertEquals(10, actions.get(0).getStarted());
        assertEquals(50, actions.get(0).getPosition());
        assertEquals(100, actions.get(0).getTotal());
    }

    @Test
    public void completedEpisodeIsQueuedWithPositionAtTheEnd() {
        connectProvider();

        queue.enqueueEpisodePlayed(playedMedia(subscribedFeed(), 100000, 10000, 20000), true);

        List<EpisodeAction> actions = storage.getQueuedEpisodeActions();
        assertEquals(1, actions.size());
        assertEquals(100, actions.get(0).getPosition());
        assertEquals(100, actions.get(0).getTotal());
    }

    @Test
    public void episodeWithoutProgressSincePlaybackStartIsNotQueued() {
        connectProvider();

        queue.enqueueEpisodePlayed(playedMedia(subscribedFeed(), 100000, 30000, 30000), false);
        queue.enqueueEpisodePlayed(playedMedia(subscribedFeed(), 100000, 30000, 20000), false);

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        verify(workManager, never()).enqueueUniqueWork(any(), any(), any(OneTimeWorkRequest.class));
    }

    @Test
    public void completedEpisodeWithoutProgressIsStillQueued() {
        connectProvider();

        queue.enqueueEpisodePlayed(playedMedia(subscribedFeed(), 100000, 30000, 30000), true);

        assertEquals(1, storage.getQueuedEpisodeActions().size());
    }

    @Test
    public void episodeWhosePlaybackNeverStartedIsNotQueued() {
        connectProvider();
        FeedItem item = new FeedItem(2, "Other", "guid-2", "http://link.example", new Date(), 0, subscribedFeed());
        FeedMedia neverStarted = new FeedMedia(item, "http://feed.example/other.mp3", 0, "audio/mp3");
        item.setMedia(neverStarted);
        neverStarted.setDuration(100000);
        neverStarted.setPosition(50000);

        queue.enqueueEpisodePlayed(neverStarted, true);

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
    }

    @Test
    public void episodesOfLocalFeedsAreNotQueued() {
        connectProvider();
        Feed localFeed = new Feed(Feed.PREFIX_LOCAL_FOLDER + "folder", null, "Local");

        queue.enqueueEpisodePlayed(playedMedia(localFeed, 100000, 0, 50000), false);

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
    }

    @Test
    public void episodesOfUnsubscribedFeedsAreNotQueued() {
        connectProvider();
        Feed unsubscribed = subscribedFeed();
        unsubscribed.setState(Feed.STATE_NOT_SUBSCRIBED);

        queue.enqueueEpisodePlayed(playedMedia(unsubscribed, 100000, 0, 50000), false);

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
    }

    @Test
    public void mediaWithoutEpisodeIsNotQueued() {
        connectProvider();
        FeedMedia orphan = new FeedMedia(null, "http://feed.example/orphan.mp3", 0, "audio/mp3");

        queue.enqueueEpisodePlayed(orphan, true);

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        verify(workManager, never()).enqueueUniqueWork(any(), any(), any(OneTimeWorkRequest.class));
    }
}
