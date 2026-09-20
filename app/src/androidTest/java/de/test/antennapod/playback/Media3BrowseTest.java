package de.test.antennapod.playback;

import androidx.media3.common.MediaItem;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaBrowser;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@LargeTest
public class Media3BrowseTest extends Media3ServiceTest {
    private static final int PAGE_SIZE = 100;

    private MediaBrowser browser;

    private MediaBrowser browser() {
        if (browser == null) {
            browser = Media3TestUtils.connectBrowser(context);
            controller = browser;
        }
        return browser;
    }

    @Test
    public void testLibraryRootIsBrowsable() {
        MediaBrowser mediaBrowser = browser();
        LibraryResult<MediaItem> result = Media3TestUtils.awaitFuture(
                Media3TestUtils.getOnMain(() -> mediaBrowser.getLibraryRoot(null)));

        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode);
        assertNotNull(result.value);
        assertEquals(context.getString(R.string.app_name), result.value.mediaMetadata.title.toString());
        assertTrue(result.value.mediaMetadata.isBrowsable);
    }

    @Test
    public void testRootContainsTheTopLevelSections() {
        List<MediaItem> children = childrenOf("root");

        List<String> titles = new ArrayList<>();
        for (MediaItem item : children) {
            titles.add(item.mediaMetadata.title.toString());
            assertTrue("Section " + item.mediaId + " is browsable", item.mediaMetadata.isBrowsable);
            assertFalse("Section " + item.mediaId + " is not playable", item.mediaMetadata.isPlayable);
        }
        assertTrue(titles.contains(context.getString(R.string.queue_label)));
        assertTrue(titles.contains(context.getString(R.string.downloads_label)));
        assertTrue(titles.contains(context.getString(R.string.episodes_label)));
        assertTrue(titles.contains(context.getString(R.string.subscriptions_label)));
        assertTrue(titles.contains(context.getString(R.string.current_playing_episode)));
    }

    @Test
    public void testQueueSectionContainsTheQueuedEpisodes() {
        List<FeedItem> queue = DBReader.getQueue();

        List<MediaItem> children = childrenOf("queue");

        assertEquals(queue.size(), children.size());
        for (int i = 0; i < queue.size(); i++) {
            assertEquals(String.valueOf(queue.get(i).getMedia().getId()), children.get(i).mediaId);
            assertEquals(queue.get(i).getTitle(), children.get(i).mediaMetadata.title.toString());
            assertTrue(children.get(i).mediaMetadata.isPlayable);
        }
    }

    @Test
    public void testQueueSectionIsEmptyAfterClearingTheQueue() throws Exception {
        DBWriter.clearQueue().get();

        assertEquals(0, childrenOf("queue").size());
        MediaItem section = item("queue");
        assertEquals(context.getResources().getQuantityString(R.plurals.num_episodes, 0, 0),
                section.mediaMetadata.subtitle.toString());
    }

    @Test
    public void testSubscriptionsSectionContainsTheFeeds() {
        List<Feed> feeds = DBReader.getFeedList();

        List<MediaItem> children = childrenOf("subscriptions");

        assertEquals(feeds.size(), children.size());
        for (MediaItem child : children) {
            assertTrue(child.mediaId.startsWith(MediaItemAdapter.MEDIA_ID_FEED_PREFIX));
            assertTrue(child.mediaMetadata.isBrowsable);
            assertFalse(child.mediaMetadata.isPlayable);
        }
    }

    @Test
    public void testFeedSectionContainsTheEpisodesOfThatFeed() {
        Feed feed = DBReader.getFeedList().get(0);
        List<FeedItem> items = DBReader.getFeed(feed.getId(), true, 0, PAGE_SIZE).getItems();

        List<MediaItem> children = childrenOf(MediaItemAdapter.MEDIA_ID_FEED_PREFIX + feed.getId());

        assertEquals(items.size(), children.size());
        assertEquals(feed.getTitle(), item(MediaItemAdapter.MEDIA_ID_FEED_PREFIX + feed.getId())
                .mediaMetadata.title.toString());
    }

    @Test
    public void testDownloadsSectionContainsTheDownloadedEpisodes() {
        List<MediaItem> children = childrenOf("downloads");

        assertFalse(children.isEmpty());
        for (MediaItem child : children) {
            FeedMedia media = DBReader.getFeedMedia(Long.parseLong(child.mediaId));
            assertNotNull(media);
            assertTrue("Episode " + child.mediaId + " is downloaded", media.isDownloaded());
        }
    }

    @Test
    public void testContinueListeningSectionStartsWithTheLastPlayedEpisode() throws Exception {
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia media = queue.get(2).getMedia();
        media.setPosition(5000);
        media.setLastPlayedTimeStatistics(System.currentTimeMillis());
        media.setLastPlayedTimeHistory(new Date());
        DBWriter.setFeedMediaPlaybackInformation(media).get();

        List<MediaItem> children = childrenOf("continue_listening");

        assertEquals(queue.size(), children.size());
        assertEquals(String.valueOf(media.getId()), children.get(0).mediaId);
    }

    @Test
    public void testEpisodesSectionIsLimitedToTheRequestedPage() {
        List<MediaItem> firstPage = children("episodes", 0, 2);
        List<MediaItem> secondPage = children("episodes", 1, 2);

        assertEquals(2, firstPage.size());
        assertEquals(2, secondPage.size());
        assertNotEquals(firstPage.get(0).mediaId, secondPage.get(0).mediaId);
    }

    @Test
    public void testSearchFindsEpisodesByTitle() {
        FeedItem item = DBReader.getQueue().get(0);

        MediaBrowser mediaBrowser = browser();
        LibraryResult<Void> searchResult = Media3TestUtils.awaitFuture(
                Media3TestUtils.getOnMain(() -> mediaBrowser.search(item.getTitle(), null)));
        assertEquals(LibraryResult.RESULT_SUCCESS, searchResult.resultCode);

        List<MediaItem> results = Media3TestUtils.awaitFuture(Media3TestUtils.getOnMain(() ->
                mediaBrowser.getSearchResult(item.getTitle(), 0, PAGE_SIZE, null))).value;
        assertNotNull(results);
        assertFalse(results.isEmpty());
        List<String> foundIds = new ArrayList<>();
        for (MediaItem result : results) {
            foundIds.add(result.mediaId);
            assertTrue("Result " + result.mediaId + " is a playable episode",
                    result.mediaMetadata.isPlayable);
            assertNotNull("Result " + result.mediaId + " exists in the database",
                    DBReader.getFeedMedia(Long.parseLong(result.mediaId)));
        }
        assertTrue("The searched episode is among the results",
                foundIds.contains(String.valueOf(item.getMedia().getId())));
    }

    @Test
    public void testSearchWithoutResultsReturnsAnEmptyList() {
        MediaBrowser mediaBrowser = browser();
        ImmutableList<MediaItem> results = Media3TestUtils.awaitFuture(Media3TestUtils.getOnMain(() ->
                mediaBrowser.getSearchResult("no episode is called like this", 0, PAGE_SIZE, null))).value;

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testSectionItemsReportTheNumberOfEpisodes() {
        int downloaded = DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.DOWNLOADED));

        MediaItem downloads = item("downloads");

        assertEquals(context.getString(R.string.downloads_label), downloads.mediaMetadata.title.toString());
        assertEquals(context.getResources().getQuantityString(R.plurals.num_episodes, downloaded, downloaded),
                downloads.mediaMetadata.subtitle.toString());
    }

    @Test
    public void testItemOfAnUnknownFeedReportsAnError() {
        MediaBrowser mediaBrowser = browser();
        LibraryResult<MediaItem> result = Media3TestUtils.awaitFuture(Media3TestUtils.getOnMain(() ->
                mediaBrowser.getItem(MediaItemAdapter.MEDIA_ID_FEED_PREFIX + unknownFeedId())));

        assertEquals("Looking up a feed that does not exist fails",
                LibraryResult.RESULT_ERROR_UNKNOWN, result.resultCode);
    }

    @Test
    public void testItemOfAnEpisodeIsNotBrowsable() {
        FeedItem item = DBReader.getQueue().get(0);

        MediaBrowser mediaBrowser = browser();
        LibraryResult<MediaItem> result = Media3TestUtils.awaitFuture(Media3TestUtils.getOnMain(() ->
                mediaBrowser.getItem(String.valueOf(item.getMedia().getId()))));

        assertEquals("An episode is not a browsable item",
                LibraryResult.RESULT_ERROR_NOT_SUPPORTED, result.resultCode);
    }

    @Test
    public void testChildrenOfAnUnknownSectionReportAnError() {
        MediaBrowser mediaBrowser = browser();
        LibraryResult<ImmutableList<MediaItem>> result = Media3TestUtils.awaitFuture(
                Media3TestUtils.getOnMain(() -> mediaBrowser.getChildren("not_a_section", 0, PAGE_SIZE, null)));

        assertEquals("Browsing a section that does not exist fails",
                LibraryResult.RESULT_ERROR_UNKNOWN, result.resultCode);
    }

    @Test
    public void testBrowsedEpisodeCanBePlayed() {
        List<MediaItem> children = childrenOf("queue");
        MediaItem first = children.get(0);

        MediaBrowser mediaBrowser = browser();
        Media3TestUtils.runOnMain(() -> {
            mediaBrowser.setMediaItem(first);
            mediaBrowser.prepare();
            mediaBrowser.play();
        });

        awaitCurrentMedia(DBReader.getFeedMedia(Long.parseLong(first.mediaId)));
        awaitPlaying();
    }

    private long unknownFeedId() {
        long unused = 1;
        for (Feed feed : DBReader.getFeedList()) {
            unused = Math.max(unused, feed.getId() + 1);
        }
        return unused;
    }

    private List<MediaItem> childrenOf(String parentId) {
        return children(parentId, 0, PAGE_SIZE);
    }

    private List<MediaItem> children(String parentId, int page, int pageSize) {
        MediaBrowser mediaBrowser = browser();
        LibraryResult<ImmutableList<MediaItem>> result = Media3TestUtils.awaitFuture(
                Media3TestUtils.getOnMain(() -> mediaBrowser.getChildren(parentId, page, pageSize, null)));
        assertEquals("Browsing " + parentId + " failed", LibraryResult.RESULT_SUCCESS, result.resultCode);
        assertNotNull(result.value);
        return result.value;
    }

    private MediaItem item(String mediaId) {
        MediaBrowser mediaBrowser = browser();
        LibraryResult<MediaItem> result = Media3TestUtils.awaitFuture(
                Media3TestUtils.getOnMain(() -> mediaBrowser.getItem(mediaId)));
        assertEquals("Looking up " + mediaId + " failed", LibraryResult.RESULT_SUCCESS, result.resultCode);
        assertNotNull(result.value);
        return result.value;
    }
}
