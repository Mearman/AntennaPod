package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FeedAccessorsTest {
    private Feed feed;

    @Before
    public void setUp() {
        feed = FeedMother.anyFeed();
    }

    @Test
    public void setters_replaceValues() {
        FeedPreferences preferences = new FeedPreferences(0, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, null, null);
        feed.setPreferences(preferences);
        feed.setLastModified("new-etag");
        feed.setType(Feed.TYPE_ATOM1);
        feed.setLocalFileUrl("/new/file");
        feed.setPageNr(2);
        feed.setPaged(true);
        feed.setNextPageLink("http://example.com/page3");
        feed.setLastUpdateFailed(true);
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
        feed.setSortOrder(SortOrder.EPISODE_TITLE_A_Z);

        assertSame(preferences, feed.getPreferences());
        assertEquals("new-etag", feed.getLastModified());
        assertEquals(Feed.TYPE_ATOM1, feed.getType());
        assertEquals("/new/file", feed.getLocalFileUrl());
        assertEquals(2, feed.getPageNr());
        assertTrue(feed.isPaged());
        assertEquals("http://example.com/page3", feed.getNextPageLink());
        assertTrue(feed.hasLastUpdateFailed());
        assertEquals(Feed.STATE_NOT_SUBSCRIBED, feed.getState());
        assertEquals(SortOrder.EPISODE_TITLE_A_Z, feed.getSortOrder());
        feed.setLastUpdateFailed(false);
        assertFalse(feed.hasLastUpdateFailed());
    }
}
