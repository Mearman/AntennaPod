package de.danoeh.antennapod.net.download.service.feed.local;

import android.content.ContentResolver;
import android.content.Context;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;

@RunWith(RobolectricTestRunner.class)
public class FastDocumentFileTest {
    private static final Uri FOLDER =
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APodcasts"
                    + "/document/primary%3APodcasts");

    private final Context context = Mockito.mock(Context.class);
    private final ContentResolver resolver = Mockito.mock(ContentResolver.class);
    private final MatrixCursor cursor = new MatrixCursor(new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE});

    @Before
    public void setUp() {
        Mockito.when(context.getContentResolver()).thenReturn(resolver);
    }

    private void provideCursor() {
        Mockito.when(resolver.query(any(Uri.class), any(String[].class), isNull(), isNull(), isNull()))
                .thenReturn(cursor);
    }

    @Test
    public void listsEveryChildOfTheFolderWithItsMetadata() {
        cursor.addRow(new Object[] {"primary:Podcasts/one.mp3", "one.mp3", 1234L, 1000L, "audio/mpeg"});
        cursor.addRow(new Object[] {"primary:Podcasts/feed.xml", "feed.xml", 56L, 2000L, "text/xml"});
        provideCursor();

        List<FastDocumentFile> files = FastDocumentFile.list(context, FOLDER);

        assertEquals(2, files.size());
        assertEquals("one.mp3", files.get(0).getName());
        assertEquals("audio/mpeg", files.get(0).getType());
        assertEquals(1234, files.get(0).getLength());
        assertEquals(1000, files.get(0).getLastModified());
        assertEquals(DocumentsContract.buildDocumentUriUsingTree(FOLDER, "primary:Podcasts/one.mp3"),
                files.get(0).getUri());
        assertEquals("feed.xml", files.get(1).getName());
        assertEquals(2000, files.get(1).getLastModified());
    }

    @Test
    public void queriesTheChildrenOfTheTreeFolder() {
        provideCursor();

        FastDocumentFile.list(context, FOLDER);

        Uri expected = DocumentsContract.buildChildDocumentsUriUsingTree(FOLDER,
                DocumentsContract.getDocumentId(FOLDER));
        Mockito.verify(resolver).query(Mockito.eq(expected), any(String[].class), isNull(), isNull(), isNull());
    }

    @Test
    public void emptyFolderHasNoFiles() {
        provideCursor();

        assertTrue(FastDocumentFile.list(context, FOLDER).isEmpty());
    }

    @Test
    public void unavailableProviderYieldsNoFiles() {
        Mockito.when(resolver.query(any(Uri.class), any(String[].class), isNull(), isNull(), isNull()))
                .thenReturn(null);

        assertTrue(FastDocumentFile.list(context, FOLDER).isEmpty());
    }

    @Test
    public void cursorIsClosedAfterListing() {
        cursor.addRow(new Object[] {"primary:Podcasts/one.mp3", "one.mp3", 1L, 1L, "audio/mpeg"});
        provideCursor();

        FastDocumentFile.list(context, FOLDER);

        assertTrue(cursor.isClosed());
    }
}
