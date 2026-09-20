package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class UserPreferencesPlaybackTest {
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        UserPreferences.init(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Test
    public void playbackSpeedDefaultsToNormalAndIsPersisted() {
        assertEquals(1.0f, UserPreferences.getPlaybackSpeed(), 0f);

        UserPreferences.setPlaybackSpeed(1.5f);

        assertEquals(1.5f, UserPreferences.getPlaybackSpeed(), 0f);
    }

    @Test
    public void malformedStoredPlaybackSpeedIsResetToNormalSpeed() {
        prefs.edit().putString("prefPlaybackSpeed", "fast").commit();

        assertEquals(1.0f, UserPreferences.getPlaybackSpeed(), 0f);
        assertEquals("1.0", prefs.getString("prefPlaybackSpeed", null));
    }

    @Test
    public void playbackSpeedArrayDefaultsToStandardSpeeds() {
        assertEquals(Arrays.asList(1.0f, 1.25f, 1.5f), UserPreferences.getPlaybackSpeedArray());
    }

    @Test
    public void playbackSpeedArrayRoundTripsInGivenOrder() {
        UserPreferences.setPlaybackSpeedArray(Arrays.asList(2.5f, 0.8f, 1.0f));

        assertEquals(Arrays.asList(2.5f, 0.8f, 1.0f), UserPreferences.getPlaybackSpeedArray());
    }

    @Test
    public void playbackSpeedArrayIsStoredWithTwoDecimalsUsingDotSeparator() {
        UserPreferences.setPlaybackSpeedArray(Arrays.asList(1.257f, 3f));

        assertEquals("[\"1.26\",\"3.00\"]", prefs.getString("prefPlaybackSpeedArray", null));
    }

    @Test
    public void invalidStoredPlaybackSpeedArrayFallsBackToStandardSpeeds() {
        prefs.edit().putString("prefPlaybackSpeedArray", "not json").commit();

        assertEquals(Arrays.asList(1.0f, 1.25f, 1.5f), UserPreferences.getPlaybackSpeedArray());
    }

    @Test
    public void emptyPlaybackSpeedArrayIsPreserved() {
        UserPreferences.setPlaybackSpeedArray(List.of());

        assertTrue(UserPreferences.getPlaybackSpeedArray().isEmpty());
    }

    @Test
    public void skipSilenceIsOffByDefaultAndTogglable() {
        assertFalse(UserPreferences.isSkipSilence());

        UserPreferences.setSkipSilence(true);

        assertTrue(UserPreferences.isSkipSilence());
    }

    @Test
    public void skipIntervalsHaveDefaultsAndArePersisted() {
        assertEquals(30, UserPreferences.getFastForwardSecs());
        assertEquals(10, UserPreferences.getRewindSecs());

        UserPreferences.setFastForwardSecs(45);
        UserPreferences.setRewindSecs(5);

        assertEquals(45, UserPreferences.getFastForwardSecs());
        assertEquals(5, UserPreferences.getRewindSecs());
    }

    @Test
    public void hardwareButtonsDefaultToMediaKeysAndParseStoredKeyCodes() {
        assertEquals(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, UserPreferences.getHardwareForwardButton());
        assertEquals(KeyEvent.KEYCODE_MEDIA_REWIND, UserPreferences.getHardwarePreviousButton());

        prefs.edit()
                .putString(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, String.valueOf(KeyEvent.KEYCODE_MEDIA_NEXT))
                .putString(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON,
                        String.valueOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                .commit();

        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, UserPreferences.getHardwareForwardButton());
        assertEquals(KeyEvent.KEYCODE_MEDIA_PREVIOUS, UserPreferences.getHardwarePreviousButton());
    }

    @Test
    public void headsetAndBluetoothSettingsDefaultAndFollowStoredValues() {
        assertTrue(UserPreferences.isPauseOnHeadsetDisconnect());
        assertTrue(UserPreferences.isUnpauseOnHeadsetReconnect());
        assertFalse(UserPreferences.isUnpauseOnBluetoothReconnect());
        assertTrue(UserPreferences.shouldPauseForFocusLoss());

        prefs.edit()
                .putBoolean(UserPreferences.PREF_PAUSE_ON_HEADSET_DISCONNECT, false)
                .putBoolean(UserPreferences.PREF_UNPAUSE_ON_HEADSET_RECONNECT, false)
                .putBoolean(UserPreferences.PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT, true)
                .putBoolean(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, false)
                .commit();

        assertFalse(UserPreferences.isPauseOnHeadsetDisconnect());
        assertFalse(UserPreferences.isUnpauseOnHeadsetReconnect());
        assertTrue(UserPreferences.isUnpauseOnBluetoothReconnect());
        assertFalse(UserPreferences.shouldPauseForFocusLoss());
    }

    @Test
    public void followQueueDefaultsToTrueAndIsPersisted() {
        assertTrue(UserPreferences.isFollowQueue());

        UserPreferences.setFollowQueue(false);

        assertFalse(UserPreferences.isFollowQueue());
    }

    @Test
    public void skipAndFavoriteKeepEpisodeByDefaultAndCanBeChangedIndependently() {
        assertTrue(UserPreferences.shouldSkipKeepEpisode());
        assertTrue(UserPreferences.shouldFavoriteKeepEpisode());

        prefs.edit().putBoolean(UserPreferences.PREF_SKIP_KEEPS_EPISODE, false).commit();

        assertFalse(UserPreferences.shouldSkipKeepEpisode());
        assertTrue(UserPreferences.shouldFavoriteKeepEpisode());

        prefs.edit().putBoolean(UserPreferences.PREF_FAVORITE_KEEPS_EPISODE, false).commit();

        assertFalse(UserPreferences.shouldFavoriteKeepEpisode());
    }

    @Test
    public void autoDeleteIsDisabledByDefaultAndEachOptionIsStoredIndependently() {
        assertFalse(UserPreferences.isAutoDelete());
        assertFalse(UserPreferences.isAutoDeleteLocal());
        assertFalse(UserPreferences.shouldDeleteRemoveFromQueue());

        prefs.edit().putBoolean(UserPreferences.PREF_AUTO_DELETE, true).commit();

        assertTrue(UserPreferences.isAutoDelete());
        assertFalse(UserPreferences.isAutoDeleteLocal());
        assertFalse(UserPreferences.shouldDeleteRemoveFromQueue());

        prefs.edit().putBoolean("prefAutoDeleteLocal", true).commit();
        assertTrue(UserPreferences.isAutoDeleteLocal());
        assertFalse(UserPreferences.shouldDeleteRemoveFromQueue());

        prefs.edit().putBoolean(UserPreferences.PREF_DELETE_REMOVES_FROM_QUEUE, true).commit();
        assertTrue(UserPreferences.shouldDeleteRemoveFromQueue());
    }

    @Test
    public void smartMarkAsPlayedDefaultsToThirtySecondsAndParsesStoredValue() {
        assertEquals(30, UserPreferences.getSmartMarkAsPlayedSecs());

        prefs.edit().putString(UserPreferences.PREF_SMART_MARK_AS_PLAYED_SECS, "120").commit();

        assertEquals(120, UserPreferences.getSmartMarkAsPlayedSecs());
    }

    @Test
    public void downloadsButtonDoesNotPlayByDefault() {
        assertFalse(UserPreferences.shouldDownloadsButtonActionPlay());

        prefs.edit().putBoolean(UserPreferences.PREF_DOWNLOADS_BUTTON_ACTION, true).commit();

        assertTrue(UserPreferences.shouldDownloadsButtonActionPlay());
    }

    @Test
    public void timeRespectsSpeedIsDisabledByDefaultAndFollowsStoredValue() {
        assertFalse(UserPreferences.timeRespectsSpeed());

        prefs.edit().putBoolean("prefPlaybackTimeRespectsSpeed", true).commit();

        assertTrue(UserPreferences.timeRespectsSpeed());
    }

    @Test
    public void streamOverDownloadIsDisabledByDefaultAndTogglable() {
        assertFalse(UserPreferences.isStreamOverDownload());

        UserPreferences.setStreamOverDownload(true);

        assertTrue(UserPreferences.isStreamOverDownload());
    }

    @Test
    public void queueIsUnlockedByDefaultAndCanBeLocked() {
        assertFalse(UserPreferences.isQueueLocked());

        UserPreferences.setQueueLocked(true);

        assertTrue(UserPreferences.isQueueLocked());
    }

    @Test
    public void enqueueLocationDefaultsToBackAndIsPersisted() {
        assertEquals(UserPreferences.EnqueueLocation.BACK, UserPreferences.getEnqueueLocation());

        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.AFTER_CURRENTLY_PLAYING);

        assertEquals(UserPreferences.EnqueueLocation.AFTER_CURRENTLY_PLAYING, UserPreferences.getEnqueueLocation());
    }

    @Test
    public void invalidStoredEnqueueLocationFallsBackToBack() {
        prefs.edit().putString(UserPreferences.PREF_ENQUEUE_LOCATION, "MIDDLE").commit();

        assertEquals(UserPreferences.EnqueueLocation.BACK, UserPreferences.getEnqueueLocation());
    }

    @Test
    public void downloadedEpisodesAreEnqueuedByDefaultAndFollowStoredValue() {
        assertTrue(UserPreferences.enqueueDownloadedEpisodes());

        prefs.edit().putBoolean("prefEnqueueDownloaded", false).commit();

        assertFalse(UserPreferences.enqueueDownloadedEpisodes());
    }

    @Test
    public void queueKeepSortedIsOffByDefaultAndTogglable() {
        assertFalse(UserPreferences.isQueueKeepSorted());

        UserPreferences.setQueueKeepSorted(true);

        assertTrue(UserPreferences.isQueueKeepSorted());
    }

    @Test
    public void queueKeepSortedOrderDefaultsToNewestFirst() {
        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getQueueKeepSortedOrder());
    }

    @Test
    public void queueKeepSortedOrderIsPersisted() {
        UserPreferences.setQueueKeepSortedOrder(SortOrder.DURATION_LONG_SHORT);

        assertEquals(SortOrder.DURATION_LONG_SHORT, UserPreferences.getQueueKeepSortedOrder());
    }

    @Test
    public void settingNullQueueKeepSortedOrderKeepsPreviousOrder() {
        UserPreferences.setQueueKeepSortedOrder(SortOrder.DATE_OLD_NEW);

        UserPreferences.setQueueKeepSortedOrder(null);

        assertEquals(SortOrder.DATE_OLD_NEW, UserPreferences.getQueueKeepSortedOrder());
    }

    @Test
    public void unknownStoredQueueKeepSortedOrderFallsBackToNewestFirst() {
        prefs.edit().putString(UserPreferences.PREF_QUEUE_KEEP_SORTED_ORDER, "NOT_A_SORT_ORDER").commit();

        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getQueueKeepSortedOrder());
    }

    @Test
    public void newEpisodesActionDefaultsToInboxAndParsesStoredCode() {
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, UserPreferences.getNewEpisodesAction());

        prefs.edit().putString(UserPreferences.PREF_NEW_EPISODES_ACTION,
                String.valueOf(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE.code)).commit();

        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, UserPreferences.getNewEpisodesAction());
    }

    @Test
    public void listSortOrdersDefaultToNewestFirst() {
        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getDownloadsSortedOrder());
        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getInboxSortedOrder());
        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getPrefGlobalSortedOrder());
        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getAllEpisodesSortOrder());
    }

    @Test
    public void listSortOrdersAreStoredIndependently() {
        UserPreferences.setDownloadsSortedOrder(SortOrder.SIZE_LARGE_SMALL);
        UserPreferences.setInboxSortedOrder(SortOrder.EPISODE_TITLE_A_Z);
        UserPreferences.setPrefGlobalSortedOrder(SortOrder.DATE_OLD_NEW);
        UserPreferences.setAllEpisodesSortOrder(SortOrder.DURATION_SHORT_LONG);

        assertEquals(SortOrder.SIZE_LARGE_SMALL, UserPreferences.getDownloadsSortedOrder());
        assertEquals(SortOrder.EPISODE_TITLE_A_Z, UserPreferences.getInboxSortedOrder());
        assertEquals(SortOrder.DATE_OLD_NEW, UserPreferences.getPrefGlobalSortedOrder());
        assertEquals(SortOrder.DURATION_SHORT_LONG, UserPreferences.getAllEpisodesSortOrder());
    }

    @Test
    @Config(sdk = 26)
    public void gpodnetNotificationsAreAlwaysEnabledOnAndroid8AndLater() {
        prefs.edit().putBoolean("pref_gpodnet_notifications", false).commit();

        assertTrue(UserPreferences.gpodnetNotificationsEnabled());
        assertFalse(UserPreferences.getGpodnetNotificationsEnabledRaw());
    }

    @Test
    @Config(sdk = 25)
    public void gpodnetNotificationsFollowPreferenceBeforeAndroid8() {
        assertTrue(UserPreferences.gpodnetNotificationsEnabled());

        prefs.edit().putBoolean("pref_gpodnet_notifications", false).commit();

        assertFalse(UserPreferences.gpodnetNotificationsEnabled());
    }

    @Test
    public void enablingGpodnetNotificationsOverridesStoredFalse() {
        prefs.edit().putBoolean("pref_gpodnet_notifications", false).commit();

        UserPreferences.setGpodnetNotificationsEnabled();

        assertTrue(UserPreferences.getGpodnetNotificationsEnabledRaw());
    }
}
