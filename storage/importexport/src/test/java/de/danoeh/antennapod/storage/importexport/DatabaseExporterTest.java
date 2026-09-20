package de.danoeh.antennapod.storage.importexport;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class DatabaseExporterTest {
    private static final String MISSING_DATABASE_MESSAGE = "Cannot access current database";

    private Context context;
    private ContentResolver contentResolver;
    private Uri uri;

    @Before
    public void setUp() {
        context = mock(Context.class);
        contentResolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(contentResolver);
        when(context.getDatabasePath(anyString())).thenAnswer(invocation ->
                new File("/nonexistent-database-dir", invocation.<String>getArgument(0)));
        uri = Uri.parse("content://backup/export.db");
    }

    @Test
    public void exportToStreamFailsWhenDatabaseFileDoesNotExist() throws Exception {
        FileOutputStream out = new FileOutputStream(new FileDescriptor());

        IOException exception = assertThrows(IOException.class, () -> DatabaseExporter.exportToStream(out, context));

        assertEquals(MISSING_DATABASE_MESSAGE, exception.getMessage());
        verify(context).getDatabasePath(PodDBAdapter.DATABASE_NAME);
    }

    @Test
    public void exportToDocumentPropagatesExportFailureAndClosesFileDescriptor() throws Exception {
        ParcelFileDescriptor descriptor = mock(ParcelFileDescriptor.class);
        when(descriptor.getFileDescriptor()).thenReturn(new FileDescriptor());
        when(contentResolver.openFileDescriptor(uri, "wt")).thenReturn(descriptor);

        IOException exception = assertThrows(IOException.class, () -> DatabaseExporter.exportToDocument(uri, context));

        assertEquals(MISSING_DATABASE_MESSAGE, exception.getMessage());
        verify(descriptor).close();
    }

    @Test
    public void importBackupPropagatesFailureToOpenBackupFile() throws Exception {
        when(contentResolver.openInputStream(any(Uri.class))).thenThrow(new FileNotFoundException("no such backup"));

        FileNotFoundException exception = assertThrows(FileNotFoundException.class,
                () -> DatabaseExporter.importBackup(uri, context));

        assertEquals("no such backup", exception.getMessage());
    }
}
