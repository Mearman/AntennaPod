package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FeedItemsTest {
    private Feed feed;

    @Before
    public void setUp() {
        feed = FeedMother.anyFeed();
    }

    private FeedItem addItem(long id, Date pubDate) {
        FeedItem item = new FeedItem(id, "Item " + id, "guid-" + id, "link-" + id, pubDate, FeedItem.UNPLAYED, feed);
        feed.getItems().add(item);
        return item;
    }

    @Test
    public void getItemAtIndex_returnsItemAtThatPosition() {
        FeedItem first = addItem(1, new Date(1000));
        FeedItem second = addItem(2, new Date(2000));
        assertSame(first, feed.getItemAtIndex(0));
        assertSame(second, feed.getItemAtIndex(1));
    }

    @Test
    public void setItems_replacesItemList() {
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        assertSame(items, feed.getItems());
    }

    @Test
    public void getMostRecentItem_returnsItemWithLatestPubDate() {
        addItem(1, new Date(1000));
        FeedItem latest = addItem(2, new Date(5000));
        addItem(3, new Date(3000));
        assertSame(latest, feed.getMostRecentItem());
    }

    @Test
    public void getMostRecentItem_ignoresItemsWithoutPubDate() {
        addItem(1, null);
        FeedItem dated = addItem(2, new Date(1000));
        assertSame(dated, feed.getMostRecentItem());
    }

    @Test
    public void getMostRecentItem_noItemsOrNoDates_returnsNull() {
        assertNull(feed.getMostRecentItem());
        addItem(1, null);
        assertNull(feed.getMostRecentItem());
    }

    @Test
    public void hasEpisodeInApp_plainItemsAreNotInApp() {
        addItem(1, new Date());
        assertFalse(feed.hasEpisodeInApp());
    }

    @Test
    public void hasEpisodeInApp_favoriteQueuedOrDownloadedItemsAreInApp() {
        FeedItem favorite = addItem(1, new Date());
        favorite.addTag(FeedItem.TAG_FAVORITE);
        assertTrue(feed.hasEpisodeInApp());

        favorite.removeTag(FeedItem.TAG_FAVORITE);
        assertFalse(feed.hasEpisodeInApp());
        favorite.addTag(FeedItem.TAG_QUEUE);
        assertTrue(feed.hasEpisodeInApp());

        favorite.removeTag(FeedItem.TAG_QUEUE);
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        favorite.setMedia(media);
        assertFalse(feed.hasEpisodeInApp());
        media.setDownloaded(true, 1000);
        assertTrue(feed.hasEpisodeInApp());
    }

    @Test
    public void hasEpisodeInApp_playedItemAloneDoesNotCount() {
        addItem(1, new Date()).setPlayed(true);
        assertFalse(feed.hasEpisodeInApp());
    }

    @Test
    public void hasInteractedWithEpisode_playedItemCounts() {
        FeedItem item = addItem(1, new Date());
        assertFalse(feed.hasInteractedWithEpisode());
        item.setPlayed(true);
        assertTrue(feed.hasInteractedWithEpisode());
    }

    @Test
    public void hasInteractedWithEpisode_startedPlaybackCounts() {
        FeedItem item = addItem(1, new Date());
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        item.setMedia(media);
        assertFalse(feed.hasInteractedWithEpisode());
        media.setPosition(1000);
        assertTrue(feed.hasInteractedWithEpisode());
    }

    @Test
    public void hasInteractedWithEpisode_taggedOrDownloadedItemsCount() {
        FeedItem item = addItem(1, new Date());
        item.addTag(FeedItem.TAG_QUEUE);
        assertTrue(feed.hasInteractedWithEpisode());
        item.removeTag(FeedItem.TAG_QUEUE);
        item.addTag(FeedItem.TAG_FAVORITE);
        assertTrue(feed.hasInteractedWithEpisode());
        item.removeTag(FeedItem.TAG_FAVORITE);
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        item.setMedia(media);
        media.setDownloaded(true, 1000);
        assertTrue(feed.hasInteractedWithEpisode());
    }

    @Test
    public void episodeChecks_withoutItemListReturnFalse() {
        feed.setItems(null);
        assertFalse(feed.hasEpisodeInApp());
        assertFalse(feed.hasInteractedWithEpisode());
    }

    @Test
    public void isLocalFeed_dependsOnLocalFolderPrefixOfDownloadUrl() {
        assertFalse(feed.isLocalFeed());
        feed.setDownloadUrl(Feed.PREFIX_LOCAL_FOLDER + "content://folder");
        assertTrue(feed.isLocalFeed());
    }
}
