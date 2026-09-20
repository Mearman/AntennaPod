package de.test.antennapod.storage.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.test.antennapod.EspressoTestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Opens databases written by older versions of the app and checks that the data survives the upgrade.
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseUpgradeTest {
    private static final int FIRST_VERSION = 1;
    private static final int VERSION_BEFORE_FILTER_MIGRATION = 1040013;
    private static final long PLAYED_MEDIA_ID = 1;
    private static final long UNTOUCHED_MEDIA_ID = 2;
    private static final long QUEUED_MEDIA_ID = 3;

    private Context context;
    private File databaseFile;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EspressoTestUtils.clearDatabase();
        databaseFile = context.getDatabasePath(PodDBAdapter.DATABASE_NAME);
    }

    @After
    public void tearDown() {
        PodDBAdapter.tearDownTests();
        context.deleteDatabase(PodDBAdapter.DATABASE_NAME);
        EspressoTestUtils.clearDatabase();
    }

    private SQLiteDatabase createLegacyDatabase(int version) {
        PodDBAdapter.tearDownTests();
        context.deleteDatabase(PodDBAdapter.DATABASE_NAME);
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        legacy.setVersion(version);
        return legacy;
    }

    private void createVersionOneSchema(SQLiteDatabase legacy) {
        legacy.execSQL("CREATE TABLE Feeds (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, file_url TEXT,"
                + " download_url TEXT, downloaded INTEGER, link TEXT, description TEXT, payment_link TEXT,"
                + " last_update TEXT, language TEXT, author TEXT, image INTEGER)");
        legacy.execSQL("CREATE TABLE FeedItems (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT,"
                + " content_encoded TEXT, pubDate INTEGER, read INTEGER, link TEXT, description TEXT,"
                + " payment_link TEXT, media INTEGER, feed INTEGER, has_simple_chapters INTEGER)");
        legacy.execSQL("CREATE TABLE FeedMedia (id INTEGER PRIMARY KEY AUTOINCREMENT, duration INTEGER,"
                + " file_url TEXT, download_url TEXT, downloaded INTEGER, position INTEGER, filesize INTEGER,"
                + " mime_type TEXT)");
        legacy.execSQL("CREATE TABLE FeedImages (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT,"
                + " file_url TEXT, download_url TEXT, downloaded INTEGER)");
        legacy.execSQL("CREATE TABLE SimpleChapters (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT,"
                + " start INTEGER, feeditem INTEGER)");
        legacy.execSQL("CREATE TABLE DownloadLog (id INTEGER PRIMARY KEY AUTOINCREMENT, feedfile INTEGER,"
                + " feedfile_type INTEGER, reason INTEGER, successful INTEGER, completion_date INTEGER)");
        legacy.execSQL("CREATE TABLE Queue (id INTEGER PRIMARY KEY, feeditem INTEGER, feed INTEGER)");
    }

    private void fillVersionOneData(SQLiteDatabase legacy) {
        legacy.execSQL("INSERT INTO FeedImages (id, download_url) VALUES (1, 'http://example.com/cover.png')");
        legacy.execSQL("INSERT INTO Feeds (id, title, download_url, link, description, last_update, image)"
                + " VALUES (1, 'Legacy feed', 'http://example.com/legacy.xml', 'http://example.com/', 'Old',"
                + " 'Mon, 01 Jan 2018 10:00:00 GMT', 1)");
        legacy.execSQL("INSERT INTO FeedMedia (id, downloaded, position, download_url, mime_type, duration)"
                + " VALUES (" + PLAYED_MEDIA_ID + ", 0, 0, 'http://example.com/played.mp3', 'audio/mpeg', 1000)");
        legacy.execSQL("INSERT INTO FeedMedia (id, downloaded, position, download_url, mime_type, duration)"
                + " VALUES (" + UNTOUCHED_MEDIA_ID + ", 0, 0, 'http://example.com/untouched.mp3', 'audio/mpeg', 2000)");
        legacy.execSQL("INSERT INTO FeedMedia (id, downloaded, position, download_url, mime_type, duration)"
                + " VALUES (" + QUEUED_MEDIA_ID + ", 0, 0, 'http://example.com/queued.mp3', 'audio/mpeg', 3000)");
        legacy.execSQL("INSERT INTO FeedItems (id, title, pubDate, read, media, feed, description, content_encoded)"
                + " VALUES (1, 'Played', 1000, 1, " + PLAYED_MEDIA_ID + ", 1, 'short', 'a much longer description')");
        legacy.execSQL("INSERT INTO FeedItems (id, title, pubDate, read, media, feed, description)"
                + " VALUES (2, 'Untouched', 2000, 0, " + UNTOUCHED_MEDIA_ID + ", 1, 'plain')");
        legacy.execSQL("INSERT INTO FeedItems (id, title, pubDate, read, media, feed, description)"
                + " VALUES (3, 'Queued', 3000, 0, " + QUEUED_MEDIA_ID + ", 1, 'queued')");
        legacy.execSQL("INSERT INTO Queue (id, feeditem, feed) VALUES (0, 3, 1)");
        legacy.execSQL("INSERT INTO SimpleChapters (title, start, feeditem) VALUES ('Intro', 0, 2)");
        legacy.execSQL("INSERT INTO SimpleChapters (title, start, feeditem) VALUES ('Intro', 0, 2)");
        legacy.execSQL("INSERT INTO SimpleChapters (title, start, feeditem) VALUES ('Main', 60000, 2)");
        legacy.execSQL("INSERT INTO DownloadLog (feedfile, feedfile_type, reason, successful, completion_date)"
                + " VALUES (1, 0, 0, 1, 1000)");
    }

    private void openWithCurrentVersion() {
        assertNotNull(PodDBAdapter.getInstance());
    }

    private FeedItem itemTitled(String title) {
        for (FeedItem item : DBReader.getEpisodes(0, Integer.MAX_VALUE, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD)) {
            if (item.getTitle().equals(title)) {
                return item;
            }
        }
        throw new AssertionError("No episode " + title);
    }

    @Test
    public void databaseFromTheFirstVersionKeepsItsFeedsAndEpisodes() {
        SQLiteDatabase legacy = createLegacyDatabase(FIRST_VERSION);
        createVersionOneSchema(legacy);
        fillVersionOneData(legacy);
        legacy.close();

        openWithCurrentVersion();

        List<Feed> feeds = DBReader.getFeedList();
        assertEquals(1, feeds.size());
        assertEquals("Legacy feed", feeds.get(0).getTitle());
        assertEquals("http://example.com/legacy.xml", feeds.get(0).getDownloadUrl());
        assertEquals("http://example.com/cover.png", feeds.get(0).getImageUrl());
        assertEquals(Feed.STATE_SUBSCRIBED, feeds.get(0).getState());
        assertTrue(feeds.get(0).getPreferences().getKeepUpdated());
        assertEquals(3, DBReader.getTotalEpisodeCount(FeedItemFilter.unfiltered()));
    }

    @Test
    public void upgradeRemovesTimestampsThatCouldBeMistakenForEtags() {
        SQLiteDatabase legacy = createLegacyDatabase(FIRST_VERSION);
        createVersionOneSchema(legacy);
        fillVersionOneData(legacy);
        legacy.close();

        openWithCurrentVersion();

        assertNull(DBReader.getFeedList().get(0).getLastModified());
    }

    @Test
    public void upgradeMarksUntouchedEpisodesAsNewAndKeepsQueuedAndPlayedOnes() {
        SQLiteDatabase legacy = createLegacyDatabase(FIRST_VERSION);
        createVersionOneSchema(legacy);
        fillVersionOneData(legacy);
        legacy.close();

        openWithCurrentVersion();

        assertTrue(itemTitled("Untouched").isNew());
        assertFalse(itemTitled("Queued").isNew());
        assertTrue(itemTitled("Played").isPlayed());
        assertEquals(1, DBReader.getQueue().size());
        assertEquals("Queued", DBReader.getQueue().get(0).getTitle());
        assertTrue(itemTitled("Queued").isTagged(FeedItem.TAG_QUEUE));
    }

    @Test
    public void upgradeKeepsMediaAndPrefersTheLongerDescription() {
        SQLiteDatabase legacy = createLegacyDatabase(FIRST_VERSION);
        createVersionOneSchema(legacy);
        fillVersionOneData(legacy);
        legacy.close();

        openWithCurrentVersion();

        FeedItem played = itemTitled("Played");
        DBReader.loadDescriptionOfFeedItem(played);
        assertEquals("a much longer description", played.getDescription());
        assertEquals("http://example.com/played.mp3", played.getMedia().getDownloadUrl());
        assertEquals(1000, played.getMedia().getDuration());
        assertFalse(played.isDownloaded());
        assertEquals(3, DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.HAS_MEDIA)));
    }

    @Test
    public void upgradeRemovesDuplicateChapters() {
        SQLiteDatabase legacy = createLegacyDatabase(FIRST_VERSION);
        createVersionOneSchema(legacy);
        fillVersionOneData(legacy);
        legacy.close();

        openWithCurrentVersion();

        List<Chapter> chapters = DBReader.loadChaptersOfFeedItem(itemTitled("Untouched"));
        assertEquals(2, chapters.size());
        assertEquals("Intro", chapters.get(0).getTitle());
        assertEquals("Main", chapters.get(1).getTitle());
    }

    @Test
    public void upgradedDatabaseStillAcceptsNewData() throws Exception {
        SQLiteDatabase legacy = createLegacyDatabase(FIRST_VERSION);
        createVersionOneSchema(legacy);
        fillVersionOneData(legacy);
        legacy.close();
        openWithCurrentVersion();

        DBWriter.addFavoriteItems(Collections.singletonList(itemTitled("Played"))).get();
        DBWriter.addQueueItem(context, itemTitled("Untouched")).get();

        assertEquals(1, DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.IS_FAVORITE)));
        assertEquals(2, DBReader.getQueue().size());
        assertEquals(1, DBReader.getDownloadLog().size());
    }

    @Test
    public void upgradeInvertsSavedFeedFilters() {
        SQLiteDatabase legacy = createLegacyDatabase(VERSION_BEFORE_FILTER_MIGRATION);
        legacy.execSQL("CREATE TABLE Feeds (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, file_url TEXT,"
                + " download_url TEXT, downloaded INTEGER, link TEXT, description TEXT, payment_link TEXT,"
                + " last_update TEXT, language TEXT, author TEXT, image INTEGER, hide TEXT, type TEXT,"
                + " feed_identifier TEXT, auto_download INTEGER DEFAULT 1, username TEXT, password TEXT,"
                + " is_paged INTEGER DEFAULT 0, next_page_link TEXT, last_update_failed INTEGER DEFAULT 0,"
                + " auto_delete_action INTEGER DEFAULT 0)");
        legacy.execSQL("CREATE TABLE FeedItems (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, description TEXT,"
                + " content_encoded TEXT, image INTEGER)");
        legacy.execSQL("CREATE TABLE FeedImages (id INTEGER PRIMARY KEY AUTOINCREMENT, download_url TEXT)");
        legacy.execSQL("CREATE TABLE SimpleChapters (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT)");
        legacy.execSQL("CREATE TABLE Favorites (id INTEGER PRIMARY KEY, feeditem INTEGER, feed INTEGER)");
        legacy.execSQL("INSERT INTO Feeds (id, title, hide) VALUES (1, 'one', 'played,queued')");
        legacy.execSQL("INSERT INTO Feeds (id, title, hide) VALUES (2, 'two', 'unplayed,not_queued,not_downloaded')");
        legacy.close();

        openWithCurrentVersion();

        List<Feed> feeds = DBReader.getFeedList();
        assertEquals(2, feeds.size());
        for (Feed feed : feeds) {
            if (feed.getTitle().equals("one")) {
                assertEquals(Arrays.asList("unplayed", "not_queued"), feed.getItemFilter().getValuesList());
            } else {
                assertEquals(Arrays.asList("played", "queued", "downloaded"), feed.getItemFilter().getValuesList());
            }
        }
    }

    @Test
    public void corruptedDatabaseIsBackedUpAndReplacedByAnEmptyOne() throws IOException {
        PodDBAdapter.tearDownTests();
        context.deleteDatabase(PodDBAdapter.DATABASE_NAME);
        byte[] garbage = new byte[4096];
        Arrays.fill(garbage, (byte) 0x2a);
        FileUtils.writeByteArrayToFile(databaseFile, garbage);
        File backup = new File(context.getExternalFilesDir(null), "CorruptedDatabaseBackup.db");
        FileUtils.deleteQuietly(backup);

        openWithCurrentVersion();

        assertTrue(backup.exists());
        assertArrayEquals(garbage, FileUtils.readFileToByteArray(backup));
        assertTrue(DBReader.getFeedList().isEmpty());
        assertTrue(DBReader.getQueue().isEmpty());
        FileUtils.deleteQuietly(backup);
    }

    @Test
    public void databaseFileIsCreatedWithTheCurrentVersion() {
        PodDBAdapter.tearDownTests();
        context.deleteDatabase(PodDBAdapter.DATABASE_NAME);

        openWithCurrentVersion();

        try (SQLiteDatabase created = SQLiteDatabase.openDatabase(databaseFile.getPath(), null,
                SQLiteDatabase.OPEN_READONLY)) {
            assertEquals(PodDBAdapter.VERSION, created.getVersion());
        }
    }
}
