package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class FeedPreferencesPipelineTest extends FeedPipelineTestBase {

    private static String episode(String guid, String title, String duration) {
        return "<item><guid>" + guid + "</guid><title>" + title + "</title>"
                + "<pubDate>Mon, 02 Jan 2006 15:04:05 +0000</pubDate>"
                + "<enclosure url=\"https://example.com/" + guid + ".mp3\" length=\"5000000\" type=\"audio/mpeg\"/>"
                + (duration == null ? "" : "<itunes:duration>" + duration + "</itunes:duration>")
                + "</item>\n";
    }

    private Feed storeFeedWithEpisodes() throws Exception {
        return parseAndStore(rss("<title>Filtered</title>\n"
                + episode("interview", "Interview with a guest", "45:00")
                + episode("trailer", "Trailer", "00:45")
                + episode("bonus", "Bonus interview outtakes", "12:00")
                + episode("live", "Live Q&amp;A session", "30:00")
                + episode("unknown", "Untimed episode", null)));
    }

    private Feed saveAndReload(Feed feed) {
        DBWriter.setFeedPreferences(feed.getPreferences());
        DBWriter.tearDownTests();
        return reload(feed);
    }

    private boolean downloads(Feed feed, String guid) {
        FeedItem item = storedItem(feed, guid);
        return feed.getPreferences().getFilter().shouldAutoDownload(item);
    }

    @Test
    public void everyFeedPreferenceRoundTripsThroughTheDatabase() throws Exception {
        Feed stored = storeFeedWithEpisodes();
        FeedPreferences preferences = stored.getPreferences();
        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED);
        preferences.setKeepUpdated(false);
        preferences.setAutoDeleteAction(FeedPreferences.AutoDeleteAction.ALWAYS);
        preferences.setVolumeAdaptionSetting(VolumeAdaptionSetting.HEAVY_BOOST);
        preferences.setNewEpisodesAction(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE);
        preferences.setUsername("listener");
        preferences.setPassword("hunter2");
        preferences.setFeedPlaybackSpeed(1.4f);
        preferences.setFeedSkipIntro(15);
        preferences.setFeedSkipEnding(20);
        preferences.setFeedSkipSilence(FeedPreferences.SkipSilence.AGGRESSIVE);
        preferences.setShowEpisodeNotification(true);
        preferences.setFilter(new FeedFilter("interview", "trailer", 120));

        FeedPreferences restored = saveAndReload(stored).getPreferences();

        assertEquals(stored.getId(), restored.getFeedID());
        assertEquals(FeedPreferences.AutoDownloadSetting.ENABLED, restored.getAutoDownload());
        assertFalse(restored.getKeepUpdated());
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, restored.getAutoDeleteAction());
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, restored.getCurrentAutoDelete());
        assertEquals(VolumeAdaptionSetting.HEAVY_BOOST, restored.getVolumeAdaptionSetting());
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, restored.getNewEpisodesAction());
        assertEquals("listener", restored.getUsername());
        assertEquals("hunter2", restored.getPassword());
        assertEquals(1.4f, restored.getFeedPlaybackSpeed(), 0.0001f);
        assertEquals(15, restored.getFeedSkipIntro());
        assertEquals(20, restored.getFeedSkipEnding());
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, restored.getFeedSkipSilence());
        assertTrue(restored.getShowEpisodeNotification());
        assertEquals("interview", restored.getFilter().getIncludeFilterRaw());
        assertEquals("trailer", restored.getFilter().getExcludeFilterRaw());
        assertEquals(120, restored.getFilter().getMinimalDurationFilter());
    }

    @Test
    public void skipSilenceFollowsTheGlobalSettingWhileTheFeedUsesTheGlobalPlaybackSpeed() throws Exception {
        Feed stored = storeFeedWithEpisodes();
        stored.getPreferences().setFeedSkipSilence(FeedPreferences.SkipSilence.AGGRESSIVE);

        FeedPreferences globalSpeed = saveAndReload(stored).getPreferences();
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL, globalSpeed.getFeedPlaybackSpeed(), 0.0001f);
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, globalSpeed.getFeedSkipSilence());

        stored.getPreferences().setFeedPlaybackSpeed(1.1f);
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, saveAndReload(stored).getPreferences()
                .getFeedSkipSilence());
    }

    @Test
    public void newFeedsStartWithTheDefaultPreferences() throws Exception {
        FeedPreferences defaults = storeFeedWithEpisodes().getPreferences();

        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, defaults.getAutoDownload());
        assertTrue(defaults.getKeepUpdated());
        assertEquals(FeedPreferences.AutoDeleteAction.GLOBAL, defaults.getAutoDeleteAction());
        assertEquals(FeedPreferences.NewEpisodesAction.GLOBAL, defaults.getNewEpisodesAction());
        assertEquals(VolumeAdaptionSetting.OFF, defaults.getVolumeAdaptionSetting());
        assertFalse(defaults.getShowEpisodeNotification());
        assertFalse(defaults.getFilter().hasIncludeFilter());
        assertFalse(defaults.getFilter().hasExcludeFilter());
        assertFalse(defaults.getFilter().hasMinimalDurationFilter());
        assertEquals(0, defaults.getFeedSkipIntro());
        assertEquals(0, defaults.getFeedSkipEnding());
    }

    @Test
    public void tagsAreStoredWithTheFeed() throws Exception {
        Feed stored = storeFeedWithEpisodes();
        Set<String> tags = new HashSet<>(Set.of("news", "daily"));
        stored.getPreferences().getTags().clear();
        stored.getPreferences().getTags().addAll(tags);

        FeedPreferences restored = saveAndReload(stored).getPreferences();

        assertEquals(tags, restored.getTags());
        assertEquals(tags, new HashSet<>(List.of(restored.getTagsAsString().split(FeedPreferences.TAG_SEPARATOR))));
    }

    @Test
    public void automaticDownloadFollowsTheFeedSettingOrTheGlobalDefault() throws Exception {
        Feed stored = storeFeedWithEpisodes();
        FeedPreferences preferences = stored.getPreferences();

        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.GLOBAL);
        assertTrue(preferences.isAutoDownload(true));
        assertFalse(preferences.isAutoDownload(false));

        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.ENABLED);
        assertTrue(saveAndReload(stored).getPreferences().isAutoDownload(false));

        preferences.setAutoDownload(FeedPreferences.AutoDownloadSetting.DISABLED);
        assertFalse(saveAndReload(stored).getPreferences().isAutoDownload(true));
    }

    @Test
    public void includeTermsSelectOnlyMatchingStoredEpisodesForAutoDownload() throws Exception {
        Feed stored = storeFeedWithEpisodes();
        stored.getPreferences().setFilter(new FeedFilter("interview", "", -1));

        Feed restored = saveAndReload(stored);

        assertTrue(restored.getPreferences().getFilter().includeOnly());
        assertTrue(downloads(restored, "interview"));
        assertTrue(downloads(restored, "bonus"));
        assertFalse(downloads(restored, "trailer"));
        assertFalse(downloads(restored, "live"));
    }

    @Test
    public void excludeTermsBeatIncludeTermsForStoredEpisodes() throws Exception {
        Feed stored = storeFeedWithEpisodes();
        stored.getPreferences().setFilter(new FeedFilter("interview", "bonus", -1));

        Feed restored = saveAndReload(stored);

        assertTrue(downloads(restored, "interview"));
        assertFalse(downloads(restored, "bonus"));
        assertFalse(downloads(restored, "trailer"));
    }

    @Test
    public void excludeOnlyFilterDownloadsEverythingElse() throws Exception {
        Feed stored = storeFeedWithEpisodes();
        stored.getPreferences().setFilter(new FeedFilter("", "trailer", -1));

        Feed restored = saveAndReload(stored);

        assertTrue(restored.getPreferences().getFilter().excludeOnly());
        assertFalse(downloads(restored, "trailer"));
        assertTrue(downloads(restored, "interview"));
        assertTrue(downloads(restored, "unknown"));
    }

    @Test
    public void quotedTermsMatchAsOnePhraseAndAreCaseInsensitive() throws Exception {
        Feed stored = storeFeedWithEpisodes();
        stored.getPreferences().setFilter(new FeedFilter("\"live q&a\" trailer", "", -1));

        Feed restored = saveAndReload(stored);

        assertEquals(List.of("live q&a", "trailer"), restored.getPreferences().getFilter().getIncludeFilter());
        assertTrue(downloads(restored, "live"));
        assertTrue(downloads(restored, "trailer"));
        assertFalse(downloads(restored, "interview"));
    }

    @Test
    public void minimalDurationSkipsShortEpisodesButNotOnesWithUnknownDuration() throws Exception {
        Feed stored = storeFeedWithEpisodes();
        stored.getPreferences().setFilter(new FeedFilter("", "", 600));

        Feed restored = saveAndReload(stored);

        assertTrue(restored.getPreferences().getFilter().hasMinimalDurationFilter());
        assertTrue(downloads(restored, "interview"));
        assertTrue(downloads(restored, "bonus"));
        assertFalse(downloads(restored, "trailer"));
        assertTrue(downloads(restored, "unknown"));
    }

    @Test
    public void emptyFilterDownloadsEveryEpisode() throws Exception {
        Feed restored = storeFeedWithEpisodes();

        for (String guid : List.of("interview", "trailer", "bonus", "live", "unknown")) {
            assertTrue(guid, downloads(restored, guid));
        }
    }

    @Test
    public void everyVolumeAdaptionSettingSurvivesStorageWithItsFactor() throws Exception {
        Feed stored = storeFeedWithEpisodes();

        for (VolumeAdaptionSetting setting : VolumeAdaptionSetting.values()) {
            stored.getPreferences().setVolumeAdaptionSetting(setting);
            VolumeAdaptionSetting restored = saveAndReload(stored).getPreferences().getVolumeAdaptionSetting();
            assertEquals(setting, restored);
            assertEquals(setting.getAdaptionFactor(), restored.getAdaptionFactor(), 0.0001f);
            assertEquals(setting, VolumeAdaptionSetting.fromInteger(setting.toInteger()));
        }
        assertThrows(IllegalArgumentException.class, () -> VolumeAdaptionSetting.fromInteger(99));
    }

    @Test
    public void feedPreferencesUpdatedFromAnotherFeedOnlyTakeOverTheCredentials() throws Exception {
        Feed stored = storeFeedWithEpisodes();
        stored.getPreferences().setAutoDeleteAction(FeedPreferences.AutoDeleteAction.NEVER);
        stored.getPreferences().setUsername("old-user");
        FeedPreferences incoming = new FeedPreferences(0, FeedPreferences.AutoDownloadSetting.ENABLED,
                FeedPreferences.AutoDeleteAction.ALWAYS, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.NOTHING, "new-user", "new-password");

        stored.getPreferences().updateFromOther(incoming);
        stored.getPreferences().updateFromOther(null);

        assertEquals("new-user", stored.getPreferences().getUsername());
        assertEquals("new-password", stored.getPreferences().getPassword());
        assertEquals(FeedPreferences.AutoDeleteAction.NEVER, stored.getPreferences().getAutoDeleteAction());
        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, stored.getPreferences().getAutoDownload());
    }
}
