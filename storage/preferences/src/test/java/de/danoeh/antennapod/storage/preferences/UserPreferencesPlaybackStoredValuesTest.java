package de.danoeh.antennapod.storage.preferences;

import android.view.KeyEvent;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class UserPreferencesPlaybackStoredValuesTest extends StoredPreferencesTestBase {

    @Test
    public void headsetAndBluetoothBehaviourDefaultsCanBeSwitchedThroughStoredFlags() {
        assertTrue(UserPreferences.isPauseOnHeadsetDisconnect());
        assertTrue(UserPreferences.isUnpauseOnHeadsetReconnect());
        assertFalse(UserPreferences.isUnpauseOnBluetoothReconnect());

        stored.edit().putBoolean(UserPreferences.PREF_PAUSE_ON_HEADSET_DISCONNECT, false)
                .putBoolean(UserPreferences.PREF_UNPAUSE_ON_HEADSET_RECONNECT, false)
                .putBoolean(UserPreferences.PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT, true).commit();

        assertFalse(UserPreferences.isPauseOnHeadsetDisconnect());
        assertFalse(UserPreferences.isUnpauseOnHeadsetReconnect());
        assertTrue(UserPreferences.isUnpauseOnBluetoothReconnect());
    }

    @Test
    public void hardwareButtonsAreStoredAsKeyCodeStrings() {
        assertEquals(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, UserPreferences.getHardwareForwardButton());
        assertEquals(KeyEvent.KEYCODE_MEDIA_REWIND, UserPreferences.getHardwarePreviousButton());

        stored.edit().putString(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON,
                        String.valueOf(KeyEvent.KEYCODE_MEDIA_NEXT))
                .putString(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON,
                        String.valueOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS)).commit();

        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, UserPreferences.getHardwareForwardButton());
        assertEquals(KeyEvent.KEYCODE_MEDIA_PREVIOUS, UserPreferences.getHardwarePreviousButton());
    }

    @Test
    public void followQueueIsOnByDefaultAndStoredWhenChanged() {
        assertTrue(UserPreferences.isFollowQueue());

        UserPreferences.setFollowQueue(false);

        assertFalse(stored.getBoolean(UserPreferences.PREF_FOLLOW_QUEUE, true));
        assertFalse(UserPreferences.isFollowQueue());
    }

    @Test
    public void episodeRetentionFlagsFollowTheStoredValues() {
        assertTrue(UserPreferences.shouldSkipKeepEpisode());
        assertTrue(UserPreferences.shouldFavoriteKeepEpisode());
        assertFalse(UserPreferences.isAutoDelete());
        assertFalse(UserPreferences.isAutoDeleteLocal());
        assertFalse(UserPreferences.shouldDeleteRemoveFromQueue());
        assertFalse(UserPreferences.shouldDownloadsButtonActionPlay());

        stored.edit().putBoolean(UserPreferences.PREF_SKIP_KEEPS_EPISODE, false)
                .putBoolean(UserPreferences.PREF_FAVORITE_KEEPS_EPISODE, false)
                .putBoolean(UserPreferences.PREF_AUTO_DELETE, true)
                .putBoolean("prefAutoDeleteLocal", true)
                .putBoolean(UserPreferences.PREF_DELETE_REMOVES_FROM_QUEUE, true)
                .putBoolean(UserPreferences.PREF_DOWNLOADS_BUTTON_ACTION, true).commit();

        assertFalse(UserPreferences.shouldSkipKeepEpisode());
        assertFalse(UserPreferences.shouldFavoriteKeepEpisode());
        assertTrue(UserPreferences.isAutoDelete());
        assertTrue(UserPreferences.isAutoDeleteLocal());
        assertTrue(UserPreferences.shouldDeleteRemoveFromQueue());
        assertTrue(UserPreferences.shouldDownloadsButtonActionPlay());
    }

    @Test
    public void smartMarkAsPlayedThresholdIsStoredAsText() {
        assertEquals(30, UserPreferences.getSmartMarkAsPlayedSecs());

        stored.edit().putString(UserPreferences.PREF_SMART_MARK_AS_PLAYED_SECS, "45").commit();

        assertEquals(45, UserPreferences.getSmartMarkAsPlayedSecs());
    }

    @Test
    public void playbackSpeedIsStoredAsTextAndReadBackAsFloat() {
        assertEquals(1.0f, UserPreferences.getPlaybackSpeed(), 0.0001f);

        UserPreferences.setPlaybackSpeed(1.75f);

        assertEquals("1.75", stored.getString("prefPlaybackSpeed", null));
        assertEquals(1.75f, UserPreferences.getPlaybackSpeed(), 0.0001f);
    }

    @Test
    public void corruptPlaybackSpeedIsRepairedToNormalSpeed() {
        stored.edit().putString("prefPlaybackSpeed", "fast").commit();

        assertEquals(1.0f, UserPreferences.getPlaybackSpeed(), 0.0001f);
        assertEquals("1.0", stored.getString("prefPlaybackSpeed", null));
    }

    @Test
    public void skipSilenceAndTimeRespectsSpeedAreFlags() {
        assertFalse(UserPreferences.isSkipSilence());
        assertFalse(UserPreferences.timeRespectsSpeed());

        UserPreferences.setSkipSilence(true);
        stored.edit().putBoolean("prefPlaybackTimeRespectsSpeed", true).commit();

        assertTrue(stored.getBoolean(UserPreferences.PREF_PLAYBACK_SKIP_SILENCE, false));
        assertTrue(UserPreferences.isSkipSilence());
        assertTrue(UserPreferences.timeRespectsSpeed());
    }

    @Test
    public void playbackSpeedArrayIsStoredAsJsonWithTwoDecimals() {
        assertEquals(Arrays.asList(1.0f, 1.25f, 1.5f), UserPreferences.getPlaybackSpeedArray());

        UserPreferences.setPlaybackSpeedArray(Arrays.asList(0.75f, 1.0f, 2.5f));

        assertEquals("[\"0.75\",\"1.00\",\"2.50\"]", stored.getString("prefPlaybackSpeedArray", null));
        assertEquals(Arrays.asList(0.75f, 1.0f, 2.5f), UserPreferences.getPlaybackSpeedArray());
    }

    @Test
    public void emptyPlaybackSpeedArrayIsKeptEmptyInsteadOfFallingBackToDefaults() {
        UserPreferences.setPlaybackSpeedArray(Collections.emptyList());

        assertTrue(UserPreferences.getPlaybackSpeedArray().isEmpty());
    }

    @Test
    public void unreadablePlaybackSpeedArrayFallsBackToTheDefaultSpeeds() {
        stored.edit().putString("prefPlaybackSpeedArray", "not json").commit();

        assertEquals(Arrays.asList(1.0f, 1.25f, 1.5f), UserPreferences.getPlaybackSpeedArray());
    }

    @Test
    public void focusLossPausesPlaybackUnlessDisabled() {
        assertTrue(UserPreferences.shouldPauseForFocusLoss());

        stored.edit().putBoolean(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, false).commit();

        assertFalse(UserPreferences.shouldPauseForFocusLoss());
    }

    @Test
    public void skipIntervalsAreStoredAsIntegers() {
        assertEquals(30, UserPreferences.getFastForwardSecs());
        assertEquals(10, UserPreferences.getRewindSecs());

        UserPreferences.setFastForwardSecs(45);
        UserPreferences.setRewindSecs(5);

        assertEquals(45, stored.getInt("prefFastForwardSecs", 0));
        assertEquals(5, stored.getInt("prefRewindSecs", 0));
        assertEquals(45, UserPreferences.getFastForwardSecs());
        assertEquals(5, UserPreferences.getRewindSecs());
    }

    @Test
    public void queueLockAndStreamingPreferenceRoundTrip() {
        assertFalse(UserPreferences.isQueueLocked());
        assertFalse(UserPreferences.isStreamOverDownload());

        UserPreferences.setQueueLocked(true);
        UserPreferences.setStreamOverDownload(true);

        assertTrue(stored.getBoolean("prefQueueLocked", false));
        assertTrue(UserPreferences.isQueueLocked());
        assertTrue(stored.getBoolean(UserPreferences.PREF_STREAM_OVER_DOWNLOAD, false));
        assertTrue(UserPreferences.isStreamOverDownload());
    }

    @Test
    public void enqueueLocationIsStoredByNameAndInvalidNamesFallBackToTheBack() {
        assertEquals(UserPreferences.EnqueueLocation.BACK, UserPreferences.getEnqueueLocation());

        for (UserPreferences.EnqueueLocation location : UserPreferences.EnqueueLocation.values()) {
            UserPreferences.setEnqueueLocation(location);
            assertEquals(location.name(), stored.getString(UserPreferences.PREF_ENQUEUE_LOCATION, null));
            assertEquals(location, UserPreferences.getEnqueueLocation());
        }

        stored.edit().putString(UserPreferences.PREF_ENQUEUE_LOCATION, "SOMEWHERE").commit();

        assertEquals(UserPreferences.EnqueueLocation.BACK, UserPreferences.getEnqueueLocation());
    }

    @Test
    public void downloadedEpisodesAreEnqueuedUnlessTurnedOff() {
        assertTrue(UserPreferences.enqueueDownloadedEpisodes());

        stored.edit().putBoolean("prefEnqueueDownloaded", false).commit();

        assertFalse(UserPreferences.enqueueDownloadedEpisodes());
    }
}
