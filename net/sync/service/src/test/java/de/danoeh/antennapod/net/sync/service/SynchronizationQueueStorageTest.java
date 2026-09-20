package de.danoeh.antennapod.net.sync.service;

import android.content.Context;
import android.content.SharedPreferences;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class SynchronizationQueueStorageTest {
    private static final String FEED_A = "https://a.example/feed.xml";
    private static final String FEED_B = "https://b.example/feed.xml";
    private Context context;
    private SynchronizationQueueStorage storage;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        SynchronizationSettings.init(context);
        storage = new SynchronizationQueueStorage(context);
    }

    private static EpisodeAction playAction(String episode, int position) {
        return new EpisodeAction.Builder(FEED_A, episode, EpisodeAction.PLAY)
                .timestamp(new Date(1_700_000_000_000L))
                .started(0)
                .position(position)
                .total(100)
                .build();
    }

    @Test
    public void emptyStorageHasNoQueuedChanges() {
        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
    }

    @Test
    public void enqueuedEpisodeActionsSurviveANewStorageInstanceInOrder() {
        storage.enqueueEpisodeAction(playAction("https://a.example/1.mp3", 10));
        storage.enqueueEpisodeAction(playAction("https://a.example/2.mp3", 20));

        List<EpisodeAction> actions = new SynchronizationQueueStorage(context).getQueuedEpisodeActions();

        assertEquals(2, actions.size());
        assertEquals("https://a.example/1.mp3", actions.get(0).getEpisode());
        assertEquals(10, actions.get(0).getPosition());
        assertEquals(100, actions.get(0).getTotal());
        assertEquals(EpisodeAction.PLAY, actions.get(0).getAction());
        assertEquals("https://a.example/2.mp3", actions.get(1).getEpisode());
        assertEquals(20, actions.get(1).getPosition());
    }

    @Test
    public void clearEpisodeActionQueueLeavesFeedQueuesUntouched() {
        storage.enqueueEpisodeAction(playAction("https://a.example/1.mp3", 10));
        storage.enqueueFeedAdded(FEED_A);

        storage.clearEpisodeActionQueue();

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        assertEquals(Collections.singletonList(FEED_A), storage.getQueuedAddedFeeds());
    }

    @Test
    public void addedFeedsAreQueuedInOrder() {
        storage.enqueueFeedAdded(FEED_A);
        storage.enqueueFeedAdded(FEED_B);

        assertEquals(Arrays.asList(FEED_A, FEED_B), storage.getQueuedAddedFeeds());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
    }

    @Test
    public void removedFeedsAreQueuedInOrder() {
        storage.enqueueFeedRemoved(FEED_A);
        storage.enqueueFeedRemoved(FEED_B);

        assertEquals(Arrays.asList(FEED_A, FEED_B), storage.getQueuedRemovedFeeds());
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
    }

    @Test
    public void addingAFeedThatWasQueuedForRemovalCancelsTheRemoval() {
        storage.enqueueFeedRemoved(FEED_A);
        storage.enqueueFeedRemoved(FEED_B);

        storage.enqueueFeedAdded(FEED_A);

        assertEquals(Collections.singletonList(FEED_A), storage.getQueuedAddedFeeds());
        assertEquals(Collections.singletonList(FEED_B), storage.getQueuedRemovedFeeds());
    }

    @Test
    public void removingAFeedThatWasQueuedForAdditionCancelsTheAddition() {
        storage.enqueueFeedAdded(FEED_A);
        storage.enqueueFeedAdded(FEED_B);

        storage.enqueueFeedRemoved(FEED_A);

        assertEquals(Collections.singletonList(FEED_B), storage.getQueuedAddedFeeds());
        assertEquals(Collections.singletonList(FEED_A), storage.getQueuedRemovedFeeds());
    }

    @Test
    public void clearFeedQueuesEmptiesBothFeedQueues() {
        storage.enqueueFeedAdded(FEED_A);
        storage.enqueueFeedRemoved(FEED_B);

        storage.clearFeedQueues();

        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
    }

    @Test
    public void clearQueueEmptiesAllQueuesAndResetsSyncTimestamps() {
        storage.enqueueEpisodeAction(playAction("https://a.example/1.mp3", 10));
        storage.enqueueFeedAdded(FEED_A);
        storage.enqueueFeedRemoved(FEED_B);
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(123);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(456);
        SynchronizationSettings.updateLastSynchronizationAttempt();

        storage.clearQueue();

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
        assertEquals(0, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastSyncAttempt());
    }

    @Test
    public void legacyConflictCleanupDropsRemovalsOfFeedsThatAreStillSubscribed() {
        storage.enqueueFeedRemoved(FEED_A);

        storage.removeLegacyConflictingFeedEntries(Collections.singletonList(FEED_A));

        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
    }

    @Test
    public void corruptStoredQueuesAreReadAsEmpty() {
        SharedPreferences preferences = context.getSharedPreferences("synchronization", Context.MODE_PRIVATE);
        preferences.edit()
                .putString("sync_queued_episode_actions", "not json")
                .putString("sync_added", "{")
                .putString("sync_removed", "[")
                .commit();

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
    }
}
