package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class EpisodeCleanupAlgorithmFactoryTest {
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
    public void exceptFavoriteSettingBuildsExceptFavoriteAlgorithm() {
        storage.setEpisodeCleanupValue(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE);
        assertTrue(EpisodeCleanupAlgorithmFactory.build() instanceof ExceptFavoriteCleanupAlgorithm);
    }

    @Test
    public void queueSettingBuildsQueueAlgorithm() {
        storage.setEpisodeCleanupValue(UserPreferences.EPISODE_CLEANUP_QUEUE);
        assertTrue(EpisodeCleanupAlgorithmFactory.build() instanceof APQueueCleanupAlgorithm);
    }

    @Test
    public void nullSettingBuildsAlgorithmThatNeverCleansUp() {
        storage.setEpisodeCleanupValue(UserPreferences.EPISODE_CLEANUP_NULL);
        EpisodeCleanupAlgorithm algorithm = EpisodeCleanupAlgorithmFactory.build();
        assertTrue(algorithm instanceof APNullCleanupAlgorithm);
        assertEquals(0, algorithm.performCleanup(context, 10));
        assertEquals(0, algorithm.getDefaultCleanupParameter());
        assertEquals(0, algorithm.getReclaimableItems());
    }

    @Test
    public void positiveSettingBuildsPlaybackAgeAlgorithmWithThatRetention() {
        storage.setEpisodeCleanupValue(72);
        EpisodeCleanupAlgorithm algorithm = EpisodeCleanupAlgorithmFactory.build();
        assertTrue(algorithm instanceof APCleanupAlgorithm);
        assertEquals(72, ((APCleanupAlgorithm) algorithm).getNumberOfHoursAfterPlayback());
    }

    @Test
    public void defaultSettingBuildsPlaybackAgeAlgorithmWithoutRetention() {
        storage.setEpisodeCleanupValue(UserPreferences.EPISODE_CLEANUP_DEFAULT);
        EpisodeCleanupAlgorithm algorithm = EpisodeCleanupAlgorithmFactory.build();
        assertTrue(algorithm instanceof APCleanupAlgorithm);
        assertEquals(0, ((APCleanupAlgorithm) algorithm).getNumberOfHoursAfterPlayback());
    }
}
