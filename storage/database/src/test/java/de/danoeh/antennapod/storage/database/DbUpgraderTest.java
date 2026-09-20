package de.danoeh.antennapod.storage.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DbUpgraderTest {
    private static final String[] CURRENT_TABLES = {
        "Feeds", "FeedItems", "FeedMedia", "DownloadLog", "Queue", "SimpleChapters", "Favorites"
    };
    private static final int FIRST_VERSION = 1;
    private static final int VERSION_BEFORE_SOCIAL_INTERACT = 3070000;
    private static final int VERSION_BEFORE_FILTER_MIGRATION = 1040013;

    private SQLiteDatabase legacyDb;

    @Before
    public void openInMemoryDatabase() {
        legacyDb = SQLiteDatabase.create(null);
    }

    @After
    public void closeDatabase() {
        legacyDb.close();
    }

    private void createVersionOneSchema() {
        legacyDb.execSQL("CREATE TABLE Feeds (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, file_url TEXT,"
                + " download_url TEXT, downloaded INTEGER, link TEXT, description TEXT, payment_link TEXT,"
                + " last_update TEXT, language TEXT, author TEXT, image INTEGER)");
        legacyDb.execSQL("CREATE TABLE FeedItems (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT,"
                + " content_encoded TEXT, pubDate INTEGER, read INTEGER, link TEXT, description TEXT,"
                + " payment_link TEXT, media INTEGER, feed INTEGER, has_simple_chapters INTEGER)");
        legacyDb.execSQL("CREATE TABLE FeedMedia (id INTEGER PRIMARY KEY AUTOINCREMENT, duration INTEGER,"
                + " file_url TEXT, download_url TEXT, downloaded INTEGER, position INTEGER, filesize INTEGER,"
                + " mime_type TEXT)");
        legacyDb.execSQL("CREATE TABLE FeedImages (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT,"
                + " file_url TEXT, download_url TEXT, downloaded INTEGER)");
        legacyDb.execSQL("CREATE TABLE SimpleChapters (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT,"
                + " start INTEGER, feeditem INTEGER)");
        legacyDb.execSQL("CREATE TABLE DownloadLog (id INTEGER PRIMARY KEY AUTOINCREMENT, feedfile INTEGER,"
                + " feedfile_type INTEGER, reason INTEGER, successful INTEGER, completion_date INTEGER)");
        legacyDb.execSQL("CREATE TABLE Queue (id INTEGER PRIMARY KEY, feeditem INTEGER, feed INTEGER)");
    }

    private void createSchemaBeforeFilterMigration() {
        legacyDb.execSQL("CREATE TABLE Feeds (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, hide TEXT,"
                + " last_update TEXT, image INTEGER)");
        legacyDb.execSQL("CREATE TABLE FeedItems (id INTEGER PRIMARY KEY AUTOINCREMENT, description TEXT,"
                + " content_encoded TEXT, image INTEGER)");
        legacyDb.execSQL("CREATE TABLE FeedImages (id INTEGER PRIMARY KEY AUTOINCREMENT, download_url TEXT)");
        legacyDb.execSQL("CREATE TABLE SimpleChapters (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT)");
        legacyDb.execSQL("CREATE TABLE Favorites (id INTEGER PRIMARY KEY, feeditem INTEGER, feed INTEGER)");
    }

    private void upgradeFromVersionOne() {
        DBUpgrader.upgrade(legacyDb, FIRST_VERSION, PodDBAdapter.VERSION);
    }

    private String scalar(String sql) {
        try (Cursor cursor = legacyDb.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getString(0);
        }
    }

    private static Set<String> columnsOf(SQLiteDatabase db, String table) {
        Set<String> columns = new HashSet<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(nameIndex));
            }
        }
        return columns;
    }

    private Set<String> tableNames() {
        Set<String> names = new HashSet<>();
        try (Cursor cursor = legacyDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)) {
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
            }
        }
        return names;
    }

    @Test
    public void upgradeFromFirstVersionCreatesEveryColumnOfCurrentSchema() {
        PodDBAdapter.init(RuntimeEnvironment.getApplication());
        PodDBAdapter.getInstance();
        SQLiteDatabase currentDb = SQLiteDatabase.openDatabase(
                RuntimeEnvironment.getApplication().getDatabasePath(PodDBAdapter.DATABASE_NAME).getPath(), null,
                SQLiteDatabase.OPEN_READONLY);
        createVersionOneSchema();

        upgradeFromVersionOne();

        for (String table : CURRENT_TABLES) {
            Set<String> expected = columnsOf(currentDb, table);
            Set<String> actual = columnsOf(legacyDb, table);
            assertTrue(table + " is missing " + expected, actual.containsAll(expected));
        }
        currentDb.close();
        PodDBAdapter.tearDownTests();
    }

    @Test
    public void upgradeDropsLegacyFeedImagesTable() {
        createVersionOneSchema();

        upgradeFromVersionOne();

        assertFalse(tableNames().contains("FeedImages"));
        assertTrue(tableNames().contains("Favorites"));
    }

    @Test
    public void upgradeLinksMediaBackToItsFeedItem() {
        createVersionOneSchema();
        legacyDb.execSQL("INSERT INTO FeedMedia (id, downloaded, position) VALUES (7, 0, 0)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, media, feed) VALUES (3, 0, 7, 1)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, media, feed) VALUES (4, 0, 0, 1)");

        upgradeFromVersionOne();

        assertEquals("3", scalar("SELECT feeditem FROM FeedMedia WHERE id = 7"));
    }

    @Test
    public void upgradeRemovesDuplicateChapters() {
        createVersionOneSchema();
        legacyDb.execSQL("INSERT INTO SimpleChapters (title, start, feeditem) VALUES ('Intro', 0, 1)");
        legacyDb.execSQL("INSERT INTO SimpleChapters (title, start, feeditem) VALUES ('Intro', 0, 1)");
        legacyDb.execSQL("INSERT INTO SimpleChapters (title, start, feeditem) VALUES ('Intro', 0, 1)");
        legacyDb.execSQL("INSERT INTO SimpleChapters (title, start, feeditem) VALUES ('Outro', 900, 1)");
        legacyDb.execSQL("INSERT INTO SimpleChapters (title, start, feeditem) VALUES ('Intro', 0, 2)");

        upgradeFromVersionOne();

        assertEquals("3", scalar("SELECT COUNT(*) FROM SimpleChapters"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM SimpleChapters WHERE title = 'Intro' AND feeditem = 1"));
    }

    @Test
    public void upgradeMarksUntouchedUnplayedEpisodesAsNew() {
        createVersionOneSchema();
        legacyDb.execSQL("INSERT INTO FeedMedia (id, downloaded, position) VALUES (1, 0, 0)");
        legacyDb.execSQL("INSERT INTO FeedMedia (id, downloaded, position) VALUES (2, 0, 0)");
        legacyDb.execSQL("INSERT INTO FeedMedia (id, downloaded, position) VALUES (3, 0, 500)");
        legacyDb.execSQL("INSERT INTO FeedMedia (id, downloaded, position) VALUES (4, 0, 0)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, media, feed) VALUES (1, 0, 1, 1)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, media, feed) VALUES (2, 0, 2, 1)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, media, feed) VALUES (3, 0, 3, 1)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, media, feed) VALUES (4, 1, 4, 1)");
        legacyDb.execSQL("INSERT INTO Queue (id, feeditem, feed) VALUES (0, 2, 1)");

        upgradeFromVersionOne();

        assertEquals("-1", scalar("SELECT read FROM FeedItems WHERE id = 1"));
        assertEquals("0", scalar("SELECT read FROM FeedItems WHERE id = 2"));
        assertEquals("0", scalar("SELECT read FROM FeedItems WHERE id = 3"));
        assertEquals("1", scalar("SELECT read FROM FeedItems WHERE id = 4"));
    }

    @Test
    public void upgradeDisablesAutoDownloadForEpisodesTheUserAlreadyHandled() {
        createVersionOneSchema();
        legacyDb.execSQL("INSERT INTO FeedMedia (id, downloaded, position) VALUES (1, 0, 0)");
        legacyDb.execSQL("INSERT INTO FeedMedia (id, downloaded, position) VALUES (2, 0, 0)");
        legacyDb.execSQL("INSERT INTO FeedMedia (id, downloaded, position) VALUES (3, 0, 0)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, media, feed) VALUES (1, 1, 1, 1)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, media, feed) VALUES (2, 1, 2, 1)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, media, feed) VALUES (3, 0, 3, 1)");
        legacyDb.execSQL("INSERT INTO Feeds (id, title) VALUES (1, 'feed')");
        legacyDb.execSQL("INSERT INTO Queue (id, feeditem, feed) VALUES (0, 2, 1)");

        upgradeFromVersionOne();

        assertEquals("0", scalar("SELECT auto_download FROM FeedItems WHERE id = 1"));
        assertEquals("1", scalar("SELECT auto_download FROM FeedItems WHERE id = 2"));
        assertEquals("1", scalar("SELECT auto_download FROM FeedItems WHERE id = 3"));
    }

    @Test
    public void upgradeMarksUndownloadedMediaAsWithoutEmbeddedPicture() {
        createVersionOneSchema();
        legacyDb.execSQL("INSERT INTO FeedMedia (id, downloaded, position) VALUES (1, 0, 0)");

        upgradeFromVersionOne();

        assertEquals("0", scalar("SELECT has_embedded_picture FROM FeedMedia WHERE id = 1"));
    }

    @Test
    public void upgradeInvertsSavedFeedFilters() {
        createSchemaBeforeFilterMigration();
        legacyDb.execSQL("INSERT INTO Feeds (id, title, hide) VALUES (1, 'one', 'played,queued')");
        legacyDb.execSQL("INSERT INTO Feeds (id, title, hide) VALUES (2, 'two',"
                + " 'unplayed,not_queued,not_downloaded')");
        legacyDb.execSQL("INSERT INTO Feeds (id, title, hide) VALUES (3, 'three', 'paused,downloaded')");
        legacyDb.execSQL("INSERT INTO Feeds (id, title, hide) VALUES (4, 'four', NULL)");

        DBUpgrader.upgrade(legacyDb, VERSION_BEFORE_FILTER_MIGRATION, PodDBAdapter.VERSION);

        assertEquals("unplayed,not_queued", scalar("SELECT hide FROM Feeds WHERE id = 1"));
        assertEquals("played,queued,downloaded", scalar("SELECT hide FROM Feeds WHERE id = 2"));
        assertEquals("unplayed,not_downloaded", scalar("SELECT hide FROM Feeds WHERE id = 3"));
        assertNull(scalar("SELECT hide FROM Feeds WHERE id = 4"));
    }

    @Test
    public void upgradeClearsTimestampsThatCouldBeMistakenForEtags() {
        createVersionOneSchema();
        legacyDb.execSQL("INSERT INTO Feeds (id, title, last_update) VALUES (1, 'feed', 'Mon, 01 Jan 2018')");

        upgradeFromVersionOne();

        assertNull(scalar("SELECT last_update FROM Feeds WHERE id = 1"));
    }

    @Test
    public void upgradeMovesLegacyImageRowsIntoImageUrlColumns() {
        createSchemaBeforeFilterMigration();
        legacyDb.execSQL("INSERT INTO FeedImages (id, download_url) VALUES (1, 'https://example.com/feed.png')");
        legacyDb.execSQL("INSERT INTO FeedImages (id, download_url) VALUES (2, 'https://example.com/item.png')");
        legacyDb.execSQL("INSERT INTO Feeds (id, title, image) VALUES (1, 'feed', 1)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, image) VALUES (1, 2)");

        DBUpgrader.upgrade(legacyDb, VERSION_BEFORE_FILTER_MIGRATION, PodDBAdapter.VERSION);

        assertEquals("https://example.com/feed.png", scalar("SELECT image_url FROM Feeds WHERE id = 1"));
        assertEquals("https://example.com/item.png", scalar("SELECT image_url FROM FeedItems WHERE id = 1"));
        assertFalse(tableNames().contains("FeedImages"));
    }

    @Test
    public void upgradePrefersLongerContentEncodedAsDescription() {
        createVersionOneSchema();
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, feed, description, content_encoded)"
                + " VALUES (1, 0, 1, 'short', 'a much longer description')");
        legacyDb.execSQL("INSERT INTO FeedItems (id, read, feed, description, content_encoded)"
                + " VALUES (2, 0, 1, 'the description is longer', 'short')");

        upgradeFromVersionOne();

        assertEquals("a much longer description", scalar("SELECT description FROM FeedItems WHERE id = 1"));
        assertEquals("the description is longer", scalar("SELECT description FROM FeedItems WHERE id = 2"));
        assertNull(scalar("SELECT content_encoded FROM FeedItems WHERE id = 1"));
    }

    @Test
    public void upgradeGivesFeedsSubscribedStateAndDefaults() {
        createVersionOneSchema();
        legacyDb.execSQL("INSERT INTO Feeds (id, title) VALUES (1, 'feed')");

        upgradeFromVersionOne();

        assertEquals("0", scalar("SELECT state FROM Feeds WHERE id = 1"));
        assertEquals("1", scalar("SELECT keep_updated FROM Feeds WHERE id = 1"));
        assertEquals("-1", scalar("SELECT minimal_duration_filter FROM Feeds WHERE id = 1"));
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL,
                Float.parseFloat(scalar("SELECT feed_playback_speed FROM Feeds WHERE id = 1")), 0f);
    }

    @Test
    public void upgradeFromLastReleasedVersionDropsFavoritesOfDeletedEpisodes() {
        legacyDb.execSQL("CREATE TABLE FeedItems (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT)");
        legacyDb.execSQL("CREATE TABLE Favorites (id INTEGER PRIMARY KEY, feeditem INTEGER, feed INTEGER)");
        legacyDb.execSQL("INSERT INTO FeedItems (id, title) VALUES (1, 'kept')");
        legacyDb.execSQL("INSERT INTO Favorites (id, feeditem, feed) VALUES (0, 1, 1)");
        legacyDb.execSQL("INSERT INTO Favorites (id, feeditem, feed) VALUES (1, 99, 1)");

        DBUpgrader.upgrade(legacyDb, VERSION_BEFORE_SOCIAL_INTERACT, PodDBAdapter.VERSION);

        assertEquals("1", scalar("SELECT COUNT(*) FROM Favorites"));
        assertEquals("1", scalar("SELECT feeditem FROM Favorites"));
        assertTrue(columnsOf(legacyDb, "FeedItems").contains(PodDBAdapter.KEY_SOCIAL_INTERACT_URL));
    }

    @Test
    public void upgradeWithCurrentVersionLeavesSchemaUntouched() {
        legacyDb.execSQL("CREATE TABLE FeedItems (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT)");
        Set<String> before = columnsOf(legacyDb, "FeedItems");

        DBUpgrader.upgrade(legacyDb, PodDBAdapter.VERSION, PodDBAdapter.VERSION);

        assertEquals(before, columnsOf(legacyDb, "FeedItems"));
    }
}
