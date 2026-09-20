package de.danoeh.antennapod.storage.importexport;

import android.app.backup.BackupDataInputStream;
import android.app.backup.BackupDataOutput;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class OpmlBackupPipelineTest extends ImportExportPipelineTestBase {
    private static final String ENTITY_KEY = "antennapod-feeds.opml";
    private static final int MD5_LENGTH = 16;

    private static class RecordingFeedUpdateManager extends FeedUpdateManager {
        private int runOnceCalls = 0;

        @Override
        public void restartUpdateAlarm(Context context, boolean replace) {
        }

        @Override
        public void runOnce(Context context) {
            runOnceCalls++;
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

    private RecordingFeedUpdateManager updateManager;
    private OpmlBackupAgent.OpmlBackupHelper helper;

    @Before
    public void setUpHelper() {
        updateManager = new RecordingFeedUpdateManager();
        FeedUpdateManager.setInstance(updateManager);
        helper = new OpmlBackupAgent.OpmlBackupHelper(context);
    }

    @After
    public void removeUpdateManager() {
        FeedUpdateManager.setInstance(null);
    }

    private ParcelFileDescriptor openState(String name, byte[] content) throws IOException {
        File file = new File(context.getCacheDir(), name);
        Files.write(file.toPath(), content);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
    }

    private byte[] md5(byte[] content) throws Exception {
        return MessageDigest.getInstance("MD5").digest(content);
    }

    private byte[] stateWith(byte[] checksum) {
        byte[] state = new byte[checksum.length + 1];
        state[0] = (byte) checksum.length;
        System.arraycopy(checksum, 0, state, 1, checksum.length);
        return state;
    }

    private BackupDataInputStream restoreStream(String key, String content) throws IOException {
        ByteArrayInputStream source = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        BackupDataInputStream data = mock(BackupDataInputStream.class);
        when(data.getKey()).thenReturn(key);
        when(data.read()).thenAnswer(invocation -> source.read());
        when(data.read(any(byte[].class))).thenAnswer(invocation -> source.read(invocation.<byte[]>getArgument(0)));
        when(data.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(invocation -> source.read(
                invocation.<byte[]>getArgument(0), invocation.<Integer>getArgument(1),
                invocation.<Integer>getArgument(2)));
        return data;
    }

    private byte[] backedUpBytes(BackupDataOutput data) throws IOException {
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
        verify(data).writeEntityData(bytes.capture(), size.capture());
        verify(data).writeEntityHeader(ENTITY_KEY, size.getValue());
        return Arrays.copyOf(bytes.getValue(), size.getValue());
    }

    private List<OpmlElement> parse(byte[] opml) throws Exception {
        return new OpmlReader().readDocument(
                new InputStreamReader(new ByteArrayInputStream(opml), StandardCharsets.UTF_8));
    }

    @Test
    public void backupContainsTheStoredSubscriptionsAsOpmlAndRecordsItsChecksum() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one", "https://example.com/1.png");
        subscribe("https://example.com/two.xml", "Second podcast", "https://example.com/two", "https://example.com/2.png");
        BackupDataOutput data = mock(BackupDataOutput.class);
        ParcelFileDescriptor newState = openState("new-state", new byte[0]);

        helper.performBackup(null, data, newState);

        byte[] backedUp = backedUpBytes(data);
        List<OpmlElement> elements = parse(backedUp);
        assertEquals(2, elements.size());
        assertEquals("First podcast", elements.get(0).getText());
        assertEquals("https://example.com/two.xml", elements.get(1).getXmlUrl());
        byte[] recorded = Files.readAllBytes(new File(context.getCacheDir(), "new-state").toPath());
        assertArrayEquals(stateWith(md5(backedUp)), recorded);
    }

    @Test
    public void backupIsWrittenWhenThePreviousStateHoldsADifferentChecksum() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one", "https://example.com/1.png");
        BackupDataOutput data = mock(BackupDataOutput.class);
        ParcelFileDescriptor oldState = openState("old-state", stateWith(new byte[MD5_LENGTH]));
        ParcelFileDescriptor newState = openState("new-state", new byte[0]);

        helper.performBackup(oldState, data, newState);

        assertEquals(1, parse(backedUpBytes(data)).size());
    }

    @Test
    public void backupIsWrittenWhenThePreviousStateIsEmpty() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one", "https://example.com/1.png");
        BackupDataOutput data = mock(BackupDataOutput.class);
        ParcelFileDescriptor oldState = openState("old-state", new byte[0]);
        ParcelFileDescriptor newState = openState("new-state", new byte[0]);

        helper.performBackup(oldState, data, newState);

        assertEquals(1, parse(backedUpBytes(data)).size());
    }

    @Test
    public void backupOfAnEmptyLibraryStillWritesAnOpmlDocumentWithoutOutlines() throws Exception {
        BackupDataOutput data = mock(BackupDataOutput.class);
        ParcelFileDescriptor newState = openState("new-state", new byte[0]);

        helper.performBackup(null, data, newState);

        assertTrue(parse(backedUpBytes(data)).isEmpty());
    }

    @Test
    public void restoredOpmlBecomesSubscriptionsAndTriggersAFeedRefresh() throws Exception {
        String opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><opml version=\"2.0\"><body>"
                + "<outline text=\"Restored one\" type=\"rss\" xmlUrl=\"https://example.com/restored-one.xml\"/>"
                + "<outline text=\"Restored two\" xmlUrl=\"https://example.com/restored-two.xml\"/>"
                + "</body></opml>";

        helper.restoreEntity(restoreStream(ENTITY_KEY, opml));

        List<Feed> feeds = storedFeeds();
        assertEquals(2, feeds.size());
        List<String> titles = new ArrayList<>();
        for (Feed feed : feeds) {
            titles.add(feed.getTitle());
            assertEquals(Feed.STATE_SUBSCRIBED, feed.getState());
        }
        assertTrue(titles.contains("Restored one"));
        assertTrue(titles.contains("Restored two"));
        assertEquals(1, updateManager.runOnceCalls);
    }

    @Test
    public void restoredStateDescriptionIsTheChecksumOfTheRestoredDocument() throws Exception {
        String opml = "<opml version=\"2.0\"><body>"
                + "<outline text=\"Restored\" xmlUrl=\"https://example.com/restored.xml\"/></body></opml>";
        ParcelFileDescriptor newState = openState("new-state", new byte[0]);

        helper.restoreEntity(restoreStream(ENTITY_KEY, opml));
        helper.writeNewStateDescription(newState);

        byte[] recorded = Files.readAllBytes(new File(context.getCacheDir(), "new-state").toPath());
        assertArrayEquals(stateWith(md5(opml.getBytes(StandardCharsets.UTF_8))), recorded);
    }

    @Test
    public void stateDescriptionIsLeftEmptyWhenNothingWasRestored() throws Exception {
        ParcelFileDescriptor newState = openState("new-state", new byte[0]);

        helper.writeNewStateDescription(newState);

        assertEquals(0, Files.readAllBytes(new File(context.getCacheDir(), "new-state").toPath()).length);
    }

    @Test
    public void entitiesWithUnknownKeysAreIgnored() throws Exception {
        String opml = "<opml version=\"2.0\"><body>"
                + "<outline text=\"Ignored\" xmlUrl=\"https://example.com/ignored.xml\"/></body></opml>";
        BackupDataInputStream data = restoreStream("something-else", opml);

        helper.restoreEntity(data);

        assertTrue(storedFeeds().isEmpty());
        assertEquals(0, updateManager.runOnceCalls);
        verify(data, never()).read();
    }

    @Test
    public void malformedOpmlIsSkippedWithoutAddingFeedsOrRefreshing() throws Exception {
        helper.restoreEntity(restoreStream(ENTITY_KEY, "<opml><body><outline text=\"Broken\""));

        assertTrue(storedFeeds().isEmpty());
        assertEquals(0, updateManager.runOnceCalls);
    }
}
