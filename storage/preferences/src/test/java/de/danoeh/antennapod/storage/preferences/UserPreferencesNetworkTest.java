package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.download.ProxyConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.net.Proxy;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class UserPreferencesNetworkTest {
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        UserPreferences.init(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Test
    public void updateIntervalDefaultsToTwelveHoursInMinutes() {
        assertEquals(720, UserPreferences.getUpdateInterval());
        assertFalse(UserPreferences.isAutoUpdateDisabled());
    }

    @Test
    public void updateIntervalIsPersistedAndZeroDisablesAutoUpdate() {
        UserPreferences.setUpdateInterval(60);
        assertEquals(60, UserPreferences.getUpdateInterval());
        assertFalse(UserPreferences.isAutoUpdateDisabled());

        UserPreferences.setUpdateInterval(0);
        assertEquals(0, UserPreferences.getUpdateInterval());
        assertTrue(UserPreferences.isAutoUpdateDisabled());
    }

    @Test
    public void onlyImagesAreAllowedOnMobileDataByDefaultAndStoredSetReplacesThat() {
        assertTrue(UserPreferences.isAllowMobileImages());
        assertFalse(UserPreferences.isAllowMobileFeedRefresh());
        assertFalse(UserPreferences.isAllowMobileSync());
        assertFalse(UserPreferences.isAllowMobileEpisodeDownload());
        assertFalse(UserPreferences.isAllowMobileAutoDownload());
        assertFalse(UserPreferences.isAllowMobileStreaming());

        prefs.edit().putStringSet(UserPreferences.PREF_MOBILE_UPDATE,
                new HashSet<>(Arrays.asList("sync", "streaming"))).commit();

        assertFalse(UserPreferences.isAllowMobileImages());
        assertFalse(UserPreferences.isAllowMobileFeedRefresh());
        assertTrue(UserPreferences.isAllowMobileSync());
        assertFalse(UserPreferences.isAllowMobileEpisodeDownload());
        assertFalse(UserPreferences.isAllowMobileAutoDownload());
        assertTrue(UserPreferences.isAllowMobileStreaming());
    }

    @Test
    public void mobileDataPermissionsAreToggledIndependently() {
        UserPreferences.setAllowMobileFeedRefresh(true);
        UserPreferences.setAllowMobileSync(true);
        UserPreferences.setAllowMobileEpisodeDownload(true);
        UserPreferences.setAllowMobileAutoDownload(true);
        UserPreferences.setAllowMobileStreaming(true);

        assertTrue(UserPreferences.isAllowMobileFeedRefresh());
        assertTrue(UserPreferences.isAllowMobileSync());
        assertTrue(UserPreferences.isAllowMobileEpisodeDownload());
        assertTrue(UserPreferences.isAllowMobileAutoDownload());
        assertTrue(UserPreferences.isAllowMobileStreaming());
        assertTrue(UserPreferences.isAllowMobileImages());

        UserPreferences.setAllowMobileSync(false);
        UserPreferences.setAllowMobileImages(false);

        assertFalse(UserPreferences.isAllowMobileSync());
        assertFalse(UserPreferences.isAllowMobileImages());
        assertTrue(UserPreferences.isAllowMobileFeedRefresh());
        assertTrue(UserPreferences.isAllowMobileStreaming());
    }

    @Test
    public void episodeCacheSizeDefaultsToTwentyAndParsesUnlimitedMarker() {
        assertEquals(20, UserPreferences.getEpisodeCacheSize());

        prefs.edit().putString(UserPreferences.PREF_EPISODE_CACHE_SIZE,
                String.valueOf(UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED)).commit();

        assertEquals(UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED, UserPreferences.getEpisodeCacheSize());
    }

    @Test
    public void autoDownloadDefaultsAndStoredValues() {
        assertFalse(UserPreferences.isEnableAutodownloadGlobal());
        assertFalse(UserPreferences.isEnableAutodownloadQueue());
        assertTrue(UserPreferences.isEnableAutodownloadOnBattery());

        prefs.edit()
                .putBoolean(UserPreferences.PREF_AUTODL_GLOBAL, true)
                .putBoolean(UserPreferences.PREF_ENABLE_AUTODL_ON_BATTERY, false)
                .commit();

        assertTrue(UserPreferences.isEnableAutodownloadGlobal());
        assertFalse(UserPreferences.isEnableAutodownloadQueue());
        assertFalse(UserPreferences.isEnableAutodownloadOnBattery());

        prefs.edit().putBoolean(UserPreferences.PREF_AUTODL_QUEUE, true).commit();

        assertTrue(UserPreferences.isEnableAutodownloadQueue());
    }

    @Test
    public void episodeCleanupDefaultsToNullMarkerAndIsPersisted() {
        assertEquals(UserPreferences.EPISODE_CLEANUP_NULL, UserPreferences.getEpisodeCleanupValue());

        UserPreferences.setEpisodeCleanupValue(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE);
        assertEquals(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE, UserPreferences.getEpisodeCleanupValue());

        UserPreferences.setEpisodeCleanupValue(14);
        assertEquals(14, UserPreferences.getEpisodeCleanupValue());
    }

    @Test
    public void proxyDefaultsToDirectConnectionWithoutCredentials() {
        ProxyConfig config = UserPreferences.getProxyConfig();

        assertEquals(Proxy.Type.DIRECT, config.type);
        assertNull(config.host);
        assertEquals(0, config.port);
        assertNull(config.username);
        assertNull(config.password);
    }

    @Test
    public void proxyConfigRoundTripsAllFields() {
        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 3128, "user", "pass"));

        ProxyConfig config = UserPreferences.getProxyConfig();

        assertEquals(Proxy.Type.HTTP, config.type);
        assertEquals("proxy.example.com", config.host);
        assertEquals(3128, config.port);
        assertEquals("user", config.username);
        assertEquals("pass", config.password);
    }

    @Test
    public void proxyConfigDropsEmptyHostAndCredentials() {
        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.SOCKS, "proxy.example.com", 1080, "user", "pass"));

        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.SOCKS, "", 1080, "", null));

        ProxyConfig config = UserPreferences.getProxyConfig();
        assertEquals(Proxy.Type.SOCKS, config.type);
        assertNull(config.host);
        assertEquals(1080, config.port);
        assertNull(config.username);
        assertNull(config.password);
    }

    @Test
    public void proxyConfigDropsPortOutsideValidRange() {
        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 3128, null, null));

        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 65536, null, null));
        assertEquals(0, UserPreferences.getProxyConfig().port);

        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 3128, null, null));
        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 0, null, null));
        assertEquals(0, UserPreferences.getProxyConfig().port);

        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 65535, null, null));
        assertEquals(65535, UserPreferences.getProxyConfig().port);
    }

    @Test
    public void dataFolderFallsBackToAppStorageWhenUserFolderIsNotUsable() {
        UserPreferences.setDataFolder("/nonexistent-root/antennapod");

        File folder = UserPreferences.getDataFolder("podcasts");

        assertNotNull(folder);
        assertEquals("podcasts", folder.getName());
        assertTrue(folder.canWrite());
    }

    @Test
    public void dataFolderWithoutTypeIsTheRootOfTheAppStorage() {
        File root = UserPreferences.getDataFolder(null);
        File typed = UserPreferences.getDataFolder("media");

        assertNotNull(root);
        assertNotNull(typed);
        assertEquals(root, typed.getParentFile());
    }
}
