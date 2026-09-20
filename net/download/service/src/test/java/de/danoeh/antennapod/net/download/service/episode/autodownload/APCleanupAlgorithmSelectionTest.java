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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class APCleanupAlgorithmSelectionTest {
    private static final int ONE_HOUR = 1;
    private static final Date LONG_AGO = new Date(0);

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

    private static FeedItem playedEpisode(long id, Date lastPlayed) {
        FeedItem item = StorageMocks.downloadedEpisode(id, new Date(id), StorageMocks.remoteFeed());
        item.setPlayed(true);
        item.getMedia().setLastPlayedTimeHistory(lastPlayed);
        return item;
    }

    @Test
    public void onlyPlayedEpisodesAreReclaimable() {
        FeedItem played = playedEpisode(1, LONG_AGO);
        FeedItem unplayed = playedEpisode(2, LONG_AGO);
        unplayed.setPlayed(false);
        storage.setDownloadedEpisodes(Arrays.asList(played, unplayed));

        assertEquals(1, new APCleanupAlgorithm(ONE_HOUR).getReclaimableItems());
    }

    @Test
    public void queuedAndFavoriteEpisodesAreNotReclaimable() {
        FeedItem queued = playedEpisode(1, LONG_AGO);
        queued.addTag(FeedItem.TAG_QUEUE);
        FeedItem favorite = playedEpisode(2, LONG_AGO);
        favorite.addTag(FeedItem.TAG_FAVORITE);
        FeedItem plain = playedEpisode(3, LONG_AGO);
        storage.setDownloadedEpisodes(Arrays.asList(queued, favorite, plain));

        assertEquals(1, new APCleanupAlgorithm(ONE_HOUR).getReclaimableItems());
    }

    @Test
    public void episodesPlayedWithinRetentionPeriodAreNotReclaimable() {
        Date playedMinutesAgo = new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30));
        Date playedHoursAgo = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3));
        storage.setDownloadedEpisodes(Arrays.asList(playedEpisode(1, playedMinutesAgo),
                playedEpisode(2, playedHoursAgo)));

        assertEquals(1, new APCleanupAlgorithm(ONE_HOUR).getReclaimableItems());
    }

    @Test
    public void playedEpisodesWithoutPlaybackTimestampAreNotReclaimable() {
        storage.setDownloadedEpisodes(Collections.singletonList(playedEpisode(1, null)));

        assertEquals(0, new APCleanupAlgorithm(ONE_HOUR).getReclaimableItems());
    }

    @Test
    public void notDownloadedEpisodesAreNotReclaimable() {
        FeedItem notDownloaded = playedEpisode(1, LONG_AGO);
        notDownloaded.getMedia().setDownloaded(false, 0);
        FeedItem withoutMedia = playedEpisode(2, LONG_AGO);
        withoutMedia.setMedia(null);
        storage.setDownloadedEpisodes(Arrays.asList(notDownloaded, withoutMedia));

        assertEquals(0, new APCleanupAlgorithm(ONE_HOUR).getReclaimableItems());
    }

    @Test
    public void localFeedEpisodesAreOnlyReclaimableWhenAutoDeleteLocalIsEnabled() {
        FeedItem local = playedEpisode(1, LONG_AGO);
        local.setFeed(StorageMocks.localFeed());
        storage.setDownloadedEpisodes(Collections.singletonList(local));

        storage.setAutoDeleteLocal(false);
        assertEquals(0, new APCleanupAlgorithm(ONE_HOUR).getReclaimableItems());

        storage.setAutoDeleteLocal(true);
        assertEquals(1, new APCleanupAlgorithm(ONE_HOUR).getReclaimableItems());
    }

    @Test
    public void cleanupDeletesLeastRecentlyPlayedEpisodesFirst() {
        storage.setDownloadedEpisodes(Arrays.asList(
                playedEpisode(1, new Date(3000)),
                playedEpisode(2, new Date(1000)),
                playedEpisode(3, new Date(2000))));

        int deleted = new APCleanupAlgorithm(ONE_HOUR).performCleanup(context, 2);

        assertEquals(2, deleted);
        assertEquals(Arrays.asList(2L, 3L), storage.deletedMediaIds());
    }

    @Test
    public void cleanupDeletesAllCandidatesWhenFewerThanRequested() {
        storage.setDownloadedEpisodes(Arrays.asList(playedEpisode(1, LONG_AGO), playedEpisode(2, LONG_AGO)));

        int deleted = new APCleanupAlgorithm(ONE_HOUR).performCleanup(context, 10);

        assertEquals(2, deleted);
        assertEquals(2, storage.deletedMediaIds().size());
    }

    @Test
    public void cleanupNeverTouchesUnreclaimableEpisodes() {
        FeedItem favorite = playedEpisode(1, LONG_AGO);
        favorite.addTag(FeedItem.TAG_FAVORITE);
        storage.setDownloadedEpisodes(Collections.singletonList(favorite));

        int deleted = new APCleanupAlgorithm(ONE_HOUR).performCleanup(context, 5);

        assertEquals(0, deleted);
        assertEquals(new ArrayList<Long>(), storage.deletedMediaIds());
    }

    @Test
    public void cleanupContinuesWithRemainingEpisodesWhenADeletionFails() {
        FeedItem failing = playedEpisode(1, new Date(1000));
        storage.failDeletionOf(failing);
        storage.setDownloadedEpisodes(Arrays.asList(failing, playedEpisode(2, new Date(2000))));

        new APCleanupAlgorithm(ONE_HOUR).performCleanup(context, 2);

        assertEquals(Arrays.asList(1L, 2L), storage.deletedMediaIds());
    }

    @Test
    public void defaultCleanupParameterIsTheOverflowOfTheEpisodeCache() {
        storage.setEpisodeCacheSize(3);
        storage.setDownloadedCount(5);

        assertEquals(2, new APCleanupAlgorithm(ONE_HOUR).getDefaultCleanupParameter());
    }

    @Test
    public void defaultCleanupParameterIsZeroWhenCacheIsNotFull() {
        storage.setEpisodeCacheSize(10);
        storage.setDownloadedCount(4);

        assertEquals(0, new APCleanupAlgorithm(ONE_HOUR).getDefaultCleanupParameter());
    }

    @Test
    public void defaultCleanupParameterIsZeroForUnlimitedCache() {
        storage.setEpisodeCacheSize(UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED);
        storage.setDownloadedCount(500);

        assertEquals(0, new APCleanupAlgorithm(ONE_HOUR).getDefaultCleanupParameter());
    }

    @Test
    public void makingRoomAccountsForEpisodesAboutToBeDownloaded() {
        storage.setEpisodeCacheSize(6);
        storage.setDownloadedCount(5);
        storage.setDownloadedEpisodes(Arrays.asList(
                playedEpisode(1, new Date(1000)),
                playedEpisode(2, new Date(2000)),
                playedEpisode(3, new Date(3000))));

        int deleted = new APCleanupAlgorithm(ONE_HOUR).makeRoomForEpisodes(context, 2);

        assertEquals(1, deleted);
        assertEquals(Collections.singletonList(1L), storage.deletedMediaIds());
    }

    @Test
    public void makingRoomDeletesNothingWhileTheCacheHasSpace() {
        storage.setEpisodeCacheSize(10);
        storage.setDownloadedCount(2);
        storage.setDownloadedEpisodes(Collections.singletonList(playedEpisode(1, LONG_AGO)));

        int deleted = new APCleanupAlgorithm(ONE_HOUR).makeRoomForEpisodes(context, 3);

        assertEquals(0, deleted);
        assertEquals(new ArrayList<Long>(), storage.deletedMediaIds());
    }

    @Test
    public void makingRoomForANegativeAmountDeletesNothing() {
        storage.setEpisodeCacheSize(1);
        storage.setDownloadedCount(5);
        storage.setDownloadedEpisodes(Collections.singletonList(playedEpisode(1, LONG_AGO)));

        assertEquals(0, new APCleanupAlgorithm(ONE_HOUR).makeRoomForEpisodes(context, -1));
    }
}
