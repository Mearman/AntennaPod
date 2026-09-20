package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import de.danoeh.antennapod.model.feed.FeedItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class APQueueCleanupAlgorithmSelectionTest {
    private final Context context = Mockito.mock(Context.class);
    private StorageMocks storage;

    @Before
    public void setUp() {
        storage = new StorageMocks();
    }

    @After
    public void tearDown() {
        storage.close();
    }

    private static FeedItem episode(long id, Date pubDate) {
        return StorageMocks.downloadedEpisode(id, pubDate, StorageMocks.remoteFeed());
    }

    @Test
    public void queuedAndFavoriteEpisodesAreNotReclaimable() {
        FeedItem queued = episode(1, new Date(1000));
        queued.addTag(FeedItem.TAG_QUEUE);
        FeedItem favorite = episode(2, new Date(2000));
        favorite.addTag(FeedItem.TAG_FAVORITE);
        FeedItem plain = episode(3, new Date(3000));
        FeedItem played = episode(4, new Date(4000));
        played.setPlayed(true);
        storage.setDownloadedEpisodes(Arrays.asList(queued, favorite, plain, played));

        assertEquals(2, new APQueueCleanupAlgorithm().getReclaimableItems());
    }

    @Test
    public void localFeedEpisodesAreOnlyReclaimableWhenAutoDeleteLocalIsEnabled() {
        FeedItem local = StorageMocks.downloadedEpisode(1, new Date(1000), StorageMocks.localFeed());
        storage.setDownloadedEpisodes(Collections.singletonList(local));

        storage.setAutoDeleteLocal(false);
        assertEquals(0, new APQueueCleanupAlgorithm().getReclaimableItems());

        storage.setAutoDeleteLocal(true);
        assertEquals(1, new APQueueCleanupAlgorithm().getReclaimableItems());
    }

    @Test
    public void cleanupDeletesOldestPublishedEpisodesFirst() {
        storage.setDownloadedEpisodes(Arrays.asList(
                episode(1, new Date(3000)),
                episode(2, new Date(1000)),
                episode(3, new Date(2000))));

        int deleted = new APQueueCleanupAlgorithm().performCleanup(context, 2);

        assertEquals(2, deleted);
        assertEquals(Arrays.asList(2L, 3L), storage.deletedMediaIds());
    }

    @Test
    public void episodesWithoutPublicationDateAreDeletedLast() {
        storage.setDownloadedEpisodes(Arrays.asList(episode(1, null), episode(2, new Date(1000))));

        new APQueueCleanupAlgorithm().performCleanup(context, 1);

        assertEquals(Collections.singletonList(2L), storage.deletedMediaIds());
    }

    @Test
    public void cleanupKeepsQueuedEpisodes() {
        FeedItem queued = episode(1, new Date(1000));
        queued.addTag(FeedItem.TAG_QUEUE);
        storage.setDownloadedEpisodes(Arrays.asList(queued, episode(2, new Date(2000))));

        int deleted = new APQueueCleanupAlgorithm().performCleanup(context, 5);

        assertEquals(1, deleted);
        assertEquals(Collections.singletonList(2L), storage.deletedMediaIds());
    }

    @Test
    public void cleanupContinuesWithRemainingEpisodesWhenADeletionFails() {
        FeedItem failing = episode(1, new Date(1000));
        storage.failDeletionOf(failing);
        storage.setDownloadedEpisodes(Arrays.asList(failing, episode(2, new Date(2000))));

        new APQueueCleanupAlgorithm().performCleanup(context, 2);

        assertEquals(Arrays.asList(1L, 2L), storage.deletedMediaIds());
    }

    @Test
    public void defaultCleanupParameterIsTheOverflowOfTheEpisodeCache() {
        storage.setEpisodeCacheSize(3);
        storage.setDownloadedCount(4);

        assertEquals(1, new APQueueCleanupAlgorithm().getDefaultCleanupParameter());
    }

    @Test
    public void makingRoomAccountsForEpisodesAboutToBeDownloaded() {
        storage.setEpisodeCacheSize(4);
        storage.setDownloadedCount(4);
        storage.setDownloadedEpisodes(Arrays.asList(episode(1, new Date(1000)), episode(2, new Date(2000))));

        int deleted = new APQueueCleanupAlgorithm().makeRoomForEpisodes(context, 2);

        assertEquals(2, deleted);
    }
}
