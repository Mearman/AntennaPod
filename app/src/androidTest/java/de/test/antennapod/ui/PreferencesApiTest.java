package de.test.antennapod.ui;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.download.ProxyConfig;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedOrder;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.feed.SubscriptionsFilter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.storage.preferences.SleepTimerType;
import de.danoeh.antennapod.storage.preferences.UsageStatistics;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation;
import de.danoeh.antennapod.storage.preferences.UserPreferences.ThemePreference;
import de.test.antennapod.EspressoTestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class PreferencesApiTest {
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(UserPreferences.PREF_UPDATE_INTERVAL_MINUTES, "0").commit();
        UserPreferences.init(context);
        SleepTimerPreferences.init(context);
        PlaybackPreferences.init(context);
        UsageStatistics.init(context);
    }

    private void put(String key, Object value) {
        SharedPreferences.Editor editor = prefs.edit();
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        }
        editor.commit();
    }

    @Test
    public void testThemePreferenceRoundTrip() {
        UserPreferences.setTheme(ThemePreference.LIGHT);
        assertEquals(ThemePreference.LIGHT, UserPreferences.getTheme());
        UserPreferences.setTheme(ThemePreference.DARK);
        assertEquals(ThemePreference.DARK, UserPreferences.getTheme());
        UserPreferences.setTheme(ThemePreference.SYSTEM);
        assertEquals(ThemePreference.SYSTEM, UserPreferences.getTheme());
        UserPreferences.setTheme(ThemePreference.BLACK);
        assertEquals(ThemePreference.SYSTEM, UserPreferences.getTheme());
    }

    @Test
    public void testParentalControlPassword() {
        assertFalse(UserPreferences.isParentalControlPasswordSet());
        UserPreferences.setParentalControlPassword("secret");
        assertTrue(UserPreferences.isParentalControlPasswordSet());
        assertTrue(UserPreferences.verifyParentalControlPassword("secret"));
        assertFalse(UserPreferences.verifyParentalControlPassword("wrong"));
        UserPreferences.clearParentalControlPassword();
        assertFalse(UserPreferences.isParentalControlPasswordSet());
        assertTrue(UserPreferences.isParentalControlRequireSubscribeSet());
    }

    @Test
    public void testDrawerItemOrderingHidesHiddenItems() {
        List<String> hidden = Collections.singletonList("QueueFragment");
        List<String> order = Arrays.asList("HomeFragment", "EpisodesFragment",
                "QueueFragment", "SubscriptionsFragment");
        UserPreferences.setDrawerItemOrder(hidden, order);
        assertEquals(hidden, UserPreferences.getHiddenDrawerItems());
        List<String> visible = UserPreferences.getVisibleDrawerItemOrder();
        assertFalse(visible.contains("QueueFragment"));
        assertTrue(visible.size() > 1);
    }

    @Test
    public void testFullNotificationButtons() {
        UserPreferences.setFullNotificationButtons(Arrays.asList(
                UserPreferences.NOTIFICATION_BUTTON_SKIP,
                UserPreferences.NOTIFICATION_BUTTON_PLAYBACK_SPEED));
        assertTrue(UserPreferences.showSkipOnFullNotification());
        assertTrue(UserPreferences.showPlaybackSpeedOnFullNotification());
        assertFalse(UserPreferences.showNextChapterOnFullNotification());
        assertFalse(UserPreferences.showSleepTimerOnFullNotification());
    }

    @Test
    public void testFeedOrderAndCounter() {
        UserPreferences.setFeedOrder(FeedOrder.ALPHABETICAL);
        assertEquals(FeedOrder.ALPHABETICAL, UserPreferences.getFeedOrder());
        UserPreferences.setFeedCounterSetting(FeedCounter.SHOW_NONE);
        assertEquals(FeedCounter.SHOW_NONE, UserPreferences.getFeedCounterSetting());
    }

    @Test
    public void testDisplayPreferences() {
        put(UserPreferences.PREF_USE_EPISODE_COVER, false);
        assertFalse(UserPreferences.getUseEpisodeCoverSetting());
        UserPreferences.setShowRemainTimeSetting(true);
        assertTrue(UserPreferences.shouldShowRemainingTime());
        put(UserPreferences.PREF_EXPANDED_NOTIFICATION, true);
        assertTrue(UserPreferences.getNotifyPriority() > 0);
        put(UserPreferences.PREF_PERSISTENT_NOTIFICATION, false);
        assertFalse(UserPreferences.isPersistNotify());
        assertTrue(UserPreferences.getShowDownloadReportRaw());
        assertTrue(UserPreferences.enqueueDownloadedEpisodes());
    }

    @Test
    public void testEnqueueLocationRejectsInvalidValue() {
        UserPreferences.setEnqueueLocation(EnqueueLocation.FRONT);
        assertEquals(EnqueueLocation.FRONT, UserPreferences.getEnqueueLocation());
        UserPreferences.setEnqueueLocation(EnqueueLocation.AFTER_CURRENTLY_PLAYING);
        assertEquals(EnqueueLocation.AFTER_CURRENTLY_PLAYING, UserPreferences.getEnqueueLocation());
        UserPreferences.setEnqueueLocation(EnqueueLocation.RANDOM);
        assertEquals(EnqueueLocation.RANDOM, UserPreferences.getEnqueueLocation());
        put(UserPreferences.PREF_ENQUEUE_LOCATION, "not-a-location");
        assertEquals(EnqueueLocation.BACK, UserPreferences.getEnqueueLocation());
    }

    @Test
    public void testPlaybackBehaviourPreferences() {
        put(UserPreferences.PREF_PAUSE_ON_HEADSET_DISCONNECT, false);
        assertFalse(UserPreferences.isPauseOnHeadsetDisconnect());
        UserPreferences.setFollowQueue(false);
        assertFalse(UserPreferences.isFollowQueue());
        put(UserPreferences.PREF_SKIP_KEEPS_EPISODE, false);
        assertFalse(UserPreferences.shouldSkipKeepEpisode());
        put(UserPreferences.PREF_FAVORITE_KEEPS_EPISODE, false);
        assertFalse(UserPreferences.shouldFavoriteKeepEpisode());
        put("prefAutoDeleteLocal", true);
        assertTrue(UserPreferences.isAutoDeleteLocal());
        put(UserPreferences.PREF_SMART_MARK_AS_PLAYED_SECS, "60");
        assertEquals(60, UserPreferences.getSmartMarkAsPlayedSecs());
        put(UserPreferences.PREF_DELETE_REMOVES_FROM_QUEUE, true);
        assertTrue(UserPreferences.shouldDeleteRemoveFromQueue());
        put(UserPreferences.PREF_DOWNLOADS_BUTTON_ACTION, true);
        assertTrue(UserPreferences.shouldDownloadsButtonActionPlay());
        put(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, false);
        assertFalse(UserPreferences.shouldPauseForFocusLoss());
        put(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, "88");
        assertEquals(88, UserPreferences.getHardwareForwardButton());
        put(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON, "87");
        assertEquals(87, UserPreferences.getHardwarePreviousButton());
        put("prefPlaybackTimeRespectsSpeed", true);
        assertTrue(UserPreferences.timeRespectsSpeed());
    }

    @Test
    public void testPlaybackSpeedPreferences() {
        UserPreferences.setPlaybackSpeed(1.75f);
        assertEquals(1.75f, UserPreferences.getPlaybackSpeed(), 0.001f);
        put("prefPlaybackSpeed", "not-a-number");
        assertEquals(1.0f, UserPreferences.getPlaybackSpeed(), 0.001f);
        UserPreferences.setSkipSilence(true);
        assertTrue(UserPreferences.isSkipSilence());
        UserPreferences.setPlaybackSpeedArray(Arrays.asList(0.5f, 1.0f, 2.0f));
        List<Float> speeds = UserPreferences.getPlaybackSpeedArray();
        assertEquals(3, speeds.size());
        assertEquals(2.0f, speeds.get(2), 0.001f);
        UserPreferences.setFastForwardSecs(60);
        assertEquals(60, UserPreferences.getFastForwardSecs());
        UserPreferences.setRewindSecs(15);
        assertEquals(15, UserPreferences.getRewindSecs());
    }

    @Test
    public void testUpdateIntervalAndMobileUpdatePreferences() {
        UserPreferences.setUpdateInterval(720);
        assertEquals(720, UserPreferences.getUpdateInterval());
        assertFalse(UserPreferences.isAutoUpdateDisabled());
        UserPreferences.setUpdateInterval(0);
        assertTrue(UserPreferences.isAutoUpdateDisabled());

        UserPreferences.setAllowMobileSync(true);
        UserPreferences.setAllowMobileFeedRefresh(true);
        UserPreferences.setAllowMobileEpisodeDownload(true);
        UserPreferences.setAllowMobileAutoDownload(true);
        UserPreferences.setAllowMobileStreaming(true);
        UserPreferences.setAllowMobileImages(false);
        assertTrue(UserPreferences.isAllowMobileSync());
        assertTrue(UserPreferences.isAllowMobileFeedRefresh());
        assertTrue(UserPreferences.isAllowMobileEpisodeDownload());
        assertTrue(UserPreferences.isAllowMobileAutoDownload());
        assertTrue(UserPreferences.isAllowMobileStreaming());
        assertFalse(UserPreferences.isAllowMobileImages());
        UserPreferences.setAllowMobileSync(false);
        assertFalse(UserPreferences.isAllowMobileSync());
    }

    @Test
    public void testDownloadPreferences() {
        put(UserPreferences.PREF_EPISODE_CACHE_SIZE, "50");
        assertEquals(50, UserPreferences.getEpisodeCacheSize());
        put(UserPreferences.PREF_AUTODL_GLOBAL, true);
        assertTrue(UserPreferences.isEnableAutodownloadGlobal());
        put(UserPreferences.PREF_AUTODL_QUEUE, true);
        assertTrue(UserPreferences.isEnableAutodownloadQueue());
        put(UserPreferences.PREF_ENABLE_AUTODL_ON_BATTERY, false);
        assertFalse(UserPreferences.isEnableAutodownloadOnBattery());
        UserPreferences.setEpisodeCleanupValue(UserPreferences.EPISODE_CLEANUP_QUEUE);
        assertEquals(UserPreferences.EPISODE_CLEANUP_QUEUE, UserPreferences.getEpisodeCleanupValue());
        UserPreferences.setEpisodeCleanupValue(72);
        assertEquals(72, UserPreferences.getEpisodeCleanupValue());
    }

    @Test
    public void testProxyConfigRoundTrip() {
        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP,
                "127.0.0.1", 8080, "user", "pass"));
        ProxyConfig config = UserPreferences.getProxyConfig();
        assertEquals(Proxy.Type.HTTP, config.type);
        assertEquals("127.0.0.1", config.host);
        assertEquals(8080, config.port);
        assertEquals("user", config.username);
        assertEquals("pass", config.password);
        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.DIRECT, null, 0, null, null));
        config = UserPreferences.getProxyConfig();
        assertEquals(Proxy.Type.DIRECT, config.type);
        assertNull(config.host);
    }

    @Test
    public void testQueueAndSortingPreferences() {
        UserPreferences.setQueueLocked(true);
        assertTrue(UserPreferences.isQueueLocked());
        UserPreferences.setQueueKeepSorted(true);
        assertTrue(UserPreferences.isQueueKeepSorted());
        UserPreferences.setQueueKeepSortedOrder(SortOrder.EPISODE_TITLE_A_Z);
        assertEquals(SortOrder.EPISODE_TITLE_A_Z, UserPreferences.getQueueKeepSortedOrder());
        UserPreferences.setDownloadsSortedOrder(SortOrder.DATE_OLD_NEW);
        assertEquals(SortOrder.DATE_OLD_NEW, UserPreferences.getDownloadsSortedOrder());
        UserPreferences.setInboxSortedOrder(SortOrder.DURATION_SHORT_LONG);
        assertEquals(SortOrder.DURATION_SHORT_LONG, UserPreferences.getInboxSortedOrder());
        UserPreferences.setPrefGlobalSortedOrder(SortOrder.DURATION_LONG_SHORT);
        assertEquals(SortOrder.DURATION_LONG_SHORT, UserPreferences.getPrefGlobalSortedOrder());
        UserPreferences.setAllEpisodesSortOrder(SortOrder.EPISODE_TITLE_Z_A);
        assertEquals(SortOrder.EPISODE_TITLE_Z_A, UserPreferences.getAllEpisodesSortOrder());
        UserPreferences.setPrefFilterAllEpisodes("played");
        assertEquals("played", UserPreferences.getPrefFilterAllEpisodes());
    }

    @Test
    public void testNavigationPreferences() {
        UserPreferences.setDefaultPage("QueueFragment");
        assertEquals("QueueFragment", UserPreferences.getDefaultPage());
        put(UserPreferences.PREF_BACK_OPENS_DRAWER, true);
        assertTrue(UserPreferences.backButtonOpensDrawer());
        UserPreferences.setBottomNavigationEnabled(false);
        assertFalse(UserPreferences.isBottomNavigationEnabled());
        UserPreferences.setStreamOverDownload(true);
        assertTrue(UserPreferences.isStreamOverDownload());
        put(UserPreferences.PREF_NEW_EPISODES_ACTION,
                "" + FeedPreferences.NewEpisodesAction.NOTHING.code);
        assertEquals(FeedPreferences.NewEpisodesAction.NOTHING, UserPreferences.getNewEpisodesAction());
        SubscriptionsFilter filter = new SubscriptionsFilter("hidden");
        UserPreferences.setSubscriptionsFilter(filter);
        assertEquals("hidden", UserPreferences.getSubscriptionsFilter().serialize());
        UserPreferences.setShouldShowSubscriptionTitle(true);
        assertTrue(UserPreferences.shouldShowSubscriptionTitle());
    }

    @Test
    public void testAutomaticBackupAndGpodderNotificationPreferences() {
        UserPreferences.setAutomaticExportFolder("content://mock/folder");
        assertEquals("content://mock/folder", UserPreferences.getAutomaticExportFolder());
        UserPreferences.setAutomaticExportFolder(null);
        assertNull(UserPreferences.getAutomaticExportFolder());
        UserPreferences.setGpodnetNotificationsEnabled();
        assertTrue(UserPreferences.getGpodnetNotificationsEnabledRaw());
        assertTrue(UserPreferences.gpodnetNotificationsEnabled());
    }

    @Test
    public void testCustomDataFolderIsUsedForTypeSubfolders() {
        File custom = new File(context.getExternalFilesDir(null), "custom-data-folder");
        assertTrue(custom.mkdirs());
        UserPreferences.setDataFolder(custom.getAbsolutePath());
        File mediaFolder = UserPreferences.getDataFolder("media");
        assertEquals(new File(custom, "media").getAbsolutePath(), mediaFolder.getAbsolutePath());
        UserPreferences.setDataFolder(context.getExternalFilesDir(null).getAbsolutePath());
    }

    @Test
    public void testInitCreatesNoMediaFile() {
        File noMedia = new File(context.getExternalFilesDir(null), ".nomedia");
        assertTrue(noMedia.exists());
    }

    @Test
    public void testSleepTimerPreferences() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        SleepTimerPreferences.setLastTimer("25");
        assertEquals("25", SleepTimerPreferences.lastTimerValue());
        assertEquals(TimeUnit.MINUTES.toMillis(25), SleepTimerPreferences.timerMillisOrEpisodes());
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.EPISODES);
        SleepTimerPreferences.setLastTimer("3");
        assertEquals("3", SleepTimerPreferences.lastTimerValue());
        assertEquals(3, SleepTimerPreferences.timerMillisOrEpisodes());
        SleepTimerPreferences.setVibrate(true);
        assertTrue(SleepTimerPreferences.vibrate());
        SleepTimerPreferences.setShakeToReset(false);
        assertFalse(SleepTimerPreferences.shakeToReset());
        SleepTimerPreferences.setAutoEnable(true);
        assertTrue(SleepTimerPreferences.autoEnable());
        SleepTimerPreferences.setAutoEnableFrom(22);
        SleepTimerPreferences.setAutoEnableTo(6);
        assertEquals(22, SleepTimerPreferences.autoEnableFrom());
        assertEquals(6, SleepTimerPreferences.autoEnableTo());
        assertEquals(8, SleepTimerPreferences.autoEnableDuration());
        SleepTimerPreferences.setAutoEnableTo(2);
        assertEquals(4, SleepTimerPreferences.autoEnableDuration());
        assertTrue(SleepTimerPreferences.isInTimeRange(22, 6, 23));
        assertTrue(SleepTimerPreferences.isInTimeRange(22, 6, 3));
        assertFalse(SleepTimerPreferences.isInTimeRange(22, 6, 10));
        assertTrue(SleepTimerPreferences.isInTimeRange(6, 10, 7));
    }

    @Test
    public void testUsageStatisticsDetectsStreamingBias() {
        for (int i = 0; i < 20; i++) {
            UsageStatistics.logAction(UsageStatistics.ACTION_STREAM);
        }
        assertTrue(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD));
        UsageStatistics.doNotAskAgain(UsageStatistics.ACTION_STREAM);
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
    }

    @Test
    public void testPlaybackPreferencesTemporarySettings() {
        PlaybackPreferences.writeNoMediaPlaying();
        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING,
                PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertEquals(PlaybackPreferences.PLAYER_STATUS_OTHER,
                PlaybackPreferences.getCurrentPlayerStatus());
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING);
        assertEquals(PlaybackPreferences.PLAYER_STATUS_PLAYING,
                PlaybackPreferences.getCurrentPlayerStatus());
        PlaybackPreferences.setCurrentlyPlayingTemporaryPlaybackSpeed(1.5f);
        assertEquals(1.5f, PlaybackPreferences.getCurrentlyPlayingTemporaryPlaybackSpeed(), 0.001f);
        PlaybackPreferences.setCurrentlyPlayingTemporarySkipSilence(true);
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE,
                PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence());
        PlaybackPreferences.clearCurrentlyPlayingTemporaryPlaybackSettings();
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL,
                PlaybackPreferences.getCurrentlyPlayingTemporaryPlaybackSpeed(), 0.001f);
    }
}
