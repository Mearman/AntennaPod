package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedPreferencesTest {
    private FeedPreferences preferences;

    @Before
    public void setUp() {
        preferences = new FeedPreferences(5, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, "user", "pass");
    }

    @Test
    public void shortConstructor_appliesDefaults() {
        assertEquals(5, preferences.getFeedID());
        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, preferences.getAutoDownload());
        assertTrue(preferences.getKeepUpdated());
        assertEquals(FeedPreferences.AutoDeleteAction.GLOBAL, preferences.getAutoDeleteAction());
        assertEquals(VolumeAdaptionSetting.OFF, preferences.getVolumeAdaptionSetting());
        assertEquals(FeedPreferences.NewEpisodesAction.GLOBAL, preferences.getNewEpisodesAction());
        assertEquals("user", preferences.getUsername());
        assertEquals("pass", preferences.getPassword());
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL, preferences.getFeedPlaybackSpeed(), 0);
        assertEquals(0, preferences.getFeedSkipIntro());
        assertEquals(0, preferences.getFeedSkipEnding());
        assertFalse(preferences.getShowEpisodeNotification());
        assertTrue(preferences.getTags().isEmpty());
        assertFalse(preferences.getFilter().hasIncludeFilter());
        assertFalse(preferences.getFilter().hasExcludeFilter());
    }

    @Test
    public void fullConstructor_keepsAllValuesAndCopiesTags() {
        Set<String> tags = new HashSet<>(Arrays.asList("news", "tech"));
        FeedFilter filter = new FeedFilter("include", "exclude");
        FeedPreferences full = new FeedPreferences(9, FeedPreferences.AutoDownloadSetting.ENABLED, false,
                FeedPreferences.AutoDeleteAction.ALWAYS, VolumeAdaptionSetting.LIGHT_BOOST, "u", "p", filter,
                1.5f, 10, 20, FeedPreferences.SkipSilence.AGGRESSIVE, true,
                FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, tags);

        assertEquals(9, full.getFeedID());
        assertEquals(FeedPreferences.AutoDownloadSetting.ENABLED, full.getAutoDownload());
        assertFalse(full.getKeepUpdated());
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, full.getAutoDeleteAction());
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, full.getCurrentAutoDelete());
        assertEquals(VolumeAdaptionSetting.LIGHT_BOOST, full.getVolumeAdaptionSetting());
        assertSame(filter, full.getFilter());
        assertEquals(1.5f, full.getFeedPlaybackSpeed(), 0);
        assertEquals(10, full.getFeedSkipIntro());
        assertEquals(20, full.getFeedSkipEnding());
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, full.getFeedSkipSilence());
        assertTrue(full.getShowEpisodeNotification());
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, full.getNewEpisodesAction());
        assertEquals(tags, full.getTags());
        assertNotSame(tags, full.getTags());
        tags.add("later");
        assertFalse(full.getTags().contains("later"));
    }

    @Test
    public void isAutoDownload_enabledAndDisabledIgnoreGlobalDefault() {
        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED);
        assertTrue(preferences.isAutoDownload(false));
        assertTrue(preferences.isAutoDownload(true));

        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.DISABLED);
        assertFalse(preferences.isAutoDownload(false));
        assertFalse(preferences.isAutoDownload(true));
    }

    @Test
    public void isAutoDownload_globalSettingFollowsGlobalDefault() {
        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.GLOBAL);
        assertTrue(preferences.isAutoDownload(true));
        assertFalse(preferences.isAutoDownload(false));
    }

    @Test
    public void getFeedSkipSilence_globalPlaybackSpeedForcesGlobalSkipSilence() {
        preferences.setFeedSkipSilence(FeedPreferences.SkipSilence.AGGRESSIVE);
        preferences.setFeedPlaybackSpeed(FeedPreferences.SPEED_USE_GLOBAL);
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, preferences.getFeedSkipSilence());
    }

    @Test
    public void getFeedSkipSilence_customPlaybackSpeedReturnsStoredSetting() {
        preferences.setFeedSkipSilence(FeedPreferences.SkipSilence.AGGRESSIVE);
        preferences.setFeedPlaybackSpeed(1.25f);
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, preferences.getFeedSkipSilence());
    }

    @Test
    public void updateFromOther_copiesOnlyCredentials() {
        FeedPreferences other = new FeedPreferences(99, FeedPreferences.AutoDownloadSetting.ENABLED,
                FeedPreferences.AutoDeleteAction.ALWAYS, VolumeAdaptionSetting.HEAVY_BOOST,
                FeedPreferences.NewEpisodesAction.NOTHING, "otherUser", "otherPass");

        preferences.updateFromOther(other);

        assertEquals("otherUser", preferences.getUsername());
        assertEquals("otherPass", preferences.getPassword());
        assertEquals(5, preferences.getFeedID());
        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, preferences.getAutoDownload());
        assertEquals(FeedPreferences.AutoDeleteAction.GLOBAL, preferences.getAutoDeleteAction());
        assertEquals(VolumeAdaptionSetting.OFF, preferences.getVolumeAdaptionSetting());
        assertEquals(FeedPreferences.NewEpisodesAction.GLOBAL, preferences.getNewEpisodesAction());
    }

    @Test
    public void updateFromOther_nullLeavesPreferencesUnchanged() {
        preferences.updateFromOther(null);
        assertEquals("user", preferences.getUsername());
        assertEquals("pass", preferences.getPassword());
    }

    @Test
    public void setters_replaceValues() {
        FeedFilter filter = new FeedFilter("a", "b");
        preferences.setFilter(filter);
        preferences.setKeepUpdated(false);
        preferences.setFeedID(12);
        preferences.setAutoDeleteAction(FeedPreferences.AutoDeleteAction.NEVER);
        preferences.setVolumeAdaptionSetting(VolumeAdaptionSetting.HEAVY_REDUCTION);
        preferences.setNewEpisodesAction(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX);
        preferences.setUsername("changedUser");
        preferences.setPassword("changedPass");
        preferences.setFeedSkipIntro(3);
        preferences.setFeedSkipEnding(4);
        preferences.setShowEpisodeNotification(true);

        assertSame(filter, preferences.getFilter());
        assertFalse(preferences.getKeepUpdated());
        assertEquals(12, preferences.getFeedID());
        assertEquals(FeedPreferences.AutoDeleteAction.NEVER, preferences.getAutoDeleteAction());
        assertEquals(VolumeAdaptionSetting.HEAVY_REDUCTION, preferences.getVolumeAdaptionSetting());
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, preferences.getNewEpisodesAction());
        assertEquals("changedUser", preferences.getUsername());
        assertEquals("changedPass", preferences.getPassword());
        assertEquals(3, preferences.getFeedSkipIntro());
        assertEquals(4, preferences.getFeedSkipEnding());
        assertTrue(preferences.getShowEpisodeNotification());
    }

    @Test
    public void getTagsAsString_joinsTagsWithSeparator() {
        preferences.getTags().add("only");
        assertEquals("only", preferences.getTagsAsString());

        preferences.getTags().add("second");
        Set<String> parts = new HashSet<>(Arrays.asList(preferences.getTagsAsString()
                .split(FeedPreferences.TAG_SEPARATOR)));
        assertEquals(new HashSet<>(Arrays.asList("only", "second")), parts);
    }

    @Test
    public void getTagsAsString_noTags_returnsEmptyString() {
        assertEquals("", preferences.getTagsAsString());
    }

    @Test
    public void autoDeleteAction_fromCode_mapsCodesAndFallsBackToNever() {
        for (FeedPreferences.AutoDeleteAction action : FeedPreferences.AutoDeleteAction.values()) {
            assertEquals(action, FeedPreferences.AutoDeleteAction.fromCode(action.code));
        }
        assertEquals(FeedPreferences.AutoDeleteAction.NEVER, FeedPreferences.AutoDeleteAction.fromCode(77));
    }

    @Test
    public void newEpisodesAction_fromCode_mapsCodesAndFallsBackToInbox() {
        for (FeedPreferences.NewEpisodesAction action : FeedPreferences.NewEpisodesAction.values()) {
            assertEquals(action, FeedPreferences.NewEpisodesAction.fromCode(action.code));
        }
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, FeedPreferences.NewEpisodesAction.fromCode(77));
    }

    @Test
    public void skipSilence_fromCode_mapsCodesAndFallsBackToGlobal() {
        for (FeedPreferences.SkipSilence skipSilence : FeedPreferences.SkipSilence.values()) {
            assertEquals(skipSilence, FeedPreferences.SkipSilence.fromCode(skipSilence.code));
        }
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, FeedPreferences.SkipSilence.fromCode(77));
    }

    @Test
    public void autoDownloadSetting_fromInteger_mapsCodesAndFallsBackToGlobal() {
        for (FeedPreferences.AutoDownloadSetting setting : FeedPreferences.AutoDownloadSetting.values()) {
            assertEquals(setting, FeedPreferences.AutoDownloadSetting.fromInteger(setting.code));
        }
        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, FeedPreferences.AutoDownloadSetting.fromInteger(77));
    }

    @Test
    public void autoDownloadSetting_fromBoolean_mapsToEnabledOrDisabled() {
        assertEquals(FeedPreferences.AutoDownloadSetting.ENABLED, FeedPreferences.AutoDownloadSetting.fromBoolean(true));
        assertEquals(FeedPreferences.AutoDownloadSetting.DISABLED,
                FeedPreferences.AutoDownloadSetting.fromBoolean(false));
    }
}
