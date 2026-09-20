package de.danoeh.antennapod.storage.importexport;

import android.app.backup.BackupDataInputStream;
import android.app.backup.BackupDataOutput;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class OpmlBackupHelperTest {
    private static final String ENTITY_KEY = "antennapod-feeds.opml";
    private static final int MD5_LENGTH = 16;

    private static class RecordingFeedUpdateManager extends FeedUpdateManager {
        private final List<Context> runOnceContexts = new ArrayList<>();

        @Override
        public void restartUpdateAlarm(Context context, boolean replace) {
        }

        @Override
        public void runOnce(Context context) {
            runOnceContexts.add(context);
        }

        @Override
        public void runOnce(Context context, Feed feed) {
        }

        @Override
        public void runOnce(Context context, Feed feed, boolean nextPage) {
        }

        @Override
        public void runOnceOrAsk(Context context) {
        }

        @Override
        public void runOnceOrAsk(Context context, Feed feed) {
        }
    }

    private Context context;
    private OpmlBackupAgent.OpmlBackupHelper helper;
    private MockedStatic<DBReader> dbReader;
    private MockedStatic<FeedDatabaseWriter> feedDatabaseWriter;
    private RecordingFeedUpdateManager feedUpdateManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        helper = new OpmlBackupAgent.OpmlBackupHelper(context);
        dbReader = mockStatic(DBReader.class);
        feedDatabaseWriter = mockStatic(FeedDatabaseWriter.class);
        feedUpdateManager = new RecordingFeedUpdateManager();
        FeedUpdateManager.setInstance(feedUpdateManager);
    }

    @After
    public void tearDown() {
        dbReader.close();
        feedDatabaseWriter.close();
        FeedUpdateManager.setInstance(null);
    }

    private Feed createFeed(String title, String downloadUrl) {
        return new Feed(0, null, title, "https://example.com", null, null, null, null, null, null, null, null,
                downloadUrl, 0);
    }

    private byte[] md5(byte[] content) throws Exception {
        return MessageDigest.getInstance("MD5").digest(content);
    }

    private byte[] readAll(ParcelFileDescriptor readSide) throws IOException {
        try (FileInputStream in = new FileInputStream(readSide.getFileDescriptor())) {
            return in.readAllBytes();
        }
    }

    private BackupDataInputStream createRestoreStream(String key, String content) throws IOException {
        ByteArrayInputStream source = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        BackupDataInputStream data = mock(BackupDataInputStream.class);
        when(data.getKey()).thenReturn(key);
        when(data.read()).thenAnswer(invocation -> source.read());
        when(data.read(any(byte[].class))).thenAnswer(
                invocation -> source.read(invocation.<byte[]>getArgument(0)));
        when(data.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(invocation -> source.read(
                invocation.<byte[]>getArgument(0), invocation.<Integer>getArgument(1),
                invocation.<Integer>getArgument(2)));
        return data;
    }

    private byte[] captureBackedUpBytes(BackupDataOutput data) throws IOException {
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(data).writeEntityData(captor.capture(), sizeCaptor.capture());
        assertEquals(captor.getValue().length, (int) sizeCaptor.getValue());
        return captor.getValue();
    }

    @Test
    public void backupWritesSubscribedFeedsAsOpmlEntity() throws Exception {
        dbReader.when(DBReader::getFeedList).thenReturn(Arrays.asList(
                createFeed("Feed One", "https://example.com/one.xml"),
                createFeed("Feed Two", "https://example.com/two.xml")));
        BackupDataOutput data = mock(BackupDataOutput.class);
        ParcelFileDescriptor[] newState = ParcelFileDescriptor.createPipe();

        helper.performBackup(null, data, newState[1]);

        byte[] backedUp = captureBackedUpBytes(data);
        verify(data).writeEntityHeader(ENTITY_KEY, backedUp.length);
        List<OpmlElement> elements = new OpmlReader().readDocument(
                new StringReader(new String(backedUp, StandardCharsets.UTF_8)));
        assertEquals(2, elements.size());
        assertEquals("https://example.com/one.xml", elements.get(0).getXmlUrl());
        assertEquals("Feed Two", elements.get(1).getText());
    }

    @Test
    public void backupRecordsChecksumOfBackedUpDocumentAsNewState() throws Exception {
        dbReader.when(DBReader::getFeedList).thenReturn(
                Collections.singletonList(createFeed("Feed One", "https://example.com/one.xml")));
        BackupDataOutput data = mock(BackupDataOutput.class);
        ParcelFileDescriptor[] newState = ParcelFileDescriptor.createPipe();

        helper.performBackup(null, data, newState[1]);

        byte[] expectedChecksum = md5(captureBackedUpBytes(data));
        byte[] state = readAll(newState[0]);
        assertEquals(MD5_LENGTH, state[0]);
        assertArrayEquals(expectedChecksum, Arrays.copyOfRange(state, 1, state.length));
    }

    @Test
    public void backupProceedsWhenOldStateHoldsDifferentChecksum() throws Exception {
        dbReader.when(DBReader::getFeedList).thenReturn(
                Collections.singletonList(createFeed("Feed One", "https://example.com/one.xml")));
        BackupDataOutput data = mock(BackupDataOutput.class);
        ParcelFileDescriptor[] oldState = ParcelFileDescriptor.createPipe();
        try (FileOutputStream out = new FileOutputStream(oldState[1].getFileDescriptor())) {
            out.write(MD5_LENGTH);
            out.write(new byte[MD5_LENGTH]);
        }
        ParcelFileDescriptor[] newState = ParcelFileDescriptor.createPipe();

        helper.performBackup(oldState[0], data, newState[1]);

        byte[] backedUp = captureBackedUpBytes(data);
        byte[] state = readAll(newState[0]);
        assertNotEquals(0, state.length);
        assertArrayEquals(md5(backedUp), Arrays.copyOfRange(state, 1, state.length));
    }

    @Test
    public void backupProceedsWhenOldStateIsEmpty() throws Exception {
        dbReader.when(DBReader::getFeedList).thenReturn(Collections.emptyList());
        BackupDataOutput data = mock(BackupDataOutput.class);
        ParcelFileDescriptor[] oldState = ParcelFileDescriptor.createPipe();
        oldState[1].close();
        ParcelFileDescriptor[] newState = ParcelFileDescriptor.createPipe();

        helper.performBackup(oldState[0], data, newState[1]);

        byte[] backedUp = captureBackedUpBytes(data);
        assertEquals(0, new OpmlReader().readDocument(
                new StringReader(new String(backedUp, StandardCharsets.UTF_8))).size());
    }

    @Test
    public void backupFailureWhileWritingEntityDoesNotPropagate() throws Exception {
        dbReader.when(DBReader::getFeedList).thenReturn(
                Collections.singletonList(createFeed("Feed One", "https://example.com/one.xml")));
        BackupDataOutput data = mock(BackupDataOutput.class);
        doThrow(new IOException("disk full")).when(data).writeEntityHeader(anyString(), anyInt());
        ParcelFileDescriptor[] newState = ParcelFileDescriptor.createPipe();

        helper.performBackup(null, data, newState[1]);

        verify(data, never()).writeEntityData(any(byte[].class), anyInt());
    }

    @Test
    public void restoreIgnoresEntitiesWithUnknownKey() throws Exception {
        BackupDataInputStream data = createRestoreStream("other-entity", "<opml/>");

        helper.restoreEntity(data);

        feedDatabaseWriter.verifyNoInteractions();
        assertTrue(feedUpdateManager.runOnceContexts.isEmpty());
    }

    @Test
    public void restoreAddsEveryOpmlFeedAndTriggersFeedUpdate() throws Exception {
        String opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><opml version=\"2.0\"><body>"
                + "<outline text=\"Feed One\" xmlUrl=\"https://example.com/one.xml\"/>"
                + "<outline text=\"Feed Two\" xmlUrl=\"https://example.com/two.xml\"/>"
                + "</body></opml>";

        helper.restoreEntity(createRestoreStream(ENTITY_KEY, opml));

        ArgumentCaptor<Feed> feeds = ArgumentCaptor.forClass(Feed.class);
        feedDatabaseWriter.verify(() -> FeedDatabaseWriter.updateFeed(eq(context), feeds.capture(), eq(false)),
                times(2));
        assertEquals("https://example.com/one.xml", feeds.getAllValues().get(0).getDownloadUrl());
        assertEquals("Feed One", feeds.getAllValues().get(0).getTitle());
        assertEquals("https://example.com/two.xml", feeds.getAllValues().get(1).getDownloadUrl());
        assertTrue(feeds.getAllValues().get(0).getItems().isEmpty());
        assertEquals(Collections.singletonList(context), feedUpdateManager.runOnceContexts);
    }

    @Test
    public void restoreOfMalformedDocumentAddsNoFeedsAndSkipsFeedUpdate() throws Exception {
        helper.restoreEntity(createRestoreStream(ENTITY_KEY, "<opml><body><outline"));

        feedDatabaseWriter.verifyNoInteractions();
        assertTrue(feedUpdateManager.runOnceContexts.isEmpty());
    }

    @Test
    public void newStateAfterRestoreHoldsChecksumOfRestoredDocument() throws Exception {
        String opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><opml version=\"2.0\"><body>"
                + "<outline text=\"Feed One\" xmlUrl=\"https://example.com/one.xml\"/></body></opml>";
        helper.restoreEntity(createRestoreStream(ENTITY_KEY, opml));
        ParcelFileDescriptor[] newState = ParcelFileDescriptor.createPipe();

        helper.writeNewStateDescription(newState[1]);

        byte[] state = readAll(newState[0]);
        assertEquals(MD5_LENGTH, state[0]);
        assertArrayEquals(md5(opml.getBytes(StandardCharsets.UTF_8)), Arrays.copyOfRange(state, 1, state.length));
    }

    @Test
    public void newStateWithoutPriorRestoreIsLeftEmpty() throws Exception {
        ParcelFileDescriptor[] newState = ParcelFileDescriptor.createPipe();
        newState[1].close();

        helper.writeNewStateDescription(newState[1]);

        assertEquals(0, readAll(newState[0]).length);
    }
}
