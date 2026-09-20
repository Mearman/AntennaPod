package de.danoeh.antennapod.storage.preferences;

import de.danoeh.antennapod.model.download.ProxyConfig;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.net.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class UserPreferencesNetworkStoredValuesTest extends StoredPreferencesTestBase {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void updateIntervalIsStoredInMinutesAndZeroDisablesAutomaticUpdates() {
        assertEquals(720, UserPreferences.getUpdateInterval());
        assertFalse(UserPreferences.isAutoUpdateDisabled());

        UserPreferences.setUpdateInterval(60);
        assertEquals("60", stored.getString(UserPreferences.PREF_UPDATE_INTERVAL_MINUTES, null));
        assertEquals(60, UserPreferences.getUpdateInterval());

        UserPreferences.setUpdateInterval(0);
        assertTrue(UserPreferences.isAutoUpdateDisabled());
    }

    @Test
    public void mobileDataIsOnlyAllowedForImagesUntilChanged() {
        assertTrue(UserPreferences.isAllowMobileImages());
        assertFalse(UserPreferences.isAllowMobileFeedRefresh());
        assertFalse(UserPreferences.isAllowMobileSync());
        assertFalse(UserPreferences.isAllowMobileEpisodeDownload());
        assertFalse(UserPreferences.isAllowMobileAutoDownload());
        assertFalse(UserPreferences.isAllowMobileStreaming());
    }

    @Test
    public void eachMobileDataPermissionIsStoredIndependently() {
        UserPreferences.setAllowMobileFeedRefresh(true);
        UserPreferences.setAllowMobileSync(true);
        UserPreferences.setAllowMobileEpisodeDownload(true);
        UserPreferences.setAllowMobileAutoDownload(true);
        UserPreferences.setAllowMobileStreaming(true);

        Set<String> expected = new HashSet<>();
        expected.add("images");
        expected.add("feed_refresh");
        expected.add("sync");
        expected.add("episode_download");
        expected.add("auto_download");
        expected.add("streaming");
        assertEquals(expected, stored.getStringSet(UserPreferences.PREF_MOBILE_UPDATE, null));
        assertTrue(UserPreferences.isAllowMobileFeedRefresh());
        assertTrue(UserPreferences.isAllowMobileSync());
        assertTrue(UserPreferences.isAllowMobileEpisodeDownload());
        assertTrue(UserPreferences.isAllowMobileAutoDownload());
        assertTrue(UserPreferences.isAllowMobileStreaming());

        UserPreferences.setAllowMobileStreaming(false);
        UserPreferences.setAllowMobileImages(false);

        assertFalse(UserPreferences.isAllowMobileStreaming());
        assertFalse(UserPreferences.isAllowMobileImages());
        assertTrue(UserPreferences.isAllowMobileSync());
    }

    @Test
    public void episodeCacheAndCleanupSettingsAreStoredAsText() {
        assertEquals(20, UserPreferences.getEpisodeCacheSize());
        assertEquals(UserPreferences.EPISODE_CLEANUP_NULL, UserPreferences.getEpisodeCleanupValue());

        stored.edit().putString(UserPreferences.PREF_EPISODE_CACHE_SIZE,
                String.valueOf(UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED)).commit();
        UserPreferences.setEpisodeCleanupValue(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE);

        assertEquals(UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED, UserPreferences.getEpisodeCacheSize());
        assertEquals("-3", stored.getString(UserPreferences.PREF_EPISODE_CLEANUP, null));
        assertEquals(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE, UserPreferences.getEpisodeCleanupValue());
    }

    @Test
    public void autoDownloadFlagsDefaultToOffExceptOnBattery() {
        assertFalse(UserPreferences.isEnableAutodownloadGlobal());
        assertFalse(UserPreferences.isEnableAutodownloadQueue());
        assertTrue(UserPreferences.isEnableAutodownloadOnBattery());

        stored.edit().putBoolean(UserPreferences.PREF_AUTODL_GLOBAL, true)
                .putBoolean(UserPreferences.PREF_AUTODL_QUEUE, true)
                .putBoolean(UserPreferences.PREF_ENABLE_AUTODL_ON_BATTERY, false).commit();

        assertTrue(UserPreferences.isEnableAutodownloadGlobal());
        assertTrue(UserPreferences.isEnableAutodownloadQueue());
        assertFalse(UserPreferences.isEnableAutodownloadOnBattery());
    }

    @Test
    public void proxyIsDirectByDefault() {
        ProxyConfig config = UserPreferences.getProxyConfig();

        assertEquals(Proxy.Type.DIRECT, config.type);
        assertNull(config.host);
        assertEquals(0, config.port);
        assertNull(config.username);
        assertNull(config.password);
    }

    @Test
    public void proxyConfigurationRoundTrips() {
        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.SOCKS, "proxy.example.com", 1080, "user", "pass"));

        ProxyConfig config = UserPreferences.getProxyConfig();

        assertEquals(Proxy.Type.SOCKS, config.type);
        assertEquals("proxy.example.com", config.host);
        assertEquals(1080, config.port);
        assertEquals("user", config.username);
        assertEquals("pass", config.password);
    }

    @Test
    public void emptyOrInvalidProxyFieldsAreRemovedFromStorage() {
        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 8080, "user", "pass"));

        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "", 70000, "", null));

        assertFalse(stored.contains("prefProxyHost"));
        assertFalse(stored.contains("prefProxyPort"));
        assertFalse(stored.contains("prefProxyUser"));
        assertFalse(stored.contains("prefProxyPassword"));
        assertEquals(Proxy.Type.HTTP, UserPreferences.getProxyConfig().type);

        UserPreferences.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 0, null, null));
        assertFalse(stored.contains("prefProxyPort"));
    }

    @Test
    public void dataFolderUsesTheChosenFolderAndCreatesTypeSubfolders() throws Exception {
        File chosen = temporaryFolder.newFolder("chosen-data-folder");
        UserPreferences.setDataFolder(chosen.getAbsolutePath());

        File root = UserPreferences.getDataFolder(null);
        File media = UserPreferences.getDataFolder("media");

        assertEquals(chosen, root);
        assertEquals(new File(chosen, "media"), media);
        assertTrue(media.isDirectory());
    }

    @Test
    public void dataFolderFallsBackToTheDefaultLocationWhenTheChosenFolderIsUnusable() throws Exception {
        File regularFile = temporaryFolder.newFile("not-a-folder");
        UserPreferences.setDataFolder(regularFile.getAbsolutePath());

        File folder = UserPreferences.getDataFolder("media");

        assertTrue(folder.getAbsolutePath(), folder.canWrite());
        assertFalse(folder.getAbsolutePath().startsWith(regularFile.getAbsolutePath()));
    }

    @Test
    public void dataFolderUsesTheDefaultLocationWhenNoneWasChosen() {
        File folder = UserPreferences.getDataFolder(null);

        assertEquals(context.getExternalFilesDir(null), folder);
    }

    @Test
    public void initialisationCreatesTheNoMediaMarkerInTheExternalFilesFolder() {
        assertTrue(new File(context.getExternalFilesDir(null), ".nomedia").exists());
    }
}
