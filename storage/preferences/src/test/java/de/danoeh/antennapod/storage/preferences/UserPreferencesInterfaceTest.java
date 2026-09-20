package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedOrder;
import de.danoeh.antennapod.model.feed.SubscriptionsFilter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class UserPreferencesInterfaceTest {
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        UserPreferences.init(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Test
    public void themeDefaultsToSystem() {
        assertEquals(UserPreferences.ThemePreference.SYSTEM, UserPreferences.getTheme());
    }

    @Test
    public void lightAndDarkThemesAreStoredAndRestored() {
        UserPreferences.setTheme(UserPreferences.ThemePreference.LIGHT);
        assertEquals(UserPreferences.ThemePreference.LIGHT, UserPreferences.getTheme());

        UserPreferences.setTheme(UserPreferences.ThemePreference.DARK);
        assertEquals(UserPreferences.ThemePreference.DARK, UserPreferences.getTheme());

        UserPreferences.setTheme(UserPreferences.ThemePreference.SYSTEM);
        assertEquals(UserPreferences.ThemePreference.SYSTEM, UserPreferences.getTheme());
    }

    @Test
    public void unknownStoredThemeValueIsTreatedAsSystem() {
        prefs.edit().putString(UserPreferences.PREF_THEME, "garbage").commit();

        assertEquals(UserPreferences.ThemePreference.SYSTEM, UserPreferences.getTheme());
    }

    @Test
    public void blackThemeFlagDefaultsToFalseAndFollowsPreference() {
        assertFalse(UserPreferences.getIsBlackTheme());

        prefs.edit().putBoolean(UserPreferences.PREF_THEME_BLACK, true).commit();

        assertTrue(UserPreferences.getIsBlackTheme());
    }

    @Test
    @Config(sdk = 31)
    public void tintedColorsFollowPreferenceOnAndroid12() {
        assertFalse(UserPreferences.getIsThemeColorTinted());

        prefs.edit().putBoolean(UserPreferences.PREF_TINTED_COLORS, true).commit();

        assertTrue(UserPreferences.getIsThemeColorTinted());
    }

    @Test
    @Config(sdk = 30)
    public void tintedColorsAreDisabledBeforeAndroid12EvenWhenPreferenceIsSet() {
        prefs.edit().putBoolean(UserPreferences.PREF_TINTED_COLORS, true).commit();

        assertFalse(UserPreferences.getIsThemeColorTinted());
    }

    @Test
    public void parentalControlPasswordLifecycle() {
        assertFalse(UserPreferences.isParentalControlPasswordSet());
        assertFalse(UserPreferences.verifyParentalControlPassword("1234"));

        UserPreferences.setParentalControlPassword("1234");

        assertTrue(UserPreferences.isParentalControlPasswordSet());
        assertTrue(UserPreferences.verifyParentalControlPassword("1234"));
        assertFalse(UserPreferences.verifyParentalControlPassword("4321"));
        assertFalse(UserPreferences.verifyParentalControlPassword(null));

        UserPreferences.clearParentalControlPassword();

        assertFalse(UserPreferences.isParentalControlPasswordSet());
        assertFalse(UserPreferences.verifyParentalControlPassword("1234"));
    }

    @Test
    public void parentalControlRequiresSubscribeByDefault() {
        assertTrue(UserPreferences.isParentalControlRequireSubscribeSet());

        prefs.edit().putBoolean(UserPreferences.PREF_PARENTAL_CONTROL_REQUIRE_SUBSCRIBE, false).commit();

        assertFalse(UserPreferences.isParentalControlRequireSubscribeSet());
    }

    @Test
    public void allDrawerItemsAreVisibleInDefaultOrderInitially() {
        assertTrue(UserPreferences.getHiddenDrawerItems().isEmpty());

        List<String> visible = UserPreferences.getVisibleDrawerItemOrder();

        assertEquals("HomeFragment", visible.get(0));
        assertEquals("SubscriptionList", visible.get(visible.size() - 1));
        assertTrue(visible.containsAll(Arrays.asList("QueueFragment", "DownloadsFragment", "AddFeedFragment")));
    }

    @Test
    public void hiddenDrawerItemsAreRemovedFromVisibleOrder() {
        UserPreferences.setDrawerItemOrder(Arrays.asList("QueueFragment", "StatisticsFragment"),
                Collections.emptyList());

        assertEquals(Arrays.asList("QueueFragment", "StatisticsFragment"), UserPreferences.getHiddenDrawerItems());
        List<String> visible = UserPreferences.getVisibleDrawerItemOrder();
        assertFalse(visible.contains("QueueFragment"));
        assertFalse(visible.contains("StatisticsFragment"));
        assertTrue(visible.contains("HomeFragment"));
    }

    @Test
    public void explicitlyOrderedDrawerItemsComeFirstFollowedByRemainingItemsInDefaultOrder() {
        UserPreferences.setDrawerItemOrder(Collections.emptyList(),
                Arrays.asList("DownloadsFragment", "HomeFragment"));

        List<String> visible = UserPreferences.getVisibleDrawerItemOrder();

        assertEquals("DownloadsFragment", visible.get(0));
        assertEquals("HomeFragment", visible.get(1));
        assertEquals("QueueFragment", visible.get(2));
        assertEquals("NewEpisodesFragment", visible.get(3));
    }

    @Test
    public void fullNotificationButtonsDefaultToSkipAndPlaybackSpeed() {
        assertEquals(Arrays.asList(UserPreferences.NOTIFICATION_BUTTON_SKIP,
                UserPreferences.NOTIFICATION_BUTTON_PLAYBACK_SPEED), UserPreferences.getFullNotificationButtons());
        assertTrue(UserPreferences.showSkipOnFullNotification());
        assertTrue(UserPreferences.showPlaybackSpeedOnFullNotification());
        assertFalse(UserPreferences.showNextChapterOnFullNotification());
        assertFalse(UserPreferences.showSleepTimerOnFullNotification());
    }

    @Test
    public void fullNotificationButtonsAreStoredAndDriveVisibilityFlags() {
        UserPreferences.setFullNotificationButtons(Arrays.asList(
                UserPreferences.NOTIFICATION_BUTTON_NEXT_CHAPTER, UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER));

        assertEquals(Arrays.asList(UserPreferences.NOTIFICATION_BUTTON_NEXT_CHAPTER,
                UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER), UserPreferences.getFullNotificationButtons());
        assertFalse(UserPreferences.showSkipOnFullNotification());
        assertFalse(UserPreferences.showPlaybackSpeedOnFullNotification());
        assertTrue(UserPreferences.showNextChapterOnFullNotification());
        assertTrue(UserPreferences.showSleepTimerOnFullNotification());
    }

    @Test
    public void feedOrderDefaultsToCounterAndIsPersisted() {
        assertEquals(FeedOrder.COUNTER, UserPreferences.getFeedOrder());

        UserPreferences.setFeedOrder(FeedOrder.ALPHABETICAL);

        assertEquals(FeedOrder.ALPHABETICAL, UserPreferences.getFeedOrder());
    }

    @Test
    public void feedCounterDefaultsToShowNewAndIsPersisted() {
        assertEquals(FeedCounter.SHOW_NEW, UserPreferences.getFeedCounterSetting());

        UserPreferences.setFeedCounterSetting(FeedCounter.SHOW_DOWNLOADED_UNPLAYED);

        assertEquals(FeedCounter.SHOW_DOWNLOADED_UNPLAYED, UserPreferences.getFeedCounterSetting());
    }

    @Test
    public void episodeCoverIsUsedByDefault() {
        assertTrue(UserPreferences.getUseEpisodeCoverSetting());

        prefs.edit().putBoolean(UserPreferences.PREF_USE_EPISODE_COVER, false).commit();

        assertFalse(UserPreferences.getUseEpisodeCoverSetting());
    }

    @Test
    public void remainingTimeIsHiddenByDefaultAndTogglable() {
        assertFalse(UserPreferences.shouldShowRemainingTime());

        UserPreferences.setShowRemainTimeSetting(true);

        assertTrue(UserPreferences.shouldShowRemainingTime());
    }

    @Test
    public void notificationPriorityIsMaxOnlyWhenExpandedNotificationEnabled() {
        assertEquals(NotificationCompat.PRIORITY_DEFAULT, UserPreferences.getNotifyPriority());

        prefs.edit().putBoolean(UserPreferences.PREF_EXPANDED_NOTIFICATION, true).commit();

        assertEquals(NotificationCompat.PRIORITY_MAX, UserPreferences.getNotifyPriority());
    }

    @Test
    public void persistentNotificationAndDownloadReportDefaultToEnabled() {
        assertTrue(UserPreferences.isPersistNotify());
        assertTrue(UserPreferences.getShowDownloadReportRaw());

        prefs.edit().putBoolean(UserPreferences.PREF_PERSISTENT_NOTIFICATION, false).commit();

        assertFalse(UserPreferences.isPersistNotify());
    }

    @Test
    public void automaticExportFolderCanBeSetAndCleared() {
        assertNull(UserPreferences.getAutomaticExportFolder());

        UserPreferences.setAutomaticExportFolder("content://tree/backup");
        assertEquals("content://tree/backup", UserPreferences.getAutomaticExportFolder());

        UserPreferences.setAutomaticExportFolder(null);
        assertNull(UserPreferences.getAutomaticExportFolder());
    }

    @Test
    public void defaultPageIsHomeAndCanBeChanged() {
        assertEquals("HomeFragment", UserPreferences.getDefaultPage());

        UserPreferences.setDefaultPage(UserPreferences.DEFAULT_PAGE_REMEMBER);

        assertEquals(UserPreferences.DEFAULT_PAGE_REMEMBER, UserPreferences.getDefaultPage());
    }

    @Test
    public void bottomNavigationIsEnabledByDefaultAndTogglable() {
        assertTrue(UserPreferences.isBottomNavigationEnabled());

        UserPreferences.setBottomNavigationEnabled(false);

        assertFalse(UserPreferences.isBottomNavigationEnabled());
    }

    @Test
    public void backButtonDoesNotOpenDrawerByDefault() {
        assertFalse(UserPreferences.backButtonOpensDrawer());

        prefs.edit().putBoolean(UserPreferences.PREF_BACK_OPENS_DRAWER, true).commit();

        assertTrue(UserPreferences.backButtonOpensDrawer());
    }

    @Test
    public void subscriptionsFilterIsDisabledByDefaultAndRoundTrips() {
        assertFalse(UserPreferences.getSubscriptionsFilter().isEnabled());

        UserPreferences.setSubscriptionsFilter(new SubscriptionsFilter(new String[] {
                SubscriptionsFilter.COUNTER_GREATER_ZERO, SubscriptionsFilter.ENABLED_AUTO_DOWNLOAD}));

        SubscriptionsFilter restored = UserPreferences.getSubscriptionsFilter();
        assertTrue(restored.isEnabled());
        assertTrue(restored.showIfCounterGreaterZero);
        assertTrue(restored.showAutoDownloadEnabled);
        assertFalse(restored.showAutoDownloadDisabled);
    }

    @Test
    public void subscriptionTitleIsHiddenByDefaultAndTogglable() {
        assertFalse(UserPreferences.shouldShowSubscriptionTitle());

        UserPreferences.setShouldShowSubscriptionTitle(true);

        assertTrue(UserPreferences.shouldShowSubscriptionTitle());
    }

    @Test
    public void allEpisodesFilterDefaultsToEmptyAndIsPersisted() {
        assertEquals("", UserPreferences.getPrefFilterAllEpisodes());

        UserPreferences.setPrefFilterAllEpisodes("downloaded,unplayed");

        assertEquals("downloaded,unplayed", UserPreferences.getPrefFilterAllEpisodes());
    }
}
