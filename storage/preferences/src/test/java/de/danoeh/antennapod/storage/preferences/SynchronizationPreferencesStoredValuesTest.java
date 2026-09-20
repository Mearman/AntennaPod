package de.danoeh.antennapod.storage.preferences;

import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class SynchronizationPreferencesStoredValuesTest extends StoredPreferencesTestBase {

    @Test
    public void credentialsAreEmptyUntilSet() {
        assertNull(SynchronizationCredentials.getUsername());
        assertNull(SynchronizationCredentials.getPassword());
        assertNull(SynchronizationCredentials.getDeviceId());
        assertNull(SynchronizationCredentials.getHosturl());
    }

    @Test
    public void credentialsAreStoredInTheirOwnPreferenceFile() {
        SynchronizationCredentials.setUsername("listener");
        SynchronizationCredentials.setPassword("hunter2");
        SynchronizationCredentials.setDeviceId("device-1");
        SynchronizationCredentials.setHosturl("https://sync.example.com");

        assertEquals("listener", SynchronizationCredentials.getUsername());
        assertEquals("hunter2", SynchronizationCredentials.getPassword());
        assertEquals("device-1", SynchronizationCredentials.getDeviceId());
        assertEquals("https://sync.example.com", SynchronizationCredentials.getHosturl());
        assertEquals("listener", context.getSharedPreferences("gpodder.net", 0)
                .getString("de.danoeh.antennapod.preferences.gpoddernet.username", null));
        assertFalse(stored.contains("de.danoeh.antennapod.preferences.gpoddernet.username"));
    }

    @Test
    public void clearingCredentialsKeepsTheHostAndReEnablesGpodnetNotifications() {
        SynchronizationCredentials.setUsername("listener");
        SynchronizationCredentials.setPassword("hunter2");
        SynchronizationCredentials.setDeviceId("device-1");
        SynchronizationCredentials.setHosturl("https://sync.example.com");
        stored.edit().putBoolean("pref_gpodnet_notifications", false).commit();

        SynchronizationCredentials.clear();

        assertNull(SynchronizationCredentials.getUsername());
        assertNull(SynchronizationCredentials.getPassword());
        assertNull(SynchronizationCredentials.getDeviceId());
        assertEquals("https://sync.example.com", SynchronizationCredentials.getHosturl());
        assertTrue(UserPreferences.getGpodnetNotificationsEnabledRaw());
    }

    @Test
    public void noSyncProviderIsConnectedByDefault() {
        assertFalse(SynchronizationSettings.isProviderConnected());
        assertNull(SynchronizationSettings.getSelectedSyncProviderKey());
        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
        assertEquals(0, SynchronizationSettings.getLastSyncAttempt());
        assertEquals(0, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void selectingAProviderMarksItAsConnected() {
        SynchronizationSettings.setSelectedSyncProvider("gpoddernet");

        assertTrue(SynchronizationSettings.isProviderConnected());
        assertEquals("gpoddernet", SynchronizationSettings.getSelectedSyncProviderKey());

        SynchronizationSettings.setSelectedSyncProvider(null);

        assertFalse(SynchronizationSettings.isProviderConnected());
    }

    @Test
    public void syncAttemptRecordsTheCurrentTimeAndItsOutcome() {
        long before = System.currentTimeMillis();

        SynchronizationSettings.updateLastSynchronizationAttempt();
        SynchronizationSettings.setLastSynchronizationAttemptSuccess(true);

        long recorded = SynchronizationSettings.getLastSyncAttempt();
        assertTrue(recorded >= before && recorded <= System.currentTimeMillis());
        assertTrue(SynchronizationSettings.isLastSyncSuccessful());
    }

    @Test
    public void synchronizationTimestampsRoundTripAndCanBeReset() {
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(1000);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(2000);
        SynchronizationSettings.updateLastSynchronizationAttempt();

        assertEquals(1000, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(2000, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());

        SynchronizationSettings.resetTimestamps();

        assertEquals(0, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastSyncAttempt());
    }
}
