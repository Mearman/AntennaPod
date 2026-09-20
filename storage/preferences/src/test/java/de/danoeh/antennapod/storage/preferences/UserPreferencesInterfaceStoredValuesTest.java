package de.danoeh.antennapod.storage.preferences;

import androidx.core.app.NotificationCompat;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedOrder;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class UserPreferencesInterfaceStoredValuesTest extends StoredPreferencesTestBase {

    @Test
    public void themeIsStoredAsTheCodeUsedByTheSettingsScreen() {
        UserPreferences.setTheme(UserPreferences.ThemePreference.LIGHT);
        assertEquals("0", stored.getString(UserPreferences.PREF_THEME, null));
        assertEquals(UserPreferences.ThemePreference.LIGHT, UserPreferences.getTheme());

        UserPreferences.setTheme(UserPreferences.ThemePreference.DARK);
        assertEquals("1", stored.getString(UserPreferences.PREF_THEME, null));
        assertEquals(UserPreferences.ThemePreference.DARK, UserPreferences.getTheme());

        UserPreferences.setTheme(UserPreferences.ThemePreference.SYSTEM);
        assertEquals("system", stored.getString(UserPreferences.PREF_THEME, null));
        assertEquals(UserPreferences.ThemePreference.SYSTEM, UserPreferences.getTheme());
    }

    @Test
    public void themeFollowsTheSystemWhenNothingIsStoredOrTheValueIsUnknown() {
        assertEquals(UserPreferences.ThemePreference.SYSTEM, UserPreferences.getTheme());

        stored.edit().putString(UserPreferences.PREF_THEME, "unexpected").commit();

        assertEquals(UserPreferences.ThemePreference.SYSTEM, UserPreferences.getTheme());
    }

    @Test
    public void blackThemeReadsTheFlagWrittenBySettings() {
        assertFalse(UserPreferences.getIsBlackTheme());

        stored.edit().putBoolean(UserPreferences.PREF_THEME_BLACK, true).commit();

        assertTrue(UserPreferences.getIsBlackTheme());
    }

    @Test
    public void tintedColorsReadTheFlagWrittenBySettings() {
        assertFalse(UserPreferences.getIsThemeColorTinted());

        stored.edit().putBoolean(UserPreferences.PREF_TINTED_COLORS, true).commit();

        assertTrue(UserPreferences.getIsThemeColorTinted());
    }

    @Test
    public void parentalControlPasswordIsVerifiedAgainstTheStoredValueUntilItIsCleared() {
        assertFalse(UserPreferences.isParentalControlPasswordSet());
        assertFalse(UserPreferences.verifyParentalControlPassword("secret"));

        UserPreferences.setParentalControlPassword("secret");

        assertTrue(UserPreferences.isParentalControlPasswordSet());
        assertTrue(UserPreferences.verifyParentalControlPassword("secret"));
        assertFalse(UserPreferences.verifyParentalControlPassword("Secret"));
        assertFalse(UserPreferences.verifyParentalControlPassword(null));

        UserPreferences.clearParentalControlPassword();

        assertFalse(UserPreferences.isParentalControlPasswordSet());
        assertFalse(UserPreferences.verifyParentalControlPassword("secret"));
    }

    @Test
    public void parentalControlRequiresSubscriptionUnlessDisabled() {
        assertTrue(UserPreferences.isParentalControlRequireSubscribeSet());

        stored.edit().putBoolean(UserPreferences.PREF_PARENTAL_CONTROL_REQUIRE_SUBSCRIBE, false).commit();

        assertFalse(UserPreferences.isParentalControlRequireSubscribeSet());
    }

    @Test
    public void drawerItemsAreOrderedByTheStoredOrderAndHiddenOnesAreDropped() {
        String[] allTags = context.getResources().getStringArray(R.array.nav_drawer_section_tags);
        assertEquals(Arrays.asList(allTags), UserPreferences.getVisibleDrawerItemOrder());
        assertTrue(UserPreferences.getHiddenDrawerItems().isEmpty());

        UserPreferences.setDrawerItemOrder(Arrays.asList("QueueFragment", "HomeFragment"),
                Arrays.asList("DownloadsFragment", "EpisodesFragment"));

        assertEquals(Arrays.asList("QueueFragment", "HomeFragment"), UserPreferences.getHiddenDrawerItems());
        List<String> visible = UserPreferences.getVisibleDrawerItemOrder();
        assertEquals(allTags.length - 2, visible.size());
        assertEquals("DownloadsFragment", visible.get(0));
        assertEquals("EpisodesFragment", visible.get(1));
        assertFalse(visible.contains("QueueFragment"));
        assertFalse(visible.contains("HomeFragment"));
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
    public void fullNotificationButtonsFollowTheStoredList() {
        UserPreferences.setFullNotificationButtons(Arrays.asList(UserPreferences.NOTIFICATION_BUTTON_NEXT_CHAPTER,
                UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER));

        assertEquals("3,5", stored.getString(UserPreferences.PREF_FULL_NOTIFICATION_BUTTONS, null));
        assertFalse(UserPreferences.showSkipOnFullNotification());
        assertFalse(UserPreferences.showPlaybackSpeedOnFullNotification());
        assertTrue(UserPreferences.showNextChapterOnFullNotification());
        assertTrue(UserPreferences.showSleepTimerOnFullNotification());
    }

    @Test
    public void emptyFullNotificationButtonListShowsNoButtons() {
        UserPreferences.setFullNotificationButtons(Collections.emptyList());

        assertTrue(UserPreferences.getFullNotificationButtons().isEmpty());
        assertFalse(UserPreferences.showSkipOnFullNotification());
    }

    @Test
    public void feedOrderAndCounterAreStoredAsTheirIds() {
        assertEquals(FeedOrder.COUNTER, UserPreferences.getFeedOrder());
        assertEquals(FeedCounter.SHOW_NEW, UserPreferences.getFeedCounterSetting());

        UserPreferences.setFeedOrder(FeedOrder.ALPHABETICAL);
        UserPreferences.setFeedCounterSetting(FeedCounter.SHOW_DOWNLOADED);

        assertEquals("" + FeedOrder.ALPHABETICAL.id, stored.getString(UserPreferences.PREF_DRAWER_FEED_ORDER, null));
        assertEquals("" + FeedCounter.SHOW_DOWNLOADED.id,
                stored.getString(UserPreferences.PREF_DRAWER_FEED_COUNTER, null));
        assertEquals(FeedOrder.ALPHABETICAL, UserPreferences.getFeedOrder());
        assertEquals(FeedCounter.SHOW_DOWNLOADED, UserPreferences.getFeedCounterSetting());
    }

    @Test
    public void episodeCoverIsUsedByDefaultUntilTheStoredFlagTurnsItOff() {
        assertTrue(UserPreferences.getUseEpisodeCoverSetting());

        stored.edit().putBoolean(UserPreferences.PREF_USE_EPISODE_COVER, false).commit();

        assertFalse(UserPreferences.getUseEpisodeCoverSetting());
    }

    @Test
    public void remainingTimeIsOptInAndStoredUnderTheTimeLeftKey() {
        assertFalse(UserPreferences.shouldShowRemainingTime());

        UserPreferences.setShowRemainTimeSetting(true);

        assertTrue(UserPreferences.shouldShowRemainingTime());
        assertTrue(stored.getBoolean(UserPreferences.PREF_SHOW_TIME_LEFT, false));
    }

    @Test
    public void automaticExportFolderIsNullUntilChosenAndCanBeRemoved() {
        assertNull(UserPreferences.getAutomaticExportFolder());

        UserPreferences.setAutomaticExportFolder("content://backups/tree/1");
        assertEquals("content://backups/tree/1", UserPreferences.getAutomaticExportFolder());

        UserPreferences.setAutomaticExportFolder(null);
        assertNull(UserPreferences.getAutomaticExportFolder());
    }

    @Test
    public void expandedNotificationRaisesTheNotificationPriority() {
        assertEquals(NotificationCompat.PRIORITY_DEFAULT, UserPreferences.getNotifyPriority());

        stored.edit().putBoolean(UserPreferences.PREF_EXPANDED_NOTIFICATION, true).commit();

        assertEquals(NotificationCompat.PRIORITY_MAX, UserPreferences.getNotifyPriority());
    }

    @Test
    public void persistentNotificationIsOnUntilTheStoredFlagTurnsItOff() {
        assertTrue(UserPreferences.isPersistNotify());

        stored.edit().putBoolean(UserPreferences.PREF_PERSISTENT_NOTIFICATION, false).commit();

        assertFalse(UserPreferences.isPersistNotify());
    }

    @Test
    public void downloadReportIsShownByDefault() {
        assertTrue(UserPreferences.getShowDownloadReportRaw());
    }

    @Test
    public void defaultPageIsTheHomeScreenUntilARememberedPageIsChosen() {
        assertEquals("HomeFragment", UserPreferences.getDefaultPage());

        UserPreferences.setDefaultPage(UserPreferences.DEFAULT_PAGE_REMEMBER);

        assertEquals("remember", UserPreferences.getDefaultPage());
    }

    @Test
    public void bottomNavigationIsEnabledUntilItIsSwitchedOff() {
        assertTrue(UserPreferences.isBottomNavigationEnabled());

        UserPreferences.setBottomNavigationEnabled(false);

        assertFalse(UserPreferences.isBottomNavigationEnabled());
    }

    @Test
    public void backButtonOpensTheDrawerOnlyWhenTheStoredFlagIsSet() {
        assertFalse(UserPreferences.backButtonOpensDrawer());

        stored.edit().putBoolean(UserPreferences.PREF_BACK_OPENS_DRAWER, true).commit();

        assertTrue(UserPreferences.backButtonOpensDrawer());
    }

    @Test
    public void gpodnetNotificationsAreAlwaysEnabledOnModernAndroidButTheRawFlagIsStillStored() {
        stored.edit().putBoolean("pref_gpodnet_notifications", false).commit();

        assertTrue(UserPreferences.gpodnetNotificationsEnabled());
        assertFalse(UserPreferences.getGpodnetNotificationsEnabledRaw());

        UserPreferences.setGpodnetNotificationsEnabled();

        assertTrue(UserPreferences.getGpodnetNotificationsEnabledRaw());
    }
}
