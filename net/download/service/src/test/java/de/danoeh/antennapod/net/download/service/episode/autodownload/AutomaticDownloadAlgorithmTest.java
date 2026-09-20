package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;

@RunWith(RobolectricTestRunner.class)
public class AutomaticDownloadAlgorithmTest {
    private final Context context = Mockito.mock(Context.class);
    private final RecordingDownloads downloads = new RecordingDownloads();
    private MockedStatic<NetworkUtils> network;
    private MockedStatic<UserPreferences> preferences;
    private MockedStatic<DBReader> reader;

    private static class RecordingDownloads extends DownloadServiceInterface {
        private final List<Long> requestedEpisodeIds = new ArrayList<>();
        private int activeDownloads = 0;

        @Override
        public void downloadNow(Context context, FeedItem item, boolean ignoreConstraints) {
        }

        @Override
        public void download(Context context, FeedItem item) {
            requestedEpisodeIds.add(item.getId());
        }

        @Override
        public void cancel(Context context, FeedMedia media) {
        }

        @Override
        public void cancelAll(Context context) {
        }

        @Override
        public int getNumberOfActiveDownloads(Context context) {
            return activeDownloads;
        }
    }

    @Before
    public void setUp() {
        network = Mockito.mockStatic(NetworkUtils.class);
        preferences = Mockito.mockStatic(UserPreferences.class);
        reader = Mockito.mockStatic(DBReader.class);
        DownloadServiceInterface.setImpl(downloads);
        network.when(NetworkUtils::isAutoDownloadAllowed).thenReturn(true);
        preferences.when(UserPreferences::getEpisodeCleanupValue).thenReturn(UserPreferences.EPISODE_CLEANUP_NULL);
        preferences.when(UserPreferences::getEpisodeCacheSize).thenReturn(UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED);
        preferences.when(UserPreferences::isEnableAutodownloadOnBattery).thenReturn(true);
        reader.when(() -> DBReader.getTotalEpisodeCount(any())).thenReturn(0);
    }

    @After
    public void tearDown() {
        DownloadServiceInterface.setImpl(null);
        reader.close();
        preferences.close();
        network.close();
    }

    private static FeedPreferences.AutoDownloadSetting feedSetting(boolean enabled) {
        return enabled ? FeedPreferences.AutoDownloadSetting.ENABLED : FeedPreferences.AutoDownloadSetting.DISABLED;
    }

    private static FeedItem candidate(long id, FeedPreferences.AutoDownloadSetting setting, Feed feed) {
        FeedPreferences feedPreferences = new FeedPreferences(0, setting, FeedPreferences.AutoDeleteAction.GLOBAL,
                VolumeAdaptionSetting.OFF, FeedPreferences.NewEpisodesAction.GLOBAL, null, null);
        feed.setPreferences(feedPreferences);
        FeedItem item = new FeedItem(id, "Episode " + id, "guid" + id, "http://example.com/" + id, new Date(id),
                FeedItem.UNPLAYED, feed);
        item.setMedia(new FeedMedia(id, item, 0, 0, 0, "audio/mpeg", null, "http://example.com/" + id + ".mp3", 0,
                null, 0, 0));
        return item;
    }

    private static FeedItem candidate(long id, FeedPreferences.AutoDownloadSetting setting) {
        return candidate(id, setting, new Feed("http://example.com/feed.xml", null, "Feed"));
    }

    private void setCandidates(FeedItem... items) {
        reader.when(() -> DBReader.getAutoDownloadCandidates(anyBoolean(), anyBoolean()))
                .thenReturn(new ArrayList<>(Arrays.asList(items)));
    }

    private void run() {
        new AutomaticDownloadAlgorithm().autoDownloadUndownloadedItems(context).run();
    }

    private void setBatteryStatus(int status) {
        Intent batteryStatus = new Intent().putExtra(BatteryManager.EXTRA_STATUS, status);
        Mockito.when(context.registerReceiver(isNull(), any(IntentFilter.class))).thenReturn(batteryStatus);
    }

    @Test
    public void deviceIsChargingWhileBatteryStatusIsCharging() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        assertTrue(AutomaticDownloadAlgorithm.deviceCharging(context));
    }

    @Test
    public void deviceIsChargingWhileBatteryStatusIsFull() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_FULL);
        assertTrue(AutomaticDownloadAlgorithm.deviceCharging(context));
    }

    @Test
    public void deviceIsNotChargingWhileDischarging() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_DISCHARGING);
        assertFalse(AutomaticDownloadAlgorithm.deviceCharging(context));
    }

    @Test
    public void nothingIsDownloadedWhenNetworkDoesNotAllowAutoDownload() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        network.when(NetworkUtils::isAutoDownloadAllowed).thenReturn(false);
        setCandidates(candidate(1, feedSetting(true)));

        run();

        assertEquals(Collections.emptyList(), downloads.requestedEpisodeIds);
        reader.verify(() -> DBReader.getAutoDownloadCandidates(anyBoolean(), anyBoolean()), Mockito.never());
    }

    @Test
    public void nothingIsDownloadedOnBatteryWhenUserForbidsIt() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_DISCHARGING);
        preferences.when(UserPreferences::isEnableAutodownloadOnBattery).thenReturn(false);
        setCandidates(candidate(1, feedSetting(true)));

        run();

        assertEquals(Collections.emptyList(), downloads.requestedEpisodeIds);
    }

    @Test
    public void downloadsOnBatteryWhenUserAllowsIt() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_DISCHARGING);
        setCandidates(candidate(1, feedSetting(true)));

        run();

        assertEquals(Collections.singletonList(1L), downloads.requestedEpisodeIds);
    }

    @Test
    public void downloadsWhileChargingEvenWhenBatteryDownloadsAreForbidden() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        preferences.when(UserPreferences::isEnableAutodownloadOnBattery).thenReturn(false);
        setCandidates(candidate(1, feedSetting(true)));

        run();

        assertEquals(Collections.singletonList(1L), downloads.requestedEpisodeIds);
    }

    @Test
    public void feedSettingOverridesGlobalAutoDownloadSetting() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        preferences.when(UserPreferences::isEnableAutodownloadGlobal).thenReturn(true);
        setCandidates(candidate(1, feedSetting(false)), candidate(2, feedSetting(true)));

        run();

        assertEquals(Collections.singletonList(2L), downloads.requestedEpisodeIds);
    }

    @Test
    public void feedsFollowingTheGlobalSettingAreOnlyDownloadedWhenGlobalIsEnabled() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        setCandidates(candidate(1, FeedPreferences.AutoDownloadSetting.GLOBAL));

        run();
        assertEquals(Collections.emptyList(), downloads.requestedEpisodeIds);

        preferences.when(UserPreferences::isEnableAutodownloadGlobal).thenReturn(true);
        run();
        assertEquals(Collections.singletonList(1L), downloads.requestedEpisodeIds);
    }

    @Test
    public void queuedEpisodesAreDownloadedWhenQueueAutoDownloadIsEnabled() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        FeedItem queued = candidate(1, feedSetting(false));
        queued.addTag(FeedItem.TAG_QUEUE);
        setCandidates(queued);

        run();
        assertEquals(Collections.emptyList(), downloads.requestedEpisodeIds);

        preferences.when(UserPreferences::isEnableAutodownloadQueue).thenReturn(true);
        run();
        assertEquals(Collections.singletonList(1L), downloads.requestedEpisodeIds);
    }

    @Test
    public void episodesExcludedByTheFeedFilterAreNotDownloaded() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        FeedItem excluded = candidate(1, feedSetting(true));
        excluded.getFeed().getPreferences().setFilter(new FeedFilter("", "Episode 1"));
        setCandidates(excluded, candidate(2, feedSetting(true)));

        run();

        assertEquals(Collections.singletonList(2L), downloads.requestedEpisodeIds);
    }

    @Test
    public void episodesThatCannotBeAutoDownloadedAreSkipped() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        FeedItem optedOut = candidate(1, feedSetting(true));
        optedOut.disableAutoDownload();
        FeedItem alreadyDownloaded = candidate(2, feedSetting(true));
        alreadyDownloaded.getMedia().setDownloaded(true, 1);
        FeedItem withoutMedia = candidate(3, feedSetting(true));
        withoutMedia.setMedia(null);
        FeedItem local = candidate(4, feedSetting(true),
                new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://tree/folder", null, "Local"));
        FeedItem eligible = candidate(5, feedSetting(true));
        setCandidates(optedOut, alreadyDownloaded, withoutMedia, local, eligible);

        run();

        assertEquals(Collections.singletonList(5L), downloads.requestedEpisodeIds);
    }

    @Test
    public void everyCandidateIsDownloadedWhenTheCacheIsUnlimited() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        setCandidates(candidate(1, feedSetting(true)), candidate(2, feedSetting(true)),
                candidate(3, feedSetting(true)));

        run();

        assertEquals(Arrays.asList(1L, 2L, 3L), downloads.requestedEpisodeIds);
    }

    @Test
    public void downloadsAreLimitedToTheSpaceLeftInTheEpisodeCache() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        preferences.when(UserPreferences::getEpisodeCacheSize).thenReturn(2);
        setCandidates(candidate(1, feedSetting(true)), candidate(2, feedSetting(true)),
                candidate(3, feedSetting(true)));

        run();

        assertEquals(Arrays.asList(1L, 2L), downloads.requestedEpisodeIds);
    }

    @Test
    public void runningDownloadsOccupyEpisodeCacheSpace() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        preferences.when(UserPreferences::getEpisodeCacheSize).thenReturn(3);
        reader.when(() -> DBReader.getTotalEpisodeCount(any())).thenReturn(1);
        downloads.activeDownloads = 1;
        setCandidates(candidate(1, feedSetting(true)), candidate(2, feedSetting(true)),
                candidate(3, feedSetting(true)));

        run();

        assertEquals(Collections.singletonList(1L), downloads.requestedEpisodeIds);
    }

    @Test
    public void everyCandidateIsDownloadedWhenTheCacheHasRoomForAll() {
        setBatteryStatus(BatteryManager.BATTERY_STATUS_CHARGING);
        preferences.when(UserPreferences::getEpisodeCacheSize).thenReturn(10);
        reader.when(() -> DBReader.getTotalEpisodeCount(any())).thenReturn(2);
        setCandidates(candidate(1, feedSetting(true)), candidate(2, feedSetting(true)));

        run();

        assertEquals(Arrays.asList(1L, 2L), downloads.requestedEpisodeIds);
    }
}
