package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class FeedIdentityAndTitleTest {
    private Feed feed;

    @Before
    public void setUp() {
        feed = new Feed(1, null, "Feed title", "http://example.com", "d", null, "a", "en", null, "feed-id",
                null, null, "http://example.com/feed.xml", 0);
    }

    @Test
    public void identifyingValue_prefersFeedIdentifier() {
        assertEquals("feed-id", feed.getIdentifyingValue());
    }

    @Test
    public void identifyingValue_fallsBackToDownloadUrlThenTitleThenLink() {
        feed.setFeedIdentifier("");
        assertEquals("http://example.com/feed.xml", feed.getIdentifyingValue());

        feed.setDownloadUrl(null);
        assertEquals("Feed title", feed.getIdentifyingValue());

        feed.setTitle("");
        assertEquals("http://example.com", feed.getIdentifyingValue());
    }

    @Test
    public void identifyingValue_emptyDownloadUrlIsSkipped() {
        feed.setFeedIdentifier(null);
        feed.setDownloadUrl("");
        assertEquals("Feed title", feed.getIdentifyingValue());
    }

    @Test
    public void getTitle_prefersCustomTitle() {
        feed.setCustomTitle("My name");
        assertEquals("My name", feed.getTitle());
        assertEquals("Feed title", feed.getFeedTitle());
    }

    @Test
    public void setCustomTitle_nullOrSameAsFeedTitleClearsCustomTitle() {
        feed.setCustomTitle("My name");
        feed.setCustomTitle("Feed title");
        assertNull(feed.getCustomTitle());

        feed.setCustomTitle("My name");
        feed.setCustomTitle(null);
        assertNull(feed.getCustomTitle());
        assertEquals("Feed title", feed.getTitle());
    }

    @Test
    public void getTitle_emptyCustomTitleFallsBackToFeedTitle() {
        feed.setCustomTitle("");
        assertEquals("Feed title", feed.getTitle());
    }

    @Test
    public void humanReadableIdentifier_prefersCustomThenFeedTitleThenDownloadUrl() {
        feed.setCustomTitle("My name");
        assertEquals("My name", feed.getHumanReadableIdentifier());

        feed.setCustomTitle(null);
        assertEquals("Feed title", feed.getHumanReadableIdentifier());

        feed.setTitle("");
        assertEquals("http://example.com/feed.xml", feed.getHumanReadableIdentifier());
    }

    @Test
    public void equals_comparesIdOnly() {
        Feed same = new Feed(1, null, "Other", "other", "d", null, "a", "en", null, "other", null, null, "other", 0);
        assertEquals(feed, feed);
        assertEquals(feed, same);
        assertEquals(feed.hashCode(), same.hashCode());
        same.setId(2);
        assertNotEquals(feed, same);
        assertNotEquals(feed, null);
        assertNotEquals(feed, "feed");
    }

    @Test
    public void setId_propagatesToPreferences() {
        FeedPreferences preferences = new FeedPreferences(0, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, null, null);
        feed.setPreferences(preferences);
        feed.setId(55);
        assertEquals(55, feed.getId());
        assertEquals(55, preferences.getFeedID());
    }

    @Test
    public void setId_withoutPreferencesOnlyChangesId() {
        feed.setId(9);
        assertEquals(9, feed.getId());
        assertNull(feed.getPreferences());
    }
}
