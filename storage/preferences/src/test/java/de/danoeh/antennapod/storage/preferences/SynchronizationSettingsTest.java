package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SynchronizationSettingsTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        SynchronizationSettings.init(context);
    }

    @Test
    public void freshSettingsHaveNoProviderAndZeroTimestamps() {
        assertFalse(SynchronizationSettings.isProviderConnected());
        assertNull(SynchronizationSettings.getSelectedSyncProviderKey());
        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
        assertEquals(0, SynchronizationSettings.getLastSyncAttempt());
        assertEquals(0, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void selectingProviderMarksItConnected() {
        SynchronizationSettings.setSelectedSyncProvider("gpodder.net");

        assertTrue(SynchronizationSettings.isProviderConnected());
        assertEquals("gpodder.net", SynchronizationSettings.getSelectedSyncProviderKey());
    }

    @Test
    public void clearingProviderDisconnects() {
        SynchronizationSettings.setSelectedSyncProvider("gpodder.net");
        SynchronizationSettings.setSelectedSyncProvider(null);

        assertFalse(SynchronizationSettings.isProviderConnected());
    }

    @Test
    public void lastSyncSuccessFlagIsPersisted() {
        SynchronizationSettings.setLastSynchronizationAttemptSuccess(true);
        assertTrue(SynchronizationSettings.isLastSyncSuccessful());

        SynchronizationSettings.setLastSynchronizationAttemptSuccess(false);
        assertFalse(SynchronizationSettings.isLastSyncSuccessful());
    }

    @Test
    public void updateLastSynchronizationAttemptStoresCurrentTime() {
        long before = System.currentTimeMillis();
        SynchronizationSettings.updateLastSynchronizationAttempt();
        long after = System.currentTimeMillis();

        long stored = SynchronizationSettings.getLastSyncAttempt();
        assertTrue(stored >= before && stored <= after);
    }

    @Test
    public void subscriptionAndEpisodeActionTimestampsAreIndependent() {
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(1000L);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(2000L);

        assertEquals(1000L, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(2000L, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
    }

    @Test
    public void resetTimestampsZeroesAllTimestampsButKeepsProvider() {
        SynchronizationSettings.setSelectedSyncProvider("gpodder.net");
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(1000L);
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(2000L);
        SynchronizationSettings.updateLastSynchronizationAttempt();

        SynchronizationSettings.resetTimestamps();

        assertEquals(0, SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp());
        assertEquals(0, SynchronizationSettings.getLastSyncAttempt());
        assertEquals("gpodder.net", SynchronizationSettings.getSelectedSyncProviderKey());
    }
}
