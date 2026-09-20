package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class ExceptFavoriteCleanupAlgorithmSelectionTest {
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
    public void everythingExceptFavoritesIsReclaimable() {
        FeedItem favorite = episode(1, new Date(1000));
        favorite.addTag(FeedItem.TAG_FAVORITE);
        FeedItem queued = episode(2, new Date(2000));
        queued.addTag(FeedItem.TAG_QUEUE);
        FeedItem unplayed = episode(3, new Date(3000));
        FeedItem played = episode(4, new Date(4000));
        played.setPlayed(true);
        storage.setDownloadedEpisodes(Arrays.asList(favorite, queued, unplayed, played));

        assertEquals(3, new ExceptFavoriteCleanupAlgorithm().getReclaimableItems());
    }

    @Test
    public void notDownloadedEpisodesAreNotReclaimable() {
        FeedItem notDownloaded = episode(1, new Date(1000));
        notDownloaded.getMedia().setDownloaded(false, 0);
        FeedItem withoutMedia = episode(2, new Date(2000));
        withoutMedia.setMedia(null);
        storage.setDownloadedEpisodes(Arrays.asList(notDownloaded, withoutMedia));

        assertEquals(0, new ExceptFavoriteCleanupAlgorithm().getReclaimableItems());
    }

    @Test
    public void localFeedEpisodesAreOnlyReclaimableWhenAutoDeleteLocalIsEnabled() {
        FeedItem local = StorageMocks.downloadedEpisode(1, new Date(1000), StorageMocks.localFeed());
        storage.setDownloadedEpisodes(Collections.singletonList(local));

        storage.setAutoDeleteLocal(false);
        assertEquals(0, new ExceptFavoriteCleanupAlgorithm().getReclaimableItems());

        storage.setAutoDeleteLocal(true);
        assertEquals(1, new ExceptFavoriteCleanupAlgorithm().getReclaimableItems());
    }

    @Test
    public void cleanupDeletesOldestPublishedEpisodesFirst() {
        storage.setDownloadedEpisodes(Arrays.asList(
                episode(1, new Date(3000)),
                episode(2, new Date(1000)),
                episode(3, new Date(2000))));

        int deleted = new ExceptFavoriteCleanupAlgorithm().performCleanup(context, 2);

        assertEquals(2, deleted);
        assertEquals(Arrays.asList(2L, 3L), storage.deletedMediaIds());
    }

    @Test
    public void cleanupOrdersEpisodesWithoutPublicationDateById() {
        storage.setDownloadedEpisodes(Arrays.asList(episode(30, null), episode(10, null), episode(20, null)));

        new ExceptFavoriteCleanupAlgorithm().performCleanup(context, 2);

        assertEquals(Arrays.asList(10L, 20L), storage.deletedMediaIds());
    }

    @Test
    public void cleanupSkipsFavorites() {
        FeedItem favorite = episode(1, new Date(1000));
        favorite.addTag(FeedItem.TAG_FAVORITE);
        storage.setDownloadedEpisodes(Arrays.asList(favorite, episode(2, new Date(2000))));

        int deleted = new ExceptFavoriteCleanupAlgorithm().performCleanup(context, 5);

        assertEquals(1, deleted);
        assertEquals(Collections.singletonList(2L), storage.deletedMediaIds());
    }

    @Test
    public void cleanupContinuesWithRemainingEpisodesWhenADeletionFails() {
        FeedItem failing = episode(1, new Date(1000));
        storage.failDeletionOf(failing);
        storage.setDownloadedEpisodes(Arrays.asList(failing, episode(2, new Date(2000))));

        new ExceptFavoriteCleanupAlgorithm().performCleanup(context, 2);

        assertEquals(Arrays.asList(1L, 2L), storage.deletedMediaIds());
    }

    @Test
    public void defaultCleanupParameterIsTheOverflowOfTheEpisodeCache() {
        storage.setEpisodeCacheSize(3);
        storage.setDownloadedCount(8);

        assertEquals(5, new ExceptFavoriteCleanupAlgorithm().getDefaultCleanupParameter());
    }

    @Test
    public void defaultCleanupParameterIsZeroWhenCacheIsExactlyFull() {
        storage.setEpisodeCacheSize(3);
        storage.setDownloadedCount(3);

        assertEquals(0, new ExceptFavoriteCleanupAlgorithm().getDefaultCleanupParameter());
    }

    @Test
    public void defaultCleanupParameterIsZeroForUnlimitedCache() {
        storage.setEpisodeCacheSize(UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED);
        storage.setDownloadedCount(500);

        assertEquals(0, new ExceptFavoriteCleanupAlgorithm().getDefaultCleanupParameter());
    }

    @Test
    public void performCleanupWithoutParameterTrimsTheCacheToItsSize() {
        storage.setEpisodeCacheSize(2);
        storage.setDownloadedCount(3);
        storage.setDownloadedEpisodes(Arrays.asList(
                episode(1, new Date(1000)),
                episode(2, new Date(2000)),
                episode(3, new Date(3000))));

        int deleted = new ExceptFavoriteCleanupAlgorithm().performCleanup(context);

        assertEquals(1, deleted);
        assertEquals(Collections.singletonList(1L), storage.deletedMediaIds());
    }

    @Test
    public void performCleanupWithoutParameterDeletesNothingWhenCacheHasSpace() {
        storage.setEpisodeCacheSize(10);
        storage.setDownloadedCount(3);
        storage.setDownloadedEpisodes(Collections.singletonList(episode(1, new Date(1000))));

        assertEquals(0, new ExceptFavoriteCleanupAlgorithm().performCleanup(context));
        assertEquals(new ArrayList<Long>(), storage.deletedMediaIds());
    }
}
