package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.event.DownloadLogEvent;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.playback.PlaybackHistoryEvent;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DbWriterEpisodeStateTest extends DatabaseTestBase {

    private static List<String> titles(List<FeedItem> items) {
        List<String> result = new ArrayList<>();
        for (FeedItem item : items) {
            result.add(item.getTitle());
        }
        return result;
    }

    private static List<FeedItem> favorites() {
        return DBReader.getEpisodes(0, Integer.MAX_VALUE, new FeedItemFilter(FeedItemFilter.IS_FAVORITE),
                SortOrder.EPISODE_TITLE_A_Z);
    }

    @Test
    public void markItemsPlayedPersistsPlayStateAndNotifiesUnreadChange() {
        Feed feed = storeFeed("feed");
        FeedItem first = storeItem(feed, "first", 1000, FeedItem.NEW);
        FeedItem second = storeItem(feed, "second", 2000, FeedItem.UNPLAYED);
        events.clear();

        await(DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Arrays.asList(first, second)));

        assertEquals(FeedItem.PLAYED, DBReader.getFeedItem(first.getId()).getPlayState());
        assertEquals(FeedItem.PLAYED, DBReader.getFeedItem(second.getId()).getPlayState());
        FeedItemEvent event = events.eventsOfType(FeedItemEvent.class).get(0);
        assertTrue(event.unreadStatusChanged);
        assertEquals(2, event.items.size());
    }

    @Test
    public void markItemsPlayedKeepsPositionUnlessResetIsRequested() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        item.getMedia().setPosition(4000);
        await(DBWriter.setFeedMedia(item.getMedia()));

        await(DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(item)));
        assertEquals(4000, DBReader.getFeedItem(item.getId()).getMedia().getPosition());

        await(DBWriter.markItemsPlayed(FeedItem.UNPLAYED, true, Collections.singletonList(item)));
        assertEquals(0, DBReader.getFeedItem(item.getId()).getMedia().getPosition());
        assertEquals(FeedItem.UNPLAYED, DBReader.getFeedItem(item.getId()).getPlayState());
    }

    @Test
    public void markItemsPlayedResetsPositionOfMediaOnlyWhenItemHasMedia() {
        Feed feed = storeFeed("feed");
        FeedItem withoutMedia = storeItemWithoutMedia(feed, "text only", 1000);

        await(DBWriter.markItemsPlayed(FeedItem.PLAYED, true, Collections.singletonList(withoutMedia)));

        assertEquals(FeedItem.PLAYED, DBReader.getFeedItem(withoutMedia.getId()).getPlayState());
    }

    @Test
    public void removeFeedNewFlagOnlyAffectsNewItemsOfThatFeed() {
        Feed feed = storeFeed("feed");
        Feed other = storeFeed("other");
        FeedItem newItem = storeItem(feed, "new item", 1000, FeedItem.NEW);
        FeedItem playedItem = storeItem(feed, "played item", 2000, FeedItem.PLAYED);
        FeedItem otherNew = storeItem(other, "other new", 3000, FeedItem.NEW);

        await(DBWriter.removeFeedNewFlag(feed.getId()));

        assertEquals(FeedItem.UNPLAYED, DBReader.getFeedItem(newItem.getId()).getPlayState());
        assertEquals(FeedItem.PLAYED, DBReader.getFeedItem(playedItem.getId()).getPlayState());
        assertEquals(FeedItem.NEW, DBReader.getFeedItem(otherNew.getId()).getPlayState());
        assertTrue(events.eventsOfType(FeedItemEvent.class).get(0).unreadStatusChanged);
    }

    @Test
    public void removeAllNewFlagsAffectsEveryFeed() {
        Feed feed = storeFeed("feed");
        Feed other = storeFeed("other");
        FeedItem first = storeItem(feed, "first", 1000, FeedItem.NEW);
        FeedItem second = storeItem(other, "second", 2000, FeedItem.NEW);
        FeedItem played = storeItem(other, "played", 3000, FeedItem.PLAYED);

        await(DBWriter.removeAllNewFlags());

        assertEquals(FeedItem.UNPLAYED, DBReader.getFeedItem(first.getId()).getPlayState());
        assertEquals(FeedItem.UNPLAYED, DBReader.getFeedItem(second.getId()).getPlayState());
        assertEquals(FeedItem.PLAYED, DBReader.getFeedItem(played.getId()).getPlayState());
        assertEquals(0, DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.NEW)));
    }

    @Test
    public void addFavoriteItemsTagsAndPersistsItems() {
        Feed feed = storeFeed("feed");
        FeedItem liked = storeItem(feed, "liked");
        FeedItem ignored = storeItem(feed, "ignored");

        await(DBWriter.addFavoriteItems(Collections.singletonList(liked)));

        assertTrue(liked.isTagged(FeedItem.TAG_FAVORITE));
        assertEquals(Collections.singletonList("liked"), titles(favorites()));
        assertTrue(DBReader.getFeedItem(liked.getId()).isTagged(FeedItem.TAG_FAVORITE));
        assertFalse(DBReader.getFeedItem(ignored.getId()).isTagged(FeedItem.TAG_FAVORITE));
        assertEquals(liked.getId(), events.eventsOfType(FeedItemEvent.class).get(0).items.get(0).getId());
    }

    @Test
    public void addingFavoriteTwiceDoesNotDuplicateIt() {
        Feed feed = storeFeed("feed");
        FeedItem liked = storeItem(feed, "liked");

        await(DBWriter.addFavoriteItems(Collections.singletonList(liked)));
        await(DBWriter.addFavoriteItems(Collections.singletonList(liked)));

        assertEquals(1, favorites().size());
    }

    @Test
    public void removeFavoriteItemsUntagsAndRemovesOnlyGivenItems() {
        Feed feed = storeFeed("feed");
        FeedItem first = storeItem(feed, "first");
        FeedItem second = storeItem(feed, "second");
        await(DBWriter.addFavoriteItems(Arrays.asList(first, second)));

        await(DBWriter.removeFavoriteItems(Collections.singletonList(first)));

        assertFalse(first.isTagged(FeedItem.TAG_FAVORITE));
        assertEquals(Collections.singletonList("second"), titles(favorites()));
    }

    @Test
    public void toggleFavoriteItemSwitchesBetweenFavoriteAndNormal() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");

        await(DBWriter.toggleFavoriteItem(item));
        assertEquals(1, favorites().size());

        await(DBWriter.toggleFavoriteItem(item));
        assertTrue(favorites().isEmpty());
    }

    @Test
    public void favoriteWritesWithoutItemsChangeNothing() {
        await(DBWriter.addFavoriteItems(Collections.emptyList()));
        await(DBWriter.removeFavoriteItems(Collections.emptyList()));

        assertTrue(favorites().isEmpty());
    }

    @Test
    public void setFavoritesReplacesAllFavorites() {
        Feed feed = storeFeed("feed");
        FeedItem old = storeItem(feed, "old");
        FeedItem replacement = storeItem(feed, "replacement");
        await(DBWriter.addFavoriteItems(Collections.singletonList(old)));

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setFavorites(Collections.singletonList(replacement));
        adapter.close();

        assertEquals(Collections.singletonList("replacement"), titles(favorites()));
    }

    @Test
    public void addItemToPlaybackHistoryStampsCompletionDate() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        long before = System.currentTimeMillis();

        await(DBWriter.addItemToPlaybackHistory(item.getMedia()));

        long stored = DBReader.getFeedItem(item.getId()).getMedia().getLastPlayedTimeHistory().getTime();
        assertTrue(stored >= before);
        assertEquals(1, events.eventsOfType(PlaybackHistoryEvent.class).size());
    }

    @Test
    public void addItemToPlaybackHistoryStoresGivenDate() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");

        await(DBWriter.addItemToPlaybackHistory(item.getMedia(), new Date(123456)));

        assertEquals(123456, DBReader.getFeedItem(item.getId()).getMedia().getLastPlayedTimeHistory().getTime());
    }

    @Test
    public void deleteFromPlaybackHistoryRemovesItemFromHistoryFilter() {
        Feed feed = storeFeed("feed");
        FeedItem kept = storeItem(feed, "kept");
        FeedItem removed = storeItem(feed, "removed");
        await(DBWriter.addItemToPlaybackHistory(kept.getMedia(), new Date(5000)));
        await(DBWriter.addItemToPlaybackHistory(removed.getMedia(), new Date(6000)));
        FeedItemFilter history = new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY);
        assertEquals(2, DBReader.getTotalEpisodeCount(history));

        await(DBWriter.deleteFromPlaybackHistory(removed));

        List<FeedItem> remaining = DBReader.getEpisodes(0, 10, history, SortOrder.COMPLETION_DATE_NEW_OLD);
        assertEquals(Collections.singletonList("kept"), titles(remaining));
    }

    @Test
    public void clearPlaybackHistoryEmptiesHistory() {
        Feed feed = storeFeed("feed");
        await(DBWriter.addItemToPlaybackHistory(storeItem(feed, "a").getMedia(), new Date(5000)));
        await(DBWriter.addItemToPlaybackHistory(storeItem(feed, "b").getMedia(), new Date(6000)));
        events.clear();

        await(DBWriter.clearPlaybackHistory());

        assertEquals(0, DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY)));
        assertEquals(1, events.eventsOfType(PlaybackHistoryEvent.class).size());
    }

    @Test
    public void addDownloadStatusStoresResultForDownloadLog() {
        Date completed = new Date(987654);
        DownloadResult result = new DownloadResult(0, "episode", 12, FeedMedia.FEEDFILETYPE_FEEDMEDIA, false,
                DownloadError.ERROR_NOT_ENOUGH_SPACE, completed, "disk full");

        await(DBWriter.addDownloadStatus(result));

        assertNotEquals(0, result.getId());
        List<DownloadResult> log = DBReader.getDownloadLog();
        assertEquals(1, log.size());
        DownloadResult stored = log.get(0);
        assertEquals("episode", stored.getTitle());
        assertEquals(12, stored.getFeedfileId());
        assertEquals(FeedMedia.FEEDFILETYPE_FEEDMEDIA, stored.getFeedfileType());
        assertFalse(stored.isSuccessful());
        assertEquals(DownloadError.ERROR_NOT_ENOUGH_SPACE, stored.getReason());
        assertEquals("disk full", stored.getReasonDetailed());
        assertEquals(completed, stored.getCompletionDate());
        assertEquals(1, events.eventsOfType(DownloadLogEvent.class).size());
    }

    @Test
    public void addDownloadStatusUpdatesExistingRowWhenResultAlreadyHasAnId() {
        DownloadResult result = new DownloadResult("episode", 12, FeedMedia.FEEDFILETYPE_FEEDMEDIA, false,
                DownloadError.ERROR_IO_ERROR, "failed");
        await(DBWriter.addDownloadStatus(result));

        result.setSuccessful();
        await(DBWriter.addDownloadStatus(result));

        List<DownloadResult> log = DBReader.getDownloadLog();
        assertEquals(1, log.size());
        assertTrue(log.get(0).isSuccessful());
        assertEquals(DownloadError.SUCCESS, log.get(0).getReason());
    }

    @Test
    public void downloadLogIsSortedNewestFirstAndFilteredPerFeed() {
        await(DBWriter.addDownloadStatus(new DownloadResult(0, "old", 1, Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, new Date(1000), "")));
        await(DBWriter.addDownloadStatus(new DownloadResult(0, "new", 1, Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, new Date(3000), "")));
        await(DBWriter.addDownloadStatus(new DownloadResult(0, "other", 2, Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, new Date(2000), "")));
        await(DBWriter.addDownloadStatus(new DownloadResult(0, "media", 1, FeedMedia.FEEDFILETYPE_FEEDMEDIA, true,
                DownloadError.SUCCESS, new Date(4000), "")));

        List<DownloadResult> all = DBReader.getDownloadLog();
        List<DownloadResult> feedLog = DBReader.getFeedDownloadLog(1, 10);

        assertEquals(Arrays.asList("media", "new", "other", "old"),
                Arrays.asList(all.get(0).getTitle(), all.get(1).getTitle(), all.get(2).getTitle(),
                        all.get(3).getTitle()));
        assertEquals(Arrays.asList("new", "old"),
                Arrays.asList(feedLog.get(0).getTitle(), feedLog.get(1).getTitle()));
        assertEquals(1, DBReader.getFeedDownloadLog(1, 1).size());
    }

    @Test
    public void clearDownloadLogRemovesAllEntries() {
        await(DBWriter.addDownloadStatus(new DownloadResult("a", 1, Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, "")));
        events.clear();

        await(DBWriter.clearDownloadLog());

        assertTrue(DBReader.getDownloadLog().isEmpty());
        assertEquals(1, events.eventsOfType(DownloadLogEvent.class).size());
    }

    @Test
    public void clearOldDownloadLogKeepsOnlyEntriesFromTheLastWeek() {
        long now = System.currentTimeMillis();
        await(DBWriter.addDownloadStatus(new DownloadResult(0, "ancient", 1, Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, new Date(now - 8L * 24L * 3600L * 1000L), "")));
        await(DBWriter.addDownloadStatus(new DownloadResult(0, "recent", 1, Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, new Date(now - 24L * 3600L * 1000L), "")));

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.clearOldDownloadLog();
        adapter.close();

        List<DownloadResult> log = DBReader.getDownloadLog();
        assertEquals(1, log.size());
        assertEquals("recent", log.get(0).getTitle());
    }

    @Test
    public void setFeedItemPersistsItemAndMedia() {
        Feed feed = storeFeed("feed");
        FeedItem item = new FeedItem(0, "title", "guid", "https://example.com/link", new Date(4000),
                FeedItem.UNPLAYED, feed);
        item.setMedia(new FeedMedia(item, "https://example.com/media.mp3", 5000, "audio/mpeg"));
        feed.getItems().add(item);

        await(DBWriter.setFeedItem(item, true));

        FeedItem stored = DBReader.getFeedItem(item.getId());
        assertEquals("title", stored.getTitle());
        assertEquals("guid", stored.getItemIdentifier());
        assertEquals(4000, stored.getPubDate().getTime());
        assertEquals("https://example.com/media.mp3", stored.getMedia().getDownloadUrl());
        assertEquals(5000, stored.getMedia().getSize());
        FeedItemEvent event = events.eventsOfType(FeedItemEvent.class).get(0);
        assertTrue(event.unreadStatusChanged);
    }

    @Test
    public void setFeedItemStoresChaptersOfItem() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        item.setChapters(new ArrayList<>(Arrays.asList(new Chapter(0, "Intro", "https://example.com/intro", null),
                new Chapter(60000, "Main", null, "https://example.com/main.png"))));

        await(DBWriter.setFeedItem(item, false));

        FeedItem stored = DBReader.getFeedItem(item.getId());
        assertTrue(stored.hasChapters());
        List<Chapter> chapters = DBReader.loadChaptersOfFeedItem(stored);
        assertEquals(2, chapters.size());
        assertEquals("Intro", chapters.get(0).getTitle());
        assertEquals("https://example.com/intro", chapters.get(0).getLink());
        assertEquals(60000, chapters.get(1).getStart());
        assertEquals("https://example.com/main.png", chapters.get(1).getImageUrl());
    }

    @Test
    public void updatingItemWithChaptersUpdatesInsteadOfDuplicatingThem() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        item.setChapters(new ArrayList<>(Collections.singletonList(new Chapter(0, "Intro", null, null))));
        await(DBWriter.setFeedItem(item, false));

        item.getChapters().get(0).setTitle("Renamed");
        await(DBWriter.setFeedItem(item, false));

        List<Chapter> chapters = DBReader.loadChaptersOfFeedItem(DBReader.getFeedItem(item.getId()));
        assertEquals(1, chapters.size());
        assertEquals("Renamed", chapters.get(0).getTitle());
    }

    @Test
    public void loadChaptersOfItemWithoutChaptersClearsChapterList() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        item.setChapters(new ArrayList<>());

        assertNull(DBReader.loadChaptersOfFeedItem(item));
        assertNull(item.getChapters());
    }

    @Test
    public void setItemListStoresAllItemsAndNotifies() {
        Feed feed = storeFeed("feed");
        FeedItem first = new FeedItem(0, "first", "guid-1", "link", new Date(1000), FeedItem.UNPLAYED, feed);
        FeedItem second = new FeedItem(0, "second", "guid-2", "link", new Date(2000), FeedItem.UNPLAYED, feed);

        await(DBWriter.setItemList(Arrays.asList(first, second)));

        assertNotEquals(0, first.getId());
        assertNotEquals(0, second.getId());
        assertEquals(2, DBReader.getFeedEpisodeCount(feed.getId(), FeedItemFilter.unfiltered()));
        assertEquals(2, events.eventsOfType(FeedItemEvent.class).get(0).items.size());
    }

    @Test
    public void newItemWithoutPublicationDateGetsCurrentDate() {
        Feed feed = storeFeed("feed");
        FeedItem item = new FeedItem();
        item.setTitle("undated");
        item.setItemIdentifier("undated");
        item.setFeed(feed);
        long before = System.currentTimeMillis();

        await(DBWriter.setFeedItem(item, false));

        assertTrue(DBReader.getFeedItem(item.getId()).getPubDate().getTime() >= before);
    }

    @Test
    public void setFeedMediaOverwritesAllMediaAttributes() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        FeedMedia media = item.getMedia();
        media.setDuration(9000);
        media.setPosition(3000);
        media.setSize(777);
        media.setLastPlayedTimeStatistics(4444);
        media.setLastPlayedTimeHistory(new Date(5555));
        media.setHasEmbeddedPicture(true);

        await(DBWriter.setFeedMedia(media));

        FeedMedia stored = DBReader.getFeedItem(item.getId()).getMedia();
        assertEquals(9000, stored.getDuration());
        assertEquals(3000, stored.getPosition());
        assertEquals(777, stored.getSize());
        assertEquals(4444, stored.getLastPlayedTimeStatistics());
        assertEquals(5555, stored.getLastPlayedTimeHistory().getTime());
        assertTrue(stored.hasEmbeddedPicture());
    }

    @Test
    public void setMediaDownloadInformationStoresOnlyDownloadFields() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        FeedMedia media = item.getMedia();
        media.setLocalFileUrl("/storage/episode.mp3");
        media.setDownloaded(true, 8000);
        media.setSize(4321);
        media.setPosition(999);

        await(DBWriter.setMediaDownloadInformation(media));

        FeedMedia stored = DBReader.getFeedItem(item.getId()).getMedia();
        assertEquals("/storage/episode.mp3", stored.getLocalFileUrl());
        assertEquals(8000, stored.getDownloadDate());
        assertEquals(4321, stored.getSize());
        assertEquals(0, stored.getPosition());
    }

    @Test
    public void setMediaDownloadInformationIgnoresMediaThatWasNeverStored() {
        FeedMedia unsaved = new FeedMedia(null, "https://example.com/unsaved.mp3", 10, "audio/mpeg");

        await(DBWriter.setMediaDownloadInformation(unsaved));
        await(DBWriter.setFeedMediaPlaybackInformation(unsaved));

        assertEquals(0, unsaved.getId());
    }

    @Test
    public void setFeedMediaPlaybackInformationStoresPositionAndPlayedDuration() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        FeedMedia media = item.getMedia();
        media.setPosition(1234);
        media.setDuration(6000);
        media.setPlayedDuration(555);
        media.setLastPlayedTimeStatistics(777);
        media.setLastPlayedTimeHistory(new Date(888));
        media.setSize(1);

        await(DBWriter.setFeedMediaPlaybackInformation(media));

        FeedMedia stored = DBReader.getFeedItem(item.getId()).getMedia();
        assertEquals(1234, stored.getPosition());
        assertEquals(6000, stored.getDuration());
        assertEquals(555, stored.getPlayedDuration());
        assertEquals(777, stored.getLastPlayedTimeStatistics());
        assertEquals(888, stored.getLastPlayedTimeHistory().getTime());
        assertEquals(1000, stored.getSize());
    }

    @Test
    public void resetStatisticsClearsPlayedDurationOfAllMedia() {
        Feed feed = storeFeed("feed");
        FeedItem first = storeItem(feed, "first");
        FeedItem second = storeItem(feed, "second");
        first.getMedia().setPlayedDuration(5000);
        first.getMedia().setLastPlayedTimeHistory(new Date(1000));
        second.getMedia().setPlayedDuration(7000);
        second.getMedia().setLastPlayedTimeHistory(new Date(2000));
        await(DBWriter.setFeedMediaPlaybackInformation(first.getMedia()));
        await(DBWriter.setFeedMediaPlaybackInformation(second.getMedia()));

        await(DBWriter.resetStatistics());

        assertEquals(0, DBReader.getFeedItem(first.getId()).getMedia().getPlayedDuration());
        assertEquals(0, DBReader.getFeedItem(second.getId()).getMedia().getPlayedDuration());
        assertNotNull(DBReader.getFeedItem(first.getId()).getMedia());
    }
}
