package de.danoeh.antennapod.storage.database.mapper;

import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedPreferencesCursorTest {

    private CursorRow preferencesRow() {
        return new CursorRow()
                .with(PodDBAdapter.SELECT_KEY_FEED_ID, 6L)
                .with(PodDBAdapter.KEY_AUTO_DOWNLOAD_ENABLED, FeedPreferences.AutoDownloadSetting.ENABLED.code)
                .with(PodDBAdapter.KEY_KEEP_UPDATED, 1)
                .with(PodDBAdapter.KEY_AUTO_DELETE_ACTION, FeedPreferences.AutoDeleteAction.ALWAYS.code)
                .with(PodDBAdapter.KEY_FEED_VOLUME_ADAPTION, VolumeAdaptionSetting.HEAVY_BOOST.toInteger())
                .with(PodDBAdapter.KEY_USERNAME, "user")
                .with(PodDBAdapter.KEY_PASSWORD, "secret")
                .with(PodDBAdapter.KEY_INCLUDE_FILTER, "interview")
                .with(PodDBAdapter.KEY_EXCLUDE_FILTER, "trailer")
                .with(PodDBAdapter.KEY_MINIMAL_DURATION_FILTER, 300)
                .with(PodDBAdapter.KEY_FEED_PLAYBACK_SPEED, 1.5f)
                .with(PodDBAdapter.KEY_FEED_SKIP_SILENCE, FeedPreferences.SkipSilence.AGGRESSIVE.code)
                .with(PodDBAdapter.KEY_FEED_SKIP_INTRO, 15)
                .with(PodDBAdapter.KEY_FEED_SKIP_ENDING, 20)
                .with(PodDBAdapter.KEY_EPISODE_NOTIFICATION, 1)
                .with(PodDBAdapter.KEY_NEW_EPISODES_ACTION, FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE.code)
                .with(PodDBAdapter.KEY_FEED_TAGS, "news" + FeedPreferences.TAG_SEPARATOR + "tech");
    }

    @Test
    public void rowIsConvertedFieldByField() {
        FeedPreferences preferences = new FeedPreferencesCursor(preferencesRow().build()).getFeedPreferences();

        assertEquals(6L, preferences.getFeedID());
        assertEquals(FeedPreferences.AutoDownloadSetting.ENABLED, preferences.getAutoDownload());
        assertTrue(preferences.getKeepUpdated());
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, preferences.getAutoDeleteAction());
        assertEquals(VolumeAdaptionSetting.HEAVY_BOOST, preferences.getVolumeAdaptionSetting());
        assertEquals("user", preferences.getUsername());
        assertEquals("secret", preferences.getPassword());
        assertEquals("interview", preferences.getFilter().getIncludeFilterRaw());
        assertEquals("trailer", preferences.getFilter().getExcludeFilterRaw());
        assertEquals(300, preferences.getFilter().getMinimalDurationFilter());
        assertEquals(1.5f, preferences.getFeedPlaybackSpeed(), 0f);
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, preferences.getFeedSkipSilence());
        assertEquals(15, preferences.getFeedSkipIntro());
        assertEquals(20, preferences.getFeedSkipEnding());
        assertTrue(preferences.getShowEpisodeNotification());
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, preferences.getNewEpisodesAction());
    }

    @Test
    public void tagsAreSplitAtTheTagSeparator() {
        Set<String> tags = new FeedPreferencesCursor(preferencesRow().build()).getFeedPreferences().getTags();
        assertEquals(Set.of("news", "tech"), tags);
    }

    @Test
    public void emptyTagsColumnMeansRootTag() {
        Set<String> empty = new FeedPreferencesCursor(preferencesRow().with(PodDBAdapter.KEY_FEED_TAGS, "").build())
                .getFeedPreferences().getTags();
        Set<String> missing = new FeedPreferencesCursor(preferencesRow().with(PodDBAdapter.KEY_FEED_TAGS, null).build())
                .getFeedPreferences().getTags();
        assertEquals(Set.of(FeedPreferences.TAG_ROOT), empty);
        assertEquals(Set.of(FeedPreferences.TAG_ROOT), missing);
    }

    @Test
    public void zeroBooleanColumnsAreFalse() {
        FeedPreferences preferences = new FeedPreferencesCursor(preferencesRow()
                .with(PodDBAdapter.KEY_KEEP_UPDATED, 0)
                .with(PodDBAdapter.KEY_EPISODE_NOTIFICATION, 0)
                .build()).getFeedPreferences();
        assertFalse(preferences.getKeepUpdated());
        assertFalse(preferences.getShowEpisodeNotification());
    }

    @Test
    public void unknownEnumCodesFallBackToDocumentedDefaults() {
        FeedPreferences preferences = new FeedPreferencesCursor(preferencesRow()
                .with(PodDBAdapter.KEY_AUTO_DOWNLOAD_ENABLED, 99)
                .with(PodDBAdapter.KEY_AUTO_DELETE_ACTION, 99)
                .with(PodDBAdapter.KEY_NEW_EPISODES_ACTION, 99)
                .with(PodDBAdapter.KEY_FEED_SKIP_SILENCE, 99)
                .build()).getFeedPreferences();
        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, preferences.getAutoDownload());
        assertEquals(FeedPreferences.AutoDeleteAction.NEVER, preferences.getAutoDeleteAction());
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, preferences.getNewEpisodesAction());
        assertEquals(FeedPreferences.SkipSilence.GLOBAL, preferences.getFeedSkipSilence());
    }

    @Test
    public void unknownVolumeAdaptionCodeIsRejected() {
        FeedPreferencesCursor cursor = new FeedPreferencesCursor(
                preferencesRow().with(PodDBAdapter.KEY_FEED_VOLUME_ADAPTION, 99).build());
        assertThrows(IllegalArgumentException.class, cursor::getFeedPreferences);
    }

    @Test
    public void missingColumnIsRejectedWhenCursorIsWrapped() {
        CursorRow incomplete = new CursorRow().with(PodDBAdapter.SELECT_KEY_FEED_ID, 1L);
        assertThrows(IllegalArgumentException.class, () -> new FeedPreferencesCursor(incomplete.build()));
    }
}
