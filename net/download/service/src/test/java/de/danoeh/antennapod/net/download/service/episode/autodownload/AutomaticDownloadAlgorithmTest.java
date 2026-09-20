package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import androidx.preference.PreferenceManager;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.net.download.service.DownloadIntegrationTestBase;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@Category(IntegrationTest.class)
public class AutomaticDownloadAlgorithmTest extends DownloadIntegrationTestBase {
    private static final String PREF_AUTODL_ON_BATTERY = UserPreferences.PREF_ENABLE_AUTODL_ON_BATTERY;
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private DownloadServiceInterface downloadService;
    private final AutomaticDownloadAlgorithm algorithm = new AutomaticDownloadAlgorithm();

    @Before
    public void useRecordingDownloadService() {
        downloadService = Mockito.mock(DownloadServiceInterface.class);
        DownloadServiceInterface.setImpl(downloadService);
        setCharging(true);
        setNetwork(ConnectivityManager.TYPE_WIFI);
    }

    private void setCharging(boolean charging) {
        int status = charging ? BatteryManager.BATTERY_STATUS_CHARGING : BatteryManager.BATTERY_STATUS_DISCHARGING;
        context.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_STATUS, status));
    }

    private void setNetwork(int type) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(connectivityManager).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED, type, 0, true, NetworkInfo.State.CONNECTED));
    }

    private void setBooleanPreference(String key, boolean value) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, value).commit();
    }

    private Feed saveFeed(String title, FeedPreferences.AutoDownloadSetting setting, int state, int... ageInDays) {
        Feed unsaved = newFeed(title, server.url("/" + title + ".xml").toString());
        for (int i = 0; i < ageInDays.length; i++) {
            Date pubDate = new Date(System.currentTimeMillis() - ageInDays[i] * DAY_MILLIS);
            FeedItem item = new FeedItem(0, title + " episode " + i, title + "-guid-" + i, "link", pubDate, state,
                    unsaved);
            item.setMedia(new FeedMedia(item, "https://example.com/" + title + i + ".mp3", 0, "audio/mpeg"));
            unsaved.getItems().add(item);
        }
        Feed feed = saveFeed(unsaved);
        FeedPreferences preferences = feed.getPreferences();
        preferences.setAutoDownload(setting);
        DBWriter.setFeedPreferences(preferences);
        DBWriter.tearDownTests();
        return feed;
    }

    private List<String> downloadedTitles(int expectedCount) {
        ArgumentCaptor<FeedItem> captor = ArgumentCaptor.forClass(FeedItem.class);
        verify(downloadService, Mockito.times(expectedCount)).download(any(Context.class), captor.capture());
        return captor.getAllValues().stream().map(FeedItem::getTitle).collect(Collectors.toList());
    }

    private void runAlgorithm() {
        algorithm.autoDownloadUndownloadedItems(context).run();
    }

    private void verifyNothingDownloaded() {
        verify(downloadService, never()).download(any(Context.class), any(FeedItem.class));
    }

    @Test
    public void newEpisodesOfAutoDownloadFeedsAreDownloadedNewestFirst() {
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 3, 1, 2);

        runAlgorithm();

        assertEquals(List.of("alpha episode 1", "alpha episode 2", "alpha episode 0"), downloadedTitles(3));
    }

    @Test
    public void feedsWithAutoDownloadDisabledAreSkipped() {
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.DISABLED, FeedItem.NEW, 1);
        saveFeed("beta", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 1);

        runAlgorithm();

        assertEquals(List.of("beta episode 0"), downloadedTitles(1));
    }

    @Test
    public void feedsWithDefaultSettingFollowTheGlobalSwitch() {
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.GLOBAL, FeedItem.NEW, 1);

        runAlgorithm();
        verifyNothingDownloaded();

        setBooleanPreference(UserPreferences.PREF_AUTODL_GLOBAL, true);
        runAlgorithm();
        assertEquals(List.of("alpha episode 0"), downloadedTitles(1));
    }

    @Test
    public void globalSwitchDoesNotOverrideFeedsThatOptedOut() {
        setBooleanPreference(UserPreferences.PREF_AUTODL_GLOBAL, true);
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.DISABLED, FeedItem.NEW, 1);

        runAlgorithm();

        verifyNothingDownloaded();
    }

    @Test
    public void episodesThatAreNotNewAreSkipped() {
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.UNPLAYED, 1);

        runAlgorithm();

        verifyNothingDownloaded();
    }

    @Test
    public void episodesWithAutoDownloadDisabledAreSkipped() {
        Feed unsaved = newFeed("alpha", server.url("/alpha.xml").toString());
        FeedItem item = new FeedItem(0, "alpha episode", "guid", "link", new Date(), FeedItem.NEW, unsaved);
        item.setMedia(new FeedMedia(item, "https://example.com/a.mp3", 0, "audio/mpeg"));
        item.disableAutoDownload();
        unsaved.getItems().add(item);
        Feed feed = saveFeed(unsaved);
        FeedPreferences preferences = feed.getPreferences();
        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED);
        DBWriter.setFeedPreferences(preferences);
        DBWriter.tearDownTests();

        runAlgorithm();

        verifyNothingDownloaded();
    }

    @Test
    public void episodesWithoutMediaAreSkipped() {
        Feed unsaved = newFeed("alpha", server.url("/alpha.xml").toString());
        unsaved.getItems().add(new FeedItem(0, "text only", "guid", "link", new Date(), FeedItem.NEW, unsaved));
        Feed feed = saveFeed(unsaved);
        FeedPreferences preferences = feed.getPreferences();
        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED);
        DBWriter.setFeedPreferences(preferences);
        DBWriter.tearDownTests();

        runAlgorithm();

        verifyNothingDownloaded();
    }

    @Test
    public void alreadyDownloadedEpisodesAreSkipped() {
        Feed unsaved = newFeed("alpha", server.url("/alpha.xml").toString());
        FeedItem item = new FeedItem(0, "alpha episode", "guid", "link", new Date(), FeedItem.NEW, unsaved);
        item.setMedia(new FeedMedia(0, item, 0, 0, 100, "audio/mpeg", "/data/alpha.mp3", "https://example.com/a.mp3",
                System.currentTimeMillis(), null, 0, 0));
        unsaved.getItems().add(item);
        Feed feed = saveFeed(unsaved);
        FeedPreferences preferences = feed.getPreferences();
        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED);
        DBWriter.setFeedPreferences(preferences);
        DBWriter.tearDownTests();

        runAlgorithm();

        verifyNothingDownloaded();
    }

    @Test
    public void queuedEpisodesAreDownloadedOnlyWhenQueueAutoDownloadIsEnabled() {
        Feed feed = saveFeed("alpha", FeedPreferences.AutoDownloadSetting.DISABLED, FeedItem.UNPLAYED, 1);
        DBWriter.addQueueItem(context, feed.getItems().get(0));
        DBWriter.tearDownTests();

        runAlgorithm();
        verifyNothingDownloaded();

        setBooleanPreference(UserPreferences.PREF_AUTODL_QUEUE, true);
        runAlgorithm();
        assertEquals(List.of("alpha episode 0"), downloadedTitles(1));
    }

    @Test
    public void nothingIsDownloadedOnBatteryWhenDisallowed() {
        setCharging(false);
        setBooleanPreference(PREF_AUTODL_ON_BATTERY, false);
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 1);

        runAlgorithm();

        verifyNothingDownloaded();
    }

    @Test
    public void downloadsHappenOnBatteryWhenAllowed() {
        setCharging(false);
        setBooleanPreference(PREF_AUTODL_ON_BATTERY, true);
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 1);

        runAlgorithm();

        assertEquals(1, downloadedTitles(1).size());
    }

    @Test
    public void nothingIsDownloadedOnMobileNetworkByDefault() {
        setNetwork(ConnectivityManager.TYPE_MOBILE);
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 1);

        runAlgorithm();

        verifyNothingDownloaded();
    }

    @Test
    public void downloadsHappenOnMobileNetworkWhenAllowed() {
        setNetwork(ConnectivityManager.TYPE_MOBILE);
        UserPreferences.setAllowMobileAutoDownload(true);
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 1);

        runAlgorithm();

        assertEquals(1, downloadedTitles(1).size());
    }

    @Test
    public void downloadsHappenOnEthernet() {
        setNetwork(ConnectivityManager.TYPE_ETHERNET);
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 1);

        runAlgorithm();

        assertEquals(1, downloadedTitles(1).size());
    }

    @Test
    public void nothingIsDownloadedWithoutNetwork() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(connectivityManager).setActiveNetworkInfo(null);
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 1);

        runAlgorithm();

        verifyNothingDownloaded();
    }

    @Test
    public void limitedEpisodeCacheOnlyLetsNewestEpisodesThrough() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(UserPreferences.PREF_EPISODE_CACHE_SIZE, "2").commit();
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 3, 1, 2);

        runAlgorithm();

        assertEquals(List.of("alpha episode 1", "alpha episode 2"), downloadedTitles(2));
    }

    @Test
    public void activeDownloadsCountTowardsTheEpisodeCache() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(UserPreferences.PREF_EPISODE_CACHE_SIZE, "3").commit();
        when(downloadService.getNumberOfActiveDownloads(any(Context.class))).thenReturn(2);
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 3, 1, 2);

        runAlgorithm();

        assertEquals(List.of("alpha episode 1"), downloadedTitles(1));
    }

    @Test
    public void unlimitedEpisodeCacheDownloadsEverything() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(UserPreferences.PREF_EPISODE_CACHE_SIZE,
                        String.valueOf(UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED)).commit();
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 1, 2, 3, 4, 5);

        runAlgorithm();

        assertEquals(5, downloadedTitles(5).size());
    }

    @Test
    public void chargingAndFullBatteryStatesCountAsCharging() {
        for (int status : new int[] {BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL}) {
            context.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED)
                    .putExtra(BatteryManager.EXTRA_STATUS, status));
            assertTrue("status " + status, AutomaticDownloadAlgorithm.deviceCharging(context));
        }
    }

    @Test
    public void dischargingAndIdleBatteryStatesDoNotCountAsCharging() {
        for (int status : new int[] {BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_STATUS_UNKNOWN}) {
            context.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED)
                    .putExtra(BatteryManager.EXTRA_STATUS, status));
            assertFalse("status " + status, AutomaticDownloadAlgorithm.deviceCharging(context));
        }
    }

    @Test
    public void managerRunsAlgorithmOnItsExecutor() throws Exception {
        saveFeed("alpha", FeedPreferences.AutoDownloadSetting.ENABLED, FeedItem.NEW, 1);
        AutoDownloadManager manager = new AutoDownloadManagerImpl();

        manager.autodownloadUndownloadedItems(context).get();

        assertEquals(List.of("alpha episode 0"), downloadedTitles(1));
    }
}
