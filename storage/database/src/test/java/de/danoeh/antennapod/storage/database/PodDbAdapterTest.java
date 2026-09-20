package de.danoeh.antennapod.storage.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PodDbAdapterTest extends DatabaseTestBase {
    private static final int MAXIMUM_IN_OPERATOR_ARGUMENTS = 800;

    @Test
    public void newDatabaseContainsEveryTableAndIndex() {
        PodDBAdapter.tearDownTests();
        SQLiteDatabase inspected = SQLiteDatabase.openDatabase(
                context.getDatabasePath(PodDBAdapter.DATABASE_NAME).getPath(), null, SQLiteDatabase.OPEN_READONLY);
        Set<String> tables = new HashSet<>();
        Set<String> indexes = new HashSet<>();
        try (Cursor cursor = inspected.rawQuery("SELECT type, name FROM sqlite_master", null)) {
            while (cursor.moveToNext()) {
                if ("table".equals(cursor.getString(0))) {
                    tables.add(cursor.getString(1));
                } else if ("index".equals(cursor.getString(0))) {
                    indexes.add(cursor.getString(1));
                }
            }
        }
        int version = inspected.getVersion();
        inspected.close();

        assertTrue(tables.containsAll(Arrays.asList(PodDBAdapter.TABLE_NAME_FEEDS,
                PodDBAdapter.TABLE_NAME_FEED_ITEMS, PodDBAdapter.TABLE_NAME_FEED_MEDIA,
                PodDBAdapter.TABLE_NAME_DOWNLOAD_LOG, PodDBAdapter.TABLE_NAME_QUEUE,
                PodDBAdapter.TABLE_NAME_SIMPLECHAPTERS, PodDBAdapter.TABLE_NAME_FAVORITES)));
        assertTrue(indexes.contains("FeedItems_feed"));
        assertTrue(indexes.contains("FeedItems_pubDate"));
        assertTrue(indexes.contains("FeedItems_read"));
        assertTrue(indexes.contains("FeedMedia_feeditem"));
        assertTrue(indexes.contains("Queue_feeditem"));
        assertTrue(indexes.contains("SimpleChapters_feeditem"));
        assertEquals(PodDBAdapter.VERSION, version);
    }

    @Test
    public void deleteDatabaseRemovesEverythingButKeepsSchema() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        await(DBWriter.addQueueItem(context, item));
        await(DBWriter.addFavoriteItems(Collections.singletonList(item)));

        assertTrue(PodDBAdapter.deleteDatabase());

        assertTrue(DBReader.getFeedList().isEmpty());
        assertTrue(DBReader.getQueue().isEmpty());
        assertEquals(0, DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES)));
        storeFeed("after reset");
        assertEquals(1, DBReader.getFeedList().size());
    }

    @Test
    public void walCheckpointKeepsStoredDataIntact() {
        Feed feed = storeFeed("feed");
        storeItem(feed, "item");
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        adapter.walCheckpoint();
        adapter.close();

        assertEquals(1, DBReader.getFeed(feed.getId(), false, 0, 10).getItems().size());
    }

    @Test
    public void savingFeedKeepsItsItemFilter() {
        Feed feed = storeFeed("feed");
        await(DBWriter.setFeedItemsFilter(feed.getId(), Collections.singleton(FeedItemFilter.UNPLAYED)));
        Feed loaded = DBReader.getFeed(feed.getId(), false, 0, 10);
        assertEquals(Collections.singletonList(FeedItemFilter.UNPLAYED), loaded.getItemFilter().getValuesList());

        await(DBWriter.setCompleteFeed(loaded));

        Feed reloaded = DBReader.getFeed(feed.getId(), false, 0, 10);
        assertEquals(Collections.singletonList(FeedItemFilter.UNPLAYED), reloaded.getItemFilter().getValuesList());
    }

    @Test
    public void savingFeedWithoutItemFilterClearsPreviousFilter() {
        Feed feed = storeFeed("feed");
        await(DBWriter.setFeedItemsFilter(feed.getId(), Collections.singleton(FeedItemFilter.UNPLAYED)));
        Feed loaded = DBReader.getFeed(feed.getId(), false, 0, 10);
        Feed replacement = new Feed(feed.getDownloadUrl(), null, "feed");
        replacement.setId(loaded.getId());
        replacement.setItems(Collections.emptyList());

        await(DBWriter.setCompleteFeed(replacement));

        assertTrue(DBReader.getFeed(feed.getId(), false, 0, 10).getItemFilter().getValuesList().isEmpty());
    }

    @Test
    public void transcriptTypeAndUrlAreStoredWithEpisode() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        item.setTranscriptUrl("application/json", "https://example.com/transcript.json");

        await(DBWriter.setFeedItem(item, false));

        FeedItem stored = DBReader.getFeedItem(item.getId());
        assertEquals("https://example.com/transcript.json", stored.getTranscriptUrl());
        assertEquals("application/json", stored.getTranscriptType());
    }

    @Test
    public void episodeExtraUrlsAreStoredWithEpisode() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        item.setPodcastIndexChapterUrl("https://example.com/chapters.json");
        item.setSocialInteractUrl("https://example.com/social");
        item.setImageUrl("https://example.com/cover.png");
        item.setPaymentLink("https://example.com/pay");

        await(DBWriter.setFeedItem(item, false));

        FeedItem stored = DBReader.getFeedItem(item.getId());
        assertEquals("https://example.com/chapters.json", stored.getPodcastIndexChapterUrl());
        assertEquals("https://example.com/social", stored.getSocialInteractUrl());
        assertEquals("https://example.com/cover.png", stored.getImageUrl());
        assertEquals("https://example.com/pay", stored.getPaymentLink());
    }

    @Test
    public void removingSeveralEpisodesRemovesTheirMediaAndChaptersButNotOthers() {
        Feed feed = storeFeed("feed");
        FeedItem first = storeItem(feed, "first");
        FeedItem second = storeItem(feed, "second");
        FeedItem kept = storeItem(feed, "kept");
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        adapter.removeFeedItems(Arrays.asList(first, second));
        adapter.close();

        assertEquals(1, DBReader.getFeed(feed.getId(), false, 0, 10).getItems().size());
        assertNotNull(DBReader.getFeedItem(kept.getId()));
        assertEquals(kept.getMedia().getId(), DBReader.getFeedItem(kept.getId()).getMedia().getId());
    }

    @Test
    public void episodesLookupRejectsMoreIdsThanSqliteInOperatorSupports() {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        String[] ids = new String[MAXIMUM_IN_OPERATOR_ARGUMENTS + 1];
        Arrays.fill(ids, "1");

        assertThrows(IllegalArgumentException.class, () -> adapter.getFeedItemCursor(ids));
        adapter.close();
    }

    @Test
    public void episodesLookupAcceptsExactlyTheMaximumNumberOfIds() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        String[] ids = new String[MAXIMUM_IN_OPERATOR_ARGUMENTS];
        Arrays.fill(ids, String.valueOf(item.getId()));

        try (Cursor cursor = adapter.getFeedItemCursor(ids)) {
            assertEquals(1, cursor.getCount());
        }
        adapter.close();
    }

    @Test
    public void feedCountersCanBeRestrictedToGivenFeeds() {
        Feed first = storeFeed("first");
        Feed second = storeFeed("second");
        storeItem(first, "one", 1000, FeedItem.NEW);
        storeItem(first, "two", 2000, FeedItem.NEW);
        storeItem(second, "three", 3000, FeedItem.NEW);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        Map<Long, Integer> onlyFirst = adapter.getFeedCounters(FeedCounter.SHOW_NEW, first.getId());
        Map<Long, Integer> both = adapter.getFeedCounters(FeedCounter.SHOW_NEW, first.getId(), second.getId());
        Map<Long, Integer> all = adapter.getFeedCounters(FeedCounter.SHOW_NEW);
        adapter.close();

        assertEquals(Collections.singletonMap(first.getId(), 2), onlyFirst);
        assertEquals(2, both.size());
        assertEquals(1, (int) both.get(second.getId()));
        assertEquals(both, all);
    }

    @Test
    public void feedCountersAreEmptyWhenNothingMatches() {
        Feed feed = storeFeed("feed");
        storeItem(feed, "played", 1000, FeedItem.PLAYED);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        assertTrue(adapter.getFeedCounters(FeedCounter.SHOW_NEW).isEmpty());
        assertTrue(adapter.getFeedCounters(FeedCounter.SHOW_DOWNLOADED, feed.getId()).isEmpty());
        adapter.close();
    }

    @Test
    public void playedCountersCanBeRestrictedToGivenFeeds() {
        Feed first = storeFeed("first");
        Feed second = storeFeed("second");
        storeItem(first, "one", 1000, FeedItem.PLAYED);
        storeItem(second, "two", 2000, FeedItem.PLAYED);
        storeItem(second, "three", 3000, FeedItem.PLAYED);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        Map<Long, Integer> counters = adapter.getPlayedEpisodesCounters(second.getId());
        adapter.close();

        assertEquals(Collections.singletonMap(second.getId(), 2), counters);
    }

    @Test
    public void mostRecentItemDatesAreEmptyWithoutEpisodes() {
        storeFeed("feed");
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        assertTrue(adapter.getMostRecentItemDates().isEmpty());
        assertEquals(0, adapter.getQueueSize());
        adapter.close();
    }

    @Test
    public void mostRecentItemDatePicksNewestEpisodePerFeed() {
        Feed feed = storeFeed("feed");
        storeItem(feed, "older", 1000, FeedItem.UNPLAYED);
        storeItem(feed, "newer", 9000, FeedItem.UNPLAYED);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        Map<Long, Long> dates = adapter.getMostRecentItemDates();
        adapter.close();

        assertEquals(Collections.singletonMap(feed.getId(), 9000L), dates);
    }

    @Test
    public void lastPlayedHistoryOfMediaThatWasNeverStoredIsIgnored() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "item");
        FeedMedia unsaved = new FeedMedia(item, "https://example.com/other.mp3", 1, "audio/mpeg");
        unsaved.setLastPlayedTimeHistory(new Date(4000));
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();

        adapter.setFeedMediaLastPlayedTimeHistory(unsaved);
        adapter.close();

        assertEquals(0, unsaved.getId());
        assertEquals(0, DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY)));
    }

    @Test
    public void corruptDatabaseIsBackedUpAndDeleted() throws IOException {
        File databaseFile = context.getDatabasePath("corrupt-test.db");
        SQLiteDatabase corrupt = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        corrupt.execSQL("CREATE TABLE marker (id INTEGER)");

        new PodDBAdapter.PodDbErrorHandler().onCorruption(corrupt);

        File backup = new File(context.getExternalFilesDir(null), "CorruptedDatabaseBackup.db");
        assertTrue(backup.exists());
        assertFalse(databaseFile.exists());
        assertTrue(backup.delete());
    }

    @Test
    public void databaseFromOlderVersionIsMigratedWhenOpened() {
        PodDBAdapter.getInstance();
        PodDBAdapter.tearDownTests();
        File databaseFile = context.getDatabasePath(PodDBAdapter.DATABASE_NAME);
        SQLiteDatabase legacy = SQLiteDatabase.openDatabase(databaseFile.getPath(), null,
                SQLiteDatabase.OPEN_READWRITE);
        legacy.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " DROP COLUMN "
                + PodDBAdapter.KEY_SOCIAL_INTERACT_URL);
        legacy.setVersion(3070000);
        legacy.close();

        Feed feed = storeFeed("migrated");
        FeedItem item = storeItem(feed, "item");
        item.setSocialInteractUrl("https://example.com/social");
        await(DBWriter.setFeedItem(item, false));

        assertEquals("https://example.com/social", DBReader.getFeedItem(item.getId()).getSocialInteractUrl());
    }
}
