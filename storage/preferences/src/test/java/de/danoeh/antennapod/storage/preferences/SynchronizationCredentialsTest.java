package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SynchronizationCredentialsTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        UserPreferences.init(context);
        SynchronizationCredentials.init(context);
    }

    @Test
    public void freshCredentialsAreEmpty() {
        assertNull(SynchronizationCredentials.getUsername());
        assertNull(SynchronizationCredentials.getPassword());
        assertNull(SynchronizationCredentials.getDeviceId());
        assertNull(SynchronizationCredentials.getHosturl());
    }

    @Test
    public void credentialsArePersisted() {
        SynchronizationCredentials.setUsername("alice");
        SynchronizationCredentials.setPassword("secret");
        SynchronizationCredentials.setDeviceId("device-1");
        SynchronizationCredentials.setHosturl("https://sync.example.com");

        assertEquals("alice", SynchronizationCredentials.getUsername());
        assertEquals("secret", SynchronizationCredentials.getPassword());
        assertEquals("device-1", SynchronizationCredentials.getDeviceId());
        assertEquals("https://sync.example.com", SynchronizationCredentials.getHosturl());
    }

    @Test
    public void clearRemovesLoginDataButKeepsHostUrl() {
        SynchronizationCredentials.setUsername("alice");
        SynchronizationCredentials.setPassword("secret");
        SynchronizationCredentials.setDeviceId("device-1");
        SynchronizationCredentials.setHosturl("https://sync.example.com");

        SynchronizationCredentials.clear();

        assertNull(SynchronizationCredentials.getUsername());
        assertNull(SynchronizationCredentials.getPassword());
        assertNull(SynchronizationCredentials.getDeviceId());
        assertEquals("https://sync.example.com", SynchronizationCredentials.getHosturl());
    }

    @Test
    public void clearReenablesGpodnetNotifications() {
        SharedPreferences userPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        userPrefs.edit().putBoolean("pref_gpodnet_notifications", false).commit();

        SynchronizationCredentials.clear();

        assertTrue(UserPreferences.getGpodnetNotificationsEnabledRaw());
    }
}
