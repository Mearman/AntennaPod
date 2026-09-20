package de.danoeh.antennapod.storage.database.mapper;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedCursorTest {

    private CursorRow feedRow() {
        return new CursorRow()
                .with(PodDBAdapter.SELECT_KEY_FEED_ID, 21L)
                .with(PodDBAdapter.KEY_LASTUPDATE, "Mon, 01 Jan 2024 00:00:00 GMT")
                .with(PodDBAdapter.KEY_TITLE, "Feed title")
                .with(PodDBAdapter.KEY_CUSTOM_TITLE, "Custom title")
                .with(PodDBAdapter.KEY_LINK, "https://example.com")
                .with(PodDBAdapter.KEY_DESCRIPTION, "Feed description")
                .with(PodDBAdapter.KEY_PAYMENT_LINK, "https://example.com/donate")
                .with(PodDBAdapter.KEY_AUTHOR, "Author")
                .with(PodDBAdapter.KEY_LANGUAGE, "en")
                .with(PodDBAdapter.KEY_TYPE, "rss")
                .with(PodDBAdapter.KEY_FEED_IDENTIFIER, "identifier")
                .with(PodDBAdapter.KEY_FILE_URL, "/feeds/file.xml")
                .with(PodDBAdapter.KEY_DOWNLOAD_URL, "https://example.com/feed.xml")
                .with(PodDBAdapter.KEY_LAST_REFRESH_ATTEMPT, 1_700_000_000_000L)
                .with(PodDBAdapter.KEY_IS_PAGED, 1)
                .with(PodDBAdapter.KEY_NEXT_PAGE_LINK, "https://example.com/feed.xml?page=2")
                .with(PodDBAdapter.KEY_HIDE, "unplayed")
                .with(PodDBAdapter.KEY_SORT_ORDER, "3")
                .with(PodDBAdapter.KEY_LAST_UPDATE_FAILED, 1)
                .with(PodDBAdapter.KEY_IMAGE_URL, "https://example.com/cover.png")
                .with(PodDBAdapter.KEY_STATE, Feed.STATE_ARCHIVED)
                .with(PodDBAdapter.KEY_AUTO_DOWNLOAD_ENABLED, FeedPreferences.AutoDownloadSetting.DISABLED.code)
                .with(PodDBAdapter.KEY_KEEP_UPDATED, 0)
                .with(PodDBAdapter.KEY_AUTO_DELETE_ACTION, FeedPreferences.AutoDeleteAction.NEVER.code)
                .with(PodDBAdapter.KEY_FEED_VOLUME_ADAPTION, VolumeAdaptionSetting.LIGHT_REDUCTION.toInteger())
                .with(PodDBAdapter.KEY_USERNAME, "user")
                .with(PodDBAdapter.KEY_PASSWORD, "secret")
                .with(PodDBAdapter.KEY_INCLUDE_FILTER, "")
                .with(PodDBAdapter.KEY_EXCLUDE_FILTER, "")
                .with(PodDBAdapter.KEY_MINIMAL_DURATION_FILTER, -1)
                .with(PodDBAdapter.KEY_FEED_PLAYBACK_SPEED, 1.25f)
                .with(PodDBAdapter.KEY_FEED_SKIP_SILENCE, FeedPreferences.SkipSilence.OFF.code)
                .with(PodDBAdapter.KEY_FEED_SKIP_INTRO, 0)
                .with(PodDBAdapter.KEY_FEED_SKIP_ENDING, 0)
                .with(PodDBAdapter.KEY_EPISODE_NOTIFICATION, 0)
                .with(PodDBAdapter.KEY_NEW_EPISODES_ACTION, FeedPreferences.NewEpisodesAction.NOTHING.code)
                .with(PodDBAdapter.KEY_FEED_TAGS, "");
    }

    @Test
    public void rowIsConvertedFieldByField() {
        Feed feed = new FeedCursor(feedRow().build()).getFeed();

        assertEquals(21L, feed.getId());
        assertEquals("Mon, 01 Jan 2024 00:00:00 GMT", feed.getLastModified());
        assertEquals("Custom title", feed.getTitle());
        assertEquals("Custom title", feed.getCustomTitle());
        assertEquals("Feed title", feed.getFeedTitle());
        assertEquals("https://example.com", feed.getLink());
        assertEquals("Feed description", feed.getDescription());
        assertEquals("https://example.com/donate", feed.getPaymentLinks().get(0).url);
        assertEquals("Author", feed.getAuthor());
        assertEquals("en", feed.getLanguage());
        assertEquals("rss", feed.getType());
        assertEquals("identifier", feed.getFeedIdentifier());
        assertEquals("/feeds/file.xml", feed.getLocalFileUrl());
        assertEquals("https://example.com/feed.xml", feed.getDownloadUrl());
        assertEquals(1_700_000_000_000L, feed.getLastRefreshAttempt());
        assertTrue(feed.isPaged());
        assertEquals("https://example.com/feed.xml?page=2", feed.getNextPageLink());
        assertTrue(feed.getItemFilter().showUnplayed);
        assertEquals(SortOrder.EPISODE_TITLE_A_Z, feed.getSortOrder());
        assertTrue(feed.hasLastUpdateFailed());
        assertEquals("https://example.com/cover.png", feed.getImageUrl());
        assertEquals(Feed.STATE_ARCHIVED, feed.getState());
    }

    @Test
    public void preferencesAreAttachedToTheFeed() {
        Feed feed = new FeedCursor(feedRow().build()).getFeed();

        FeedPreferences preferences = feed.getPreferences();
        assertEquals(21L, preferences.getFeedID());
        assertEquals(FeedPreferences.AutoDownloadSetting.DISABLED, preferences.getAutoDownload());
        assertFalse(preferences.getKeepUpdated());
        assertEquals(VolumeAdaptionSetting.LIGHT_REDUCTION, preferences.getVolumeAdaptionSetting());
        assertEquals("user", preferences.getUsername());
        assertEquals("secret", preferences.getPassword());
        assertEquals(FeedPreferences.NewEpisodesAction.NOTHING, preferences.getNewEpisodesAction());
        assertEquals(1.25f, preferences.getFeedPlaybackSpeed(), 0f);
    }

    @Test
    public void zeroBooleanColumnsAreFalse() {
        Feed feed = new FeedCursor(feedRow()
                .with(PodDBAdapter.KEY_IS_PAGED, 0)
                .with(PodDBAdapter.KEY_LAST_UPDATE_FAILED, 0)
                .build()).getFeed();
        assertFalse(feed.isPaged());
        assertFalse(feed.hasLastUpdateFailed());
    }

    @Test
    public void missingSortOrderLeavesFeedWithoutSortOrder() {
        Feed feed = new FeedCursor(feedRow().with(PodDBAdapter.KEY_SORT_ORDER, null).build()).getFeed();
        assertNull(feed.getSortOrder());
    }

    @Test
    public void missingFilterMeansNoFilter() {
        Feed feed = new FeedCursor(feedRow().with(PodDBAdapter.KEY_HIDE, null).build()).getFeed();
        assertFalse(feed.getItemFilter().showUnplayed);
        assertEquals(0, feed.getItemFilter().getValues().length);
    }

    @Test
    public void sortOrderWithUnsupportedCodeIsRejected() {
        FeedCursor cursor = new FeedCursor(feedRow().with(PodDBAdapter.KEY_SORT_ORDER, "9999").build());
        assertThrows(IllegalArgumentException.class, cursor::getFeed);
    }

    @Test
    public void missingColumnIsRejectedWhenCursorIsWrapped() {
        CursorRow incomplete = new CursorRow().with(PodDBAdapter.SELECT_KEY_FEED_ID, 1L);
        assertThrows(IllegalArgumentException.class, () -> new FeedCursor(incomplete.build()));
    }
}
