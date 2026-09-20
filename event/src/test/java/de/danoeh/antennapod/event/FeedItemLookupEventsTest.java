package de.danoeh.antennapod.event;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.model.download.DownloadStatus;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FeedItemLookupEventsTest {

    private static FeedItem itemWithId(long id) {
        FeedItem item = new FeedItem();
        item.setId(id);
        return item;
    }

    private static FeedItem itemWithDownloadUrl(String url) {
        FeedItem item = new FeedItem();
        item.setMedia(new FeedMedia(item, url, 0, "audio/mpeg"));
        return item;
    }

    @Test
    public void itemIndexIsFoundById() {
        List<FeedItem> items = Arrays.asList(itemWithId(10), itemWithId(20), itemWithId(30));
        assertEquals(1, FeedItemEvent.indexOfItemWithId(items, 20));
    }

    @Test
    public void itemIndexIsMinusOneWhenIdIsMissing() {
        List<FeedItem> items = Arrays.asList(itemWithId(10), itemWithId(20));
        assertEquals(-1, FeedItemEvent.indexOfItemWithId(items, 99));
    }

    @Test
    public void itemIndexLookupSkipsNullEntries() {
        List<FeedItem> items = Arrays.asList(null, itemWithId(20));
        assertEquals(1, FeedItemEvent.indexOfItemWithId(items, 20));
    }

    @Test
    public void firstMatchingItemIsReturnedForDuplicateIds() {
        List<FeedItem> items = Arrays.asList(itemWithId(5), itemWithId(5));
        assertEquals(0, FeedItemEvent.indexOfItemWithId(items, 5));
    }

    @Test
    public void feedItemEventKeepsItemsAndUnreadFlag() {
        List<FeedItem> items = new ArrayList<>(List.of(itemWithId(1)));
        FeedItemEvent event = new FeedItemEvent(items, true);
        assertSame(items, event.items);
        assertTrue(event.unreadStatusChanged);
        assertFalse(new FeedItemEvent(items, false).unreadStatusChanged);
    }

    @Test
    public void downloadIndexIsFoundByMediaUrl() {
        List<FeedItem> items = Arrays.asList(itemWithDownloadUrl("https://example.com/a.mp3"),
                itemWithDownloadUrl("https://example.com/b.mp3"));
        assertEquals(1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "https://example.com/b.mp3"));
    }

    @Test
    public void downloadIndexIsMinusOneForUnknownUrl() {
        List<FeedItem> items = List.of(itemWithDownloadUrl("https://example.com/a.mp3"));
        assertEquals(-1, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "https://example.com/other.mp3"));
    }

    @Test
    public void downloadIndexLookupSkipsNullItemsAndItemsWithoutMedia() {
        List<FeedItem> items = Arrays.asList(null, new FeedItem(), itemWithDownloadUrl("https://example.com/a.mp3"));
        assertEquals(2, EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, "https://example.com/a.mp3"));
    }

    @Test
    public void episodeDownloadEventExposesUrlsOfStatusMap() {
        Map<String, DownloadStatus> statuses = new HashMap<>();
        statuses.put("https://example.com/a.mp3", new DownloadStatus(DownloadStatus.STATE_RUNNING, 10));
        statuses.put("https://example.com/b.mp3", new DownloadStatus(DownloadStatus.STATE_QUEUED, 0));
        assertEquals(statuses.keySet(), new EpisodeDownloadEvent(statuses).getUrls());
    }
}
