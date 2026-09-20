package de.danoeh.antennapod.storage.importexport;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DatabaseBackupPipelineTest extends ImportExportPipelineTestBase {

    private File backupFile() {
        return new File(context.getCacheDir(), "backup.db");
    }

    private int exportToFile(File target) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(target)) {
            return DatabaseExporter.exportToStream(stream, context);
        }
    }

    private List<FeedItem> items(Feed feed) {
        return DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD, 0,
                Integer.MAX_VALUE);
    }

    @Test
    public void exportedDatabaseRestoresSubscriptionsAndEpisodeStateIntoAnEmptyDatabase() throws Exception {
        Feed feed = subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one",
                "https://example.com/1.png", "Alpha", "Beta");
        FeedItem played = items(feed).get(0);
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, List.of(played));
        DBWriter.tearDownTests();
        File backup = backupFile();
        int bytesWritten = exportToFile(backup);
        assertTrue(bytesWritten > 0);
        assertEquals(bytesWritten, backup.length());

        resetDatabase();
        assertTrue(storedFeeds().isEmpty());
        DatabaseExporter.importBackup(Uri.fromFile(backup), context);
        reopenDatabase();

        List<Feed> restored = storedFeeds();
        assertEquals(1, restored.size());
        assertEquals("First podcast", restored.get(0).getTitle());
        assertEquals("https://example.com/one.xml", restored.get(0).getDownloadUrl());
        List<FeedItem> restoredItems = items(restored.get(0));
        assertEquals(2, restoredItems.size());
        assertEquals(played.getTitle(), restoredItems.get(0).getTitle());
        assertTrue(restoredItems.get(0).isPlayed());
        assertFalse(restoredItems.get(1).isPlayed());
    }

    @Test
    public void importReplacesTheCurrentDatabaseInsteadOfMergingWithIt() throws Exception {
        subscribe("https://example.com/backed-up.xml", "Backed up", "https://example.com/b", "https://example.com/b.png");
        File backup = backupFile();
        exportToFile(backup);
        subscribe("https://example.com/later.xml", "Added later", "https://example.com/l", "https://example.com/l.png");
        assertEquals(2, storedFeeds().size());

        DatabaseExporter.importBackup(Uri.fromFile(backup), context);
        reopenDatabase();

        List<Feed> restored = storedFeeds();
        assertEquals(1, restored.size());
        assertEquals("Backed up", restored.get(0).getTitle());
    }

    @Test
    public void exportLeavesTheLiveDatabaseAndNoTemporaryCopyBehind() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one", "https://example.com/1.png");

        exportToFile(backupFile());

        assertEquals(1, storedFeeds().size());
        assertFalse(context.getDatabasePath(PodDBAdapter.DATABASE_NAME + "_tmp").exists());
    }

    @Test
    public void exportToADocumentThatCannotBeOpenedFailsWithoutTouchingTheDatabase() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one", "https://example.com/1.png");
        File unreachable = new File(new File(context.getCacheDir(), "missing-directory"), "backup.db");

        assertThrows(IOException.class, () -> DatabaseExporter.exportToDocument(Uri.fromFile(unreachable), context));

        assertFalse(unreachable.exists());
        assertEquals(1, storedFeeds().size());
    }

    @Test
    public void exportFailsWhenThereIsNoDatabaseFile() throws Exception {
        assertTrue(context.getDatabasePath(PodDBAdapter.DATABASE_NAME).delete());

        IOException exception = assertThrows(IOException.class, () -> exportToFile(backupFile()));

        assertEquals("Cannot access current database", exception.getMessage());
    }

    @Test
    public void importOfABackupFromANewerVersionIsRejectedAndKeepsTheCurrentDatabase() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one", "https://example.com/1.png");
        File newer = backupFile();
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(newer, null);
        database.setVersion(PodDBAdapter.VERSION + 1);
        database.close();

        IOException exception = assertThrows(IOException.class,
                () -> DatabaseExporter.importBackup(Uri.fromFile(newer), context));

        assertEquals(context.getString(R.string.import_no_downgrade), exception.getMessage());
        assertEquals(1, storedFeeds().size());
    }

    @Test
    public void importOfAFileThatIsNotADatabaseFailsAndKeepsTheCurrentDatabase() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one", "https://example.com/1.png");
        File garbage = backupFile();
        Files.write(garbage.toPath(), "this is not an sqlite database, just text".getBytes(StandardCharsets.UTF_8));

        assertThrows(SQLiteException.class, () -> DatabaseExporter.importBackup(Uri.fromFile(garbage), context));

        assertEquals(1, storedFeeds().size());
    }
}
