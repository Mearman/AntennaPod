package de.danoeh.antennapod.net.sync.service;

import android.content.Context;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SynchronizationQueueStorageTest {
    private Context context;
    private SynchronizationQueueStorage storage;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        SynchronizationSettings.init(context);
        storage = new SynchronizationQueueStorage(context);
    }

    private static EpisodeAction downloadAction(String episode) {
        return new EpisodeAction.Builder("http://podcast.example", episode, EpisodeAction.DOWNLOAD)
                .timestamp(new Date(1609488000000L))
                .build();
    }

    @Test
    public void newStorageHasEmptyQueues() {
        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
    }

    @Test
    public void enqueuedFeedsAreReturnedInOrderOfEnqueueing() {
        storage.enqueueFeedAdded("http://a.example");
        storage.enqueueFeedAdded("http://b.example");
        storage.enqueueFeedRemoved("http://c.example");
        storage.enqueueFeedRemoved("http://d.example");

        assertEquals(Arrays.asList("http://a.example", "http://b.example"), storage.getQueuedAddedFeeds());
        assertEquals(Arrays.asList("http://c.example", "http://d.example"), storage.getQueuedRemovedFeeds());
    }

    @Test
    public void removingAFeedThatWasQueuedAsAddedCancelsTheAddition() {
        storage.enqueueFeedAdded("http://a.example");
        storage.enqueueFeedAdded("http://b.example");

        storage.enqueueFeedRemoved("http://a.example");

        assertEquals(Collections.singletonList("http://b.example"), storage.getQueuedAddedFeeds());
        assertEquals(Collections.singletonList("http://a.example"), storage.getQueuedRemovedFeeds());
    }

    @Test
    public void addingAFeedThatWasQueuedAsRemovedCancelsTheRemoval() {
        storage.enqueueFeedRemoved("http://a.example");
        storage.enqueueFeedRemoved("http://b.example");

        storage.enqueueFeedAdded("http://a.example");

        assertEquals(Collections.singletonList("http://b.example"), storage.getQueuedRemovedFeeds());
        assertEquals(Collections.singletonList("http://a.example"), storage.getQueuedAddedFeeds());
    }

    @Test
    public void removingAnUnknownFeedKeepsOtherQueuedAdditions() {
        storage.enqueueFeedAdded("http://a.example");

        storage.enqueueFeedRemoved("http://other.example");

        assertEquals(Collections.singletonList("http://a.example"), storage.getQueuedAddedFeeds());
        assertEquals(Collections.singletonList("http://other.example"), storage.getQueuedRemovedFeeds());
    }

    @Test
    public void enqueuedEpisodeActionsAreReadBackInOrder() {
        storage.enqueueEpisodeAction(downloadAction("episode1"));
        storage.enqueueEpisodeAction(downloadAction("episode2"));

        List<EpisodeAction> actions = storage.getQueuedEpisodeActions();

        assertEquals(2, actions.size());
        assertEquals("episode1", actions.get(0).getEpisode());
        assertEquals("episode2", actions.get(1).getEpisode());
        assertEquals(EpisodeAction.DOWNLOAD, actions.get(0).getAction());
        assertEquals(new Date(1609488000000L), actions.get(0).getTimestamp());
    }

    @Test
    public void episodeActionQueueSurvivesRecreatingTheStorage() {
        storage.enqueueEpisodeAction(downloadAction("episode1"));

        List<EpisodeAction> actions = new SynchronizationQueueStorage(context).getQueuedEpisodeActions();

        assertEquals(1, actions.size());
        assertEquals("episode1", actions.get(0).getEpisode());
    }

    @Test
    public void clearingEpisodeActionsKeepsFeedQueues() {
        storage.enqueueEpisodeAction(downloadAction("episode1"));
        storage.enqueueFeedAdded("http://a.example");

        storage.clearEpisodeActionQueue();

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        assertEquals(Collections.singletonList("http://a.example"), storage.getQueuedAddedFeeds());
    }

    @Test
    public void clearingFeedQueuesKeepsEpisodeActions() {
        storage.enqueueEpisodeAction(downloadAction("episode1"));
        storage.enqueueFeedAdded("http://a.example");
        storage.enqueueFeedRemoved("http://b.example");

        storage.clearFeedQueues();

        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
        assertEquals(1, storage.getQueuedEpisodeActions().size());
    }

    @Test
    public void clearingTheWholeQueueAlsoResetsSynchronisationTimestamps() {
        storage.enqueueEpisodeAction(downloadAction("episode1"));
        storage.enqueueFeedAdded("http://a.example");
        storage.enqueueFeedRemoved("http://b.example");
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(11);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(22);

        storage.clearQueue();

        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
        assertEquals(0, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void corruptedQueuesAreTreatedAsEmpty() {
        context.getSharedPreferences("synchronization", Context.MODE_PRIVATE).edit()
                .putString("sync_removed", "{broken")
                .putString("sync_added", "{broken")
                .putString("sync_queued_episode_actions", "{broken")
                .apply();

        assertTrue(storage.getQueuedRemovedFeeds().isEmpty());
        assertTrue(storage.getQueuedAddedFeeds().isEmpty());
        assertTrue(storage.getQueuedEpisodeActions().isEmpty());
    }
}
