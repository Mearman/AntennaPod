package de.danoeh.antennapod.storage.importexport;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class DatabaseExporterRoundTripTest {
    private static final byte[] CURRENT_CONTENT = "current database".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BACKUP_CONTENT = "backed up database".getBytes(StandardCharsets.UTF_8);
    private static final String TEMP_DB_NAME = PodDBAdapter.DATABASE_NAME + "_tmp";

    private Context context;
    private File currentDb;
    private File tempDb;
    private PodDBAdapter adapter;
    private SQLiteDatabase openedDatabase;
    private MockedStatic<PodDBAdapter> adapterStatic;
    private MockedStatic<SQLiteDatabase> sqliteStatic;

    @Before
    public void setUp() throws IOException {
        context = ApplicationProvider.getApplicationContext();
        currentDb = context.getDatabasePath(PodDBAdapter.DATABASE_NAME);
        tempDb = context.getDatabasePath(TEMP_DB_NAME);
        FileUtils.forceMkdirParent(currentDb);
        FileUtils.writeByteArrayToFile(currentDb, CURRENT_CONTENT);

        adapter = mock(PodDBAdapter.class);
        adapterStatic = mockStatic(PodDBAdapter.class);
        adapterStatic.when(PodDBAdapter::getInstance).thenReturn(adapter);

        openedDatabase = mock(SQLiteDatabase.class);
        when(openedDatabase.getVersion()).thenReturn(PodDBAdapter.VERSION);
        sqliteStatic = mockStatic(SQLiteDatabase.class);
        sqliteStatic.when(() -> SQLiteDatabase.openDatabase(anyString(), any(), anyInt()))
                .thenReturn(openedDatabase);
    }

    @After
    public void tearDown() {
        adapterStatic.close();
        sqliteStatic.close();
    }

    private File writeBackup(byte[] content) throws IOException {
        File backup = new File(context.getCacheDir(), "backup.db");
        FileUtils.writeByteArrayToFile(backup, content);
        return backup;
    }

    @Test
    public void exportToStreamCopiesDatabaseContentAfterCheckpointingAndRemovesTemporaryCopy() throws Exception {
        File target = new File(context.getCacheDir(), "exported.db");

        int bytesCopied;
        try (FileOutputStream out = new FileOutputStream(target)) {
            bytesCopied = DatabaseExporter.exportToStream(out, context);
        }

        assertEquals(CURRENT_CONTENT.length, bytesCopied);
        assertArrayEquals(CURRENT_CONTENT, FileUtils.readFileToByteArray(target));
        verify(adapter).walCheckpoint();
        assertFalse(tempDb.exists());
        assertArrayEquals(CURRENT_CONTENT, FileUtils.readFileToByteArray(currentDb));
    }

    @Test
    public void exportToStreamRejectsDatabaseWithUnexpectedVersionAndRemovesTemporaryCopy() throws Exception {
        when(openedDatabase.getVersion()).thenReturn(PodDBAdapter.VERSION - 1);
        File target = new File(context.getCacheDir(), "exported.db");

        try (FileOutputStream out = new FileOutputStream(target)) {
            IOException exception = assertThrows(IOException.class,
                    () -> DatabaseExporter.exportToStream(out, context));

            assertEquals("Database version mismatch. Expected: " + PodDBAdapter.VERSION
                    + ", found: " + (PodDBAdapter.VERSION - 1), exception.getMessage());
        }

        assertEquals(0, target.length());
        assertFalse(tempDb.exists());
    }

    private Context contextResolvingDocumentsTo(File target, long reportedSize) throws IOException {
        ParcelFileDescriptor descriptor = mock(ParcelFileDescriptor.class);
        when(descriptor.getFileDescriptor()).thenReturn(new FileOutputStream(target).getFD());
        when(descriptor.getStatSize()).thenReturn(reportedSize);
        ContentResolver resolver = mock(ContentResolver.class);
        when(resolver.openFileDescriptor(any(Uri.class), anyString())).thenReturn(descriptor);
        return new ContextWrapper(context) {
            @Override
            public ContentResolver getContentResolver() {
                return resolver;
            }
        };
    }

    @Test
    public void exportToDocumentWritesTheWholeDatabaseToTheTargetDocument() throws Exception {
        File target = new File(context.getCacheDir(), "document.db");
        Context documentContext = contextResolvingDocumentsTo(target, CURRENT_CONTENT.length);

        DatabaseExporter.exportToDocument(Uri.parse("content://backup/export.db"), documentContext);

        assertArrayEquals(CURRENT_CONTENT, FileUtils.readFileToByteArray(target));
    }

    @Test
    public void exportToDocumentFailsWhenTheDocumentSizeDiffersFromTheBytesCopied() throws Exception {
        File target = new File(context.getCacheDir(), "document.db");
        Context documentContext = contextResolvingDocumentsTo(target, CURRENT_CONTENT.length - 1);

        IOException exception = assertThrows(IOException.class,
                () -> DatabaseExporter.exportToDocument(Uri.parse("content://backup/export.db"), documentContext));

        assertTrue(exception.getMessage().startsWith("Unable to write entire database."));
    }

    @Test
    public void importBackupReplacesCurrentDatabaseWithBackupContent() throws Exception {
        File backup = writeBackup(BACKUP_CONTENT);

        DatabaseExporter.importBackup(Uri.fromFile(backup), context);

        assertArrayEquals(BACKUP_CONTENT, FileUtils.readFileToByteArray(currentDb));
        assertFalse(tempDb.exists());
        verify(openedDatabase).close();
    }

    @Test
    public void importBackupRemovesSidecarFilesOfTheReplacedDatabase() throws Exception {
        File[] sidecars = {
                new File(currentDb.getAbsolutePath() + "-wal"),
                new File(currentDb.getAbsolutePath() + "-shm"),
                new File(currentDb.getAbsolutePath() + "-journal")};
        for (File sidecar : sidecars) {
            FileUtils.writeStringToFile(sidecar, "leftover", StandardCharsets.UTF_8);
        }

        DatabaseExporter.importBackup(Uri.fromFile(writeBackup(BACKUP_CONTENT)), context);

        for (File sidecar : sidecars) {
            assertFalse(sidecar.getName() + " should be removed", sidecar.exists());
        }
        assertTrue(currentDb.exists());
    }

    @Test
    public void importBackupFromNewerDatabaseVersionIsRejectedAndKeepsCurrentDatabase() throws Exception {
        when(openedDatabase.getVersion()).thenReturn(PodDBAdapter.VERSION + 1);
        File backup = writeBackup(BACKUP_CONTENT);

        IOException exception = assertThrows(IOException.class,
                () -> DatabaseExporter.importBackup(Uri.fromFile(backup), context));

        assertEquals(context.getString(R.string.import_no_downgrade), exception.getMessage());
        assertArrayEquals(CURRENT_CONTENT, FileUtils.readFileToByteArray(currentDb));
    }

    @Test
    public void importBackupFromOlderDatabaseVersionIsAccepted() throws Exception {
        when(openedDatabase.getVersion()).thenReturn(PodDBAdapter.VERSION - 1);

        DatabaseExporter.importBackup(Uri.fromFile(writeBackup(BACKUP_CONTENT)), context);

        assertArrayEquals(BACKUP_CONTENT, FileUtils.readFileToByteArray(currentDb));
    }

    @Test
    public void exportedDatabaseCanBeImportedBack() throws Exception {
        File exported = new File(context.getCacheDir(), "exported.db");
        try (FileOutputStream out = new FileOutputStream(exported)) {
            DatabaseExporter.exportToStream(out, context);
        }
        FileUtils.writeByteArrayToFile(currentDb, "changed afterwards".getBytes(StandardCharsets.UTF_8));

        DatabaseExporter.importBackup(Uri.fromFile(exported), context);

        try (FileInputStream in = new FileInputStream(currentDb)) {
            ByteArrayOutputStream restored = new ByteArrayOutputStream();
            in.transferTo(restored);
            assertArrayEquals(CURRENT_CONTENT, restored.toByteArray());
        }
    }
}
