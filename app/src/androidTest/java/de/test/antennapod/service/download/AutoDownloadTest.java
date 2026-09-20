package de.test.antennapod.service.download;

import android.Manifest;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.net.download.service.episode.autodownload.APCleanupAlgorithm;
import de.danoeh.antennapod.net.download.service.episode.autodownload.APNullCleanupAlgorithm;
import de.danoeh.antennapod.net.download.service.episode.autodownload.APQueueCleanupAlgorithm;
import de.danoeh.antennapod.net.download.service.episode.autodownload.AutomaticDownloadAlgorithm;
import de.danoeh.antennapod.net.download.service.episode.autodownload.EpisodeCleanupAlgorithm;
import de.danoeh.antennapod.net.download.service.episode.autodownload.EpisodeCleanupAlgorithmFactory;
import de.danoeh.antennapod.net.download.service.episode.autodownload.ExceptFavoriteCleanupAlgorithm;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Runs the automatic download and the episode cleanup algorithms against the database and the local test server.
 */
@RunWith(AndroidJUnit4.class)
public class AutoDownloadTest {
    private static final long TIMEOUT_SECONDS = 60;
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Context context;
    private DownloadServiceInterface downloads;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
        UserPreferences.setAllowMobileAutoDownload(true);
        UserPreferences.setAllowMobileEpisodeDownload(true);
        downloads = DownloadServiceInterface.get();
    }

    @After
    public void tearDown() throws Exception {
        runShellCommand("dumpsys battery reset");
        runAutoDownload();
        downloads.cancelAll(context);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> downloads.getNumberOfActiveDownloads(context) == 0);
        WorkManager.getInstance(context).pruneWork().getResult().get();
        fixture.tearDown();
    }

    private void runShellCommand(String command) throws IOException {
        ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(command);
        try (FileInputStream in = new FileInputStream(output.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) {
                continue;
            }
        }
    }

    private void unplugDevice() throws IOException {
        runShellCommand("dumpsys battery unplug");
        runShellCommand("dumpsys battery set status " + BatteryManager.BATTERY_STATUS_DISCHARGING);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> !AutomaticDownloadAlgorithm.deviceCharging(context));
    }

    private void enableGlobalAutoDownload() {
        DownloadTestFixture.putBoolean(UserPreferences.PREF_AUTODL_GLOBAL, true);
    }

    private void runAutoDownload() throws Exception {
        AutoDownloadManager.getInstance().autodownloadUndownloadedItems(context).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private FeedItem reload(FeedItem item) {
        return DBReader.getFeedItem(item.getId());
    }

    private boolean isDownloaded(FeedItem item) {
        return reload(item).isDownloaded();
    }

    private void awaitDownloaded(FeedItem item) {
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> isDownloaded(item));
    }

    private void awaitAllDownloadsFinished() {
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> downloads.getNumberOfActiveDownloads(context) == 0);
    }

    private void setFeedAutoDownload(Feed feed, FeedPreferences.AutoDownloadSetting setting) throws Exception {
        feed.getPreferences().setAutoDownload(setting);
        DBWriter.setFeedPreferences(feed.getPreferences()).get();
    }

    private int downloadedEpisodes() {
        return DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.DOWNLOADED));
    }

    private EpisodeCleanupAlgorithm cleanupWith(int cleanupValue) throws Exception {
        runAutoDownload();
        UserPreferences.setEpisodeCleanupValue(cleanupValue);
        return EpisodeCleanupAlgorithmFactory.build();
    }

    private void limitCacheTo(int episodes) {
        DownloadTestFixture.putString(UserPreferences.PREF_EPISODE_CACHE_SIZE, String.valueOf(episodes));
    }

    @Test
    public void newEpisodesAreDownloadedWhenAutomaticDownloadIsEnabledGlobally() throws Exception {
        enableGlobalAutoDownload();
        Feed feed = fixture.subscribe("Global", 2, FeedItem.NEW);

        runAutoDownload();

        awaitDownloaded(feed.getItemAtIndex(0));
        awaitDownloaded(feed.getItemAtIndex(1));
    }

    @Test
    public void episodesAreNotDownloadedWhenAutomaticDownloadIsDisabledEverywhere() throws Exception {
        Feed feed = fixture.subscribe("Disabled", 2, FeedItem.NEW);

        runAutoDownload();

        awaitAllDownloadsFinished();
        assertFalse(isDownloaded(feed.getItemAtIndex(0)));
        assertTrue(fixture.requestsFor(feed.getItemAtIndex(0).getMedia().getDownloadUrl()).isEmpty());
    }

    @Test
    public void feedSettingDisabledOverridesTheGlobalSetting() throws Exception {
        enableGlobalAutoDownload();
        Feed feed = fixture.subscribe("Feed disabled", 1, FeedItem.NEW);
        setFeedAutoDownload(feed, FeedPreferences.AutoDownloadSetting.DISABLED);

        runAutoDownload();

        awaitAllDownloadsFinished();
        assertFalse(isDownloaded(feed.getItemAtIndex(0)));
    }

    @Test
    public void feedSettingEnabledOverridesTheGlobalSetting() throws Exception {
        Feed feed = fixture.subscribe("Feed enabled", 1, FeedItem.NEW);
        setFeedAutoDownload(feed, FeedPreferences.AutoDownloadSetting.ENABLED);

        runAutoDownload();

        awaitDownloaded(feed.getItemAtIndex(0));
    }

    @Test
    public void episodesThatAreNotNewAreNotDownloaded() throws Exception {
        enableGlobalAutoDownload();
        Feed feed = fixture.subscribe("Not new", 1, FeedItem.UNPLAYED);

        runAutoDownload();

        awaitAllDownloadsFinished();
        assertFalse(isDownloaded(feed.getItemAtIndex(0)));
    }

    @Test
    public void excludeFilterKeepsMatchingEpisodesOut() throws Exception {
        Feed feed = fixture.subscribe("Filtered", 2, FeedItem.NEW);
        feed.getPreferences().setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED);
        feed.getPreferences().setFilter(new FeedFilter("", "\"episode 1\""));
        DBWriter.setFeedPreferences(feed.getPreferences()).get();

        runAutoDownload();

        awaitDownloaded(feed.getItemAtIndex(0));
        awaitAllDownloadsFinished();
        assertFalse(isDownloaded(feed.getItemAtIndex(1)));
    }

    @Test
    public void includeFilterOnlyLetsMatchingEpisodesThrough() throws Exception {
        Feed feed = fixture.subscribe("Included", 2, FeedItem.NEW);
        feed.getPreferences().setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED);
        feed.getPreferences().setFilter(new FeedFilter("\"episode 1\"", ""));
        DBWriter.setFeedPreferences(feed.getPreferences()).get();

        runAutoDownload();

        awaitDownloaded(feed.getItemAtIndex(1));
        awaitAllDownloadsFinished();
        assertFalse(isDownloaded(feed.getItemAtIndex(0)));
    }

    @Test
    public void cacheSizeLimitsTheNumberOfDownloadedEpisodes() throws Exception {
        enableGlobalAutoDownload();
        limitCacheTo(1);
        Feed feed = fixture.subscribe("Limited", 3, FeedItem.NEW);

        runAutoDownload();

        awaitDownloaded(feed.getItemAtIndex(0));
        awaitAllDownloadsFinished();
        assertEquals(1, downloadedEpisodes());
    }

    @Test
    public void queuedEpisodesAreDownloadedWhenQueueDownloadIsEnabled() throws Exception {
        DownloadTestFixture.putBoolean(UserPreferences.PREF_AUTODL_QUEUE, true);
        Feed feed = fixture.subscribe("Queued", 2);
        DBWriter.addQueueItem(context, feed.getItemAtIndex(1)).get();

        runAutoDownload();

        awaitDownloaded(feed.getItemAtIndex(1));
        awaitAllDownloadsFinished();
        assertFalse(isDownloaded(feed.getItemAtIndex(0)));
    }

    @Test
    public void nothingIsDownloadedOnBatteryWhenTheUserForbidsIt() throws Exception {
        enableGlobalAutoDownload();
        DownloadTestFixture.putBoolean(UserPreferences.PREF_ENABLE_AUTODL_ON_BATTERY, false);
        Feed feed = fixture.subscribe("Battery", 1, FeedItem.NEW);
        unplugDevice();

        runAutoDownload();

        awaitAllDownloadsFinished();
        assertFalse(isDownloaded(feed.getItemAtIndex(0)));
        assertTrue(fixture.requestsFor(feed.getItemAtIndex(0).getMedia().getDownloadUrl()).isEmpty());
    }

    @Test
    public void episodesAreDownloadedOnBatteryWhenTheUserAllowsIt() throws Exception {
        enableGlobalAutoDownload();
        DownloadTestFixture.putBoolean(UserPreferences.PREF_ENABLE_AUTODL_ON_BATTERY, true);
        Feed feed = fixture.subscribe("On battery", 1, FeedItem.NEW);
        unplugDevice();

        runAutoDownload();

        awaitDownloaded(feed.getItemAtIndex(0));
    }

    @Test
    public void playedEpisodesAreDeletedToMakeRoomForNewDownloads() throws Exception {
        enableGlobalAutoDownload();
        limitCacheTo(1);
        cleanupWith(UserPreferences.EPISODE_CLEANUP_DEFAULT);
        Feed old = fixture.subscribe("Old", 1);
        fixture.markDownloaded(old.getItemAtIndex(0));
        fixture.markPlayed(old.getItemAtIndex(0), System.currentTimeMillis() - 2 * DAY_MILLIS);
        Feed fresh = fixture.subscribe("Fresh", 1, FeedItem.NEW);

        runAutoDownload();

        awaitDownloaded(fresh.getItemAtIndex(0));
        assertFalse(isDownloaded(old.getItemAtIndex(0)));
        assertEquals(1, downloadedEpisodes());
    }

    @Test
    public void performAutoCleanupDeletesPlayedEpisodesBeyondTheCacheSize() throws Exception {
        limitCacheTo(1);
        cleanupWith(UserPreferences.EPISODE_CLEANUP_DEFAULT);
        Feed feed = fixture.subscribe("Cleaned", 3);
        for (int i = 0; i < 3; i++) {
            fixture.markDownloaded(feed.getItemAtIndex(i));
            fixture.markPlayed(feed.getItemAtIndex(i), System.currentTimeMillis() - (3 - i) * DAY_MILLIS);
        }

        AutoDownloadManager.getInstance().performAutoCleanup(context);

        assertEquals(1, downloadedEpisodes());
        assertTrue(isDownloaded(feed.getItemAtIndex(2)));
    }

    @Test
    public void defaultCleanupOnlyRemovesPlayedEpisodesThatAreNotQueuedOrFavourites() throws Exception {
        limitCacheTo(2);
        Feed feed = fixture.subscribe("Default cleanup", 5);
        for (int i = 0; i < 5; i++) {
            fixture.markDownloaded(feed.getItemAtIndex(i));
        }
        long now = System.currentTimeMillis();
        fixture.markPlayed(feed.getItemAtIndex(0), now - 5 * DAY_MILLIS);
        fixture.markPlayed(feed.getItemAtIndex(1), now - 4 * DAY_MILLIS);
        fixture.markPlayed(feed.getItemAtIndex(2), now - 3 * DAY_MILLIS);
        DBWriter.addQueueItem(context, feed.getItemAtIndex(0)).get();
        DBWriter.addFavoriteItems(Arrays.asList(feed.getItemAtIndex(1))).get();
        APCleanupAlgorithm algorithm = (APCleanupAlgorithm) cleanupWith(UserPreferences.EPISODE_CLEANUP_DEFAULT);
        assertEquals(1, algorithm.getReclaimableItems());

        assertEquals(1, algorithm.makeRoomForEpisodes(context, 0));

        assertFalse(isDownloaded(feed.getItemAtIndex(2)));
        assertTrue(isDownloaded(feed.getItemAtIndex(0)));
        assertTrue(isDownloaded(feed.getItemAtIndex(1)));
        assertTrue(isDownloaded(feed.getItemAtIndex(3)));
        assertTrue(isDownloaded(feed.getItemAtIndex(4)));
    }

    @Test
    public void queueCleanupKeepsQueuedEpisodesAndFavourites() throws Exception {
        limitCacheTo(1);
        Feed feed = fixture.subscribe("Queue cleanup", 4);
        for (int i = 0; i < 4; i++) {
            fixture.markDownloaded(feed.getItemAtIndex(i));
        }
        DBWriter.addQueueItem(context, feed.getItemAtIndex(0)).get();
        DBWriter.addFavoriteItems(Arrays.asList(feed.getItemAtIndex(1))).get();
        EpisodeCleanupAlgorithm algorithm = cleanupWith(UserPreferences.EPISODE_CLEANUP_QUEUE);
        assertTrue(algorithm instanceof APQueueCleanupAlgorithm);
        assertEquals(2, algorithm.getReclaimableItems());

        assertEquals(2, algorithm.makeRoomForEpisodes(context, 1));

        assertTrue(isDownloaded(feed.getItemAtIndex(0)));
        assertTrue(isDownloaded(feed.getItemAtIndex(1)));
        assertFalse(isDownloaded(feed.getItemAtIndex(2)));
        assertFalse(isDownloaded(feed.getItemAtIndex(3)));
    }

    @Test
    public void favouriteCleanupOnlyKeepsFavourites() throws Exception {
        limitCacheTo(2);
        Feed feed = fixture.subscribe("Favourite cleanup", 4);
        for (int i = 0; i < 4; i++) {
            fixture.markDownloaded(feed.getItemAtIndex(i));
        }
        DBWriter.addFavoriteItems(Arrays.asList(feed.getItemAtIndex(3))).get();
        EpisodeCleanupAlgorithm algorithm = cleanupWith(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE);
        assertTrue(algorithm instanceof ExceptFavoriteCleanupAlgorithm);
        assertEquals(3, algorithm.getReclaimableItems());

        assertEquals(2, algorithm.performCleanup(context));

        assertTrue(isDownloaded(feed.getItemAtIndex(3)));
        assertTrue(isDownloaded(feed.getItemAtIndex(0)));
        assertFalse(isDownloaded(feed.getItemAtIndex(1)));
        assertFalse(isDownloaded(feed.getItemAtIndex(2)));
    }

    @Test
    public void nullCleanupNeverDeletesAnything() throws Exception {
        limitCacheTo(1);
        Feed feed = fixture.subscribe("Null cleanup", 2);
        fixture.markDownloaded(feed.getItemAtIndex(0));
        fixture.markDownloaded(feed.getItemAtIndex(1));
        EpisodeCleanupAlgorithm algorithm = cleanupWith(UserPreferences.EPISODE_CLEANUP_NULL);
        assertTrue(algorithm instanceof APNullCleanupAlgorithm);

        assertEquals(0, algorithm.makeRoomForEpisodes(context, 5));
        assertEquals(0, algorithm.performCleanup(context));
        assertEquals(0, algorithm.getReclaimableItems());

        assertEquals(2, downloadedEpisodes());
    }

    @Test
    public void cleanupDoesNothingWhileTheCacheIsUnlimited() throws Exception {
        limitCacheTo(UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED);
        Feed feed = fixture.subscribe("Unlimited", 2);
        fixture.markDownloaded(feed.getItemAtIndex(0));
        fixture.markDownloaded(feed.getItemAtIndex(1));

        assertEquals(0, cleanupWith(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE).makeRoomForEpisodes(context, 3));
        assertEquals(0, cleanupWith(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE).performCleanup(context));

        assertEquals(2, downloadedEpisodes());
    }
}
