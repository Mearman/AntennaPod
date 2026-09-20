package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FeedConstructionTest {

    @Test
    public void databaseConstructor_keepsAllValues() {
        String funding = "http://example.com/pay" + FeedFunding.FUNDING_TITLE_SEPARATOR + "Donate";
        Feed feed = new Feed(3, "etag", "Feed title", "Custom", "http://example.com", "Description", funding,
                "Author", "en", Feed.TYPE_RSS2, "feed-id", "http://example.com/image", "/local/file",
                "http://example.com/feed.xml", 1234, true, "http://example.com/page2", "played,downloaded",
                SortOrder.DATE_NEW_OLD, true, Feed.STATE_ARCHIVED);

        assertEquals(3, feed.getId());
        assertEquals("etag", feed.getLastModified());
        assertEquals("Feed title", feed.getFeedTitle());
        assertEquals("Custom", feed.getCustomTitle());
        assertEquals("http://example.com", feed.getLink());
        assertEquals("Description", feed.getDescription());
        assertEquals(Arrays.asList(new FeedFunding("http://example.com/pay", "Donate")), feed.getPaymentLinks());
        assertEquals("Author", feed.getAuthor());
        assertEquals("en", feed.getLanguage());
        assertEquals(Feed.TYPE_RSS2, feed.getType());
        assertEquals("feed-id", feed.getFeedIdentifier());
        assertEquals("http://example.com/image", feed.getImageUrl());
        assertEquals("/local/file", feed.getLocalFileUrl());
        assertEquals("http://example.com/feed.xml", feed.getDownloadUrl());
        assertEquals(1234, feed.getLastRefreshAttempt());
        assertTrue(feed.isPaged());
        assertEquals("http://example.com/page2", feed.getNextPageLink());
        assertEquals(Arrays.asList("played", "downloaded"), feed.getItemFilter().getValuesList());
        assertEquals(SortOrder.DATE_NEW_OLD, feed.getSortOrder());
        assertTrue(feed.hasLastUpdateFailed());
        assertEquals(Feed.STATE_ARCHIVED, feed.getState());
        assertTrue(feed.getItems().isEmpty());
    }

    @Test
    public void databaseConstructor_nullFilterCreatesUnfilteredItemFilter() {
        Feed feed = FeedMother.anyFeed();
        assertNotNull(feed.getItemFilter());
        assertEquals(0, feed.getItemFilter().getValues().length);
    }

    @Test
    public void databaseConstructor_interFeedSortOrderIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Feed(1, null, "t", null, "l", "d", null, "a", "en", null, "id", null, null, "url", 0,
                        false, null, null, SortOrder.RANDOM, false, Feed.STATE_SUBSCRIBED));
    }

    @Test
    public void shortConstructor_defaultsToSubscribedAndNotPaged() {
        Feed feed = FeedMother.anyFeed();
        assertEquals(Feed.STATE_SUBSCRIBED, feed.getState());
        assertFalse(feed.isPaged());
        assertNull(feed.getNextPageLink());
        assertNull(feed.getCustomTitle());
        assertNull(feed.getSortOrder());
        assertFalse(feed.hasLastUpdateFailed());
        assertEquals("title", feed.getTitle());
    }

    @Test
    public void downloadRequestConstructor_setsOnlyUrlAndLastModified() {
        Feed feed = new Feed("http://example.com/feed.xml", "etag");
        assertEquals("http://example.com/feed.xml", feed.getDownloadUrl());
        assertEquals("etag", feed.getLastModified());
        assertNull(feed.getLocalFileUrl());
        assertEquals(0, feed.getLastRefreshAttempt());
        assertNull(feed.getTitle());
        assertNull(feed.getPreferences());
    }

    @Test
    public void downloadRequestConstructorWithTitle_setsFeedTitle() {
        Feed feed = new Feed("http://example.com/feed.xml", "etag", "Known title");
        assertEquals("Known title", feed.getFeedTitle());
        assertEquals("Known title", feed.getTitle());
        assertNull(feed.getPreferences());
    }

    @Test
    public void downloadRequestConstructorWithCredentials_createsPreferencesWithGlobalDefaults() {
        Feed feed = new Feed("http://example.com/feed.xml", "etag", "Title", "user", "secret");
        FeedPreferences preferences = feed.getPreferences();
        assertEquals("user", preferences.getUsername());
        assertEquals("secret", preferences.getPassword());
        assertEquals(FeedPreferences.AutoDownloadSetting.GLOBAL, preferences.getAutoDownload());
        assertEquals(FeedPreferences.AutoDeleteAction.GLOBAL, preferences.getAutoDeleteAction());
        assertEquals(FeedPreferences.NewEpisodesAction.GLOBAL, preferences.getNewEpisodesAction());
        assertEquals(VolumeAdaptionSetting.OFF, preferences.getVolumeAdaptionSetting());
    }
}
