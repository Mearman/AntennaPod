package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.download.DownloadStatus;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;

public class ItemEnqueuePositionCalculatorDownloadsTest {
    private static final int RANDOM_SAMPLES = 1000;
    private final Map<String, DownloadStatus> currentDownloads = new HashMap<>();
    private List<FeedItem> queue;

    @Before
    public void setUp() {
        DownloadServiceInterface stub = new DownloadServiceInterfaceStub();
        stub.setCurrentDownloads(currentDownloads);
        DownloadServiceInterface.setImpl(stub);
        queue = new ArrayList<>(List.of(createFeedItem(11), createFeedItem(12), createFeedItem(13),
                createFeedItem(14)));
    }

    @After
    public void tearDown() {
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
    }

    private static FeedItem createFeedItem(long id) {
        Feed feed = new Feed("http://example.com/feed", null, "title");
        FeedItem item = new FeedItem(id, "Item" + id, "ItemId" + id, "url", new Date(), FeedItem.PLAYED, feed);
        FeedMedia media = new FeedMedia(item, "http://download.url.net/" + id, 1234567, "audio/mpeg");
        media.setId(id);
        item.setMedia(media);
        return item;
    }

    private void markDownloading(FeedItem item, int state) {
        currentDownloads.put(item.getMedia().getDownloadUrl(), new DownloadStatus(state, 0));
    }

    private int position(EnqueueLocation location, FeedItem currentlyPlaying) {
        return new ItemEnqueuePositionCalculator(location).calcPosition(queue,
                currentlyPlaying == null ? null : currentlyPlaying.getMedia());
    }

    @Test
    public void frontSkipsLeadingItemsThatAreStillDownloading() {
        markDownloading(queue.get(0), DownloadStatus.STATE_RUNNING);
        markDownloading(queue.get(1), DownloadStatus.STATE_QUEUED);
        assertEquals(2, position(EnqueueLocation.FRONT, null));
    }

    @Test
    public void frontIgnoresDownloadingItemsBehindTheFirstFinishedOne() {
        markDownloading(queue.get(1), DownloadStatus.STATE_RUNNING);
        assertEquals(0, position(EnqueueLocation.FRONT, null));
    }

    @Test
    public void frontGoesToTheEndWhenEveryQueuedItemIsDownloading() {
        for (FeedItem item : queue) {
            markDownloading(item, DownloadStatus.STATE_RUNNING);
        }
        assertEquals(queue.size(), position(EnqueueLocation.FRONT, null));
    }

    @Test
    public void completedDownloadsDoNotCountAsDownloading() {
        markDownloading(queue.get(0), DownloadStatus.STATE_COMPLETED);
        assertEquals(0, position(EnqueueLocation.FRONT, null));
    }

    @Test
    public void queuedItemWithoutMediaDoesNotCountAsDownloading() {
        queue.get(0).setMedia(null);
        assertEquals(0, position(EnqueueLocation.FRONT, null));
    }

    @Test
    public void afterCurrentlyPlayingSkipsFollowingItemsThatAreStillDownloading() {
        markDownloading(queue.get(1), DownloadStatus.STATE_RUNNING);
        markDownloading(queue.get(2), DownloadStatus.STATE_RUNNING);
        assertEquals(3, position(EnqueueLocation.AFTER_CURRENTLY_PLAYING, queue.get(0)));
    }

    @Test
    public void afterCurrentlyPlayingGoesToTheEndWhenPlayingLastItem() {
        assertEquals(4, position(EnqueueLocation.AFTER_CURRENTLY_PLAYING, queue.get(3)));
    }

    @Test
    public void afterCurrentlyPlayingIgnoresDownloadsBeforeTheCurrentItem() {
        markDownloading(queue.get(0), DownloadStatus.STATE_RUNNING);
        assertEquals(2, position(EnqueueLocation.AFTER_CURRENTLY_PLAYING, queue.get(1)));
    }

    @Test
    public void randomPositionCoversEveryInsertionIndexAndNothingElse() {
        Set<Integer> positions = new TreeSet<>();
        for (int i = 0; i < RANDOM_SAMPLES; i++) {
            positions.add(position(EnqueueLocation.RANDOM, null));
        }
        Set<Integer> insertionIndexes = new TreeSet<>();
        for (int index = 0; index <= queue.size(); index++) {
            insertionIndexes.add(index);
        }
        assertEquals(insertionIndexes, positions);
    }

    @Test
    public void randomPositionInEmptyQueueIsZero() {
        queue.clear();
        assertEquals(0, position(EnqueueLocation.RANDOM, null));
    }

    @Test
    public void backAlwaysGoesBehindDownloadingItems() {
        markDownloading(queue.get(3), DownloadStatus.STATE_RUNNING);
        assertEquals(queue.size(), position(EnqueueLocation.BACK, null));
    }
}
