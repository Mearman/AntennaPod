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

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class AutoDownloadManagerImplTest {
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

    @Test
    public void nullAlgorithmLeavesDownloadedEpisodesAlone() {
        storage.setEpisodeCleanupValue(UserPreferences.EPISODE_CLEANUP_NULL);
        storage.setDownloadedEpisodes(Collections.singletonList(
                StorageMocks.downloadedEpisode(1, new Date(1000), StorageMocks.remoteFeed())));

        new AutoDownloadManagerImpl().performAutoCleanup(context);

        assertEquals(Collections.emptyList(), storage.deletedMediaIds());
    }

    @Test
    public void autoCleanupUsesTheConfiguredAlgorithm() {
        storage.setEpisodeCleanupValue(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE);
        storage.setEpisodeCacheSize(1);
        storage.setDownloadedCount(2);
        FeedItem favorite = StorageMocks.downloadedEpisode(1, new Date(1000), StorageMocks.remoteFeed());
        favorite.addTag(FeedItem.TAG_FAVORITE);
        FeedItem plain = StorageMocks.downloadedEpisode(2, new Date(2000), StorageMocks.remoteFeed());
        storage.setDownloadedEpisodes(Arrays.asList(favorite, plain));

        new AutoDownloadManagerImpl().performAutoCleanup(context);

        assertEquals(Collections.singletonList(2L), storage.deletedMediaIds());
    }
}
