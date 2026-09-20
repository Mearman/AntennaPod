package de.danoeh.antennapod.net.download.service.feed.local;

import android.content.Context;
import android.net.Uri;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class LocalFeedUpdaterMetadataIntegrationTest {
    private static final String FEED_URL =
            "content://com.android.externalstorage.documents/tree/primary%3ADownload%2Flocal-feed";
    private static final String UNICODE_DESCRIPTION =
            "Ünïcödé tëst — “smart quotes” ½ ≠ ⅓ · Ελληνικά · 日本語 · 한국어 · العربية 📝";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        SynchronizationSettings.init(context);
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
    }

    @After
    public void tearDown() {
        DBWriter.tearDownTests();
        PodDBAdapter.tearDownTests();
    }

    @Test
    public void descriptionIsReadFromId3CommentFrame() throws Exception {
        assertEquals("Description", scanSingleFile("ultraschall5.mp3", "audio/mpeg").getDescription());
    }

    @Test
    public void descriptionIsReadFromId3CommentFrameOfAnotherTagger() throws Exception {
        assertEquals("Summary", scanSingleFile("auphonic.mp3", "audio/mpeg").getDescription());
    }

    @Test
    public void descriptionIsReadFromId3CustomTextFrame() throws Exception {
        assertEquals("This is the comment",
                scanSingleFile("ffmpeg-txxx-comment.mp3", "audio/mpeg").getDescription());
    }

    @Test
    public void unicodeId3CommentIsDecoded() throws Exception {
        assertEquals(UNICODE_DESCRIPTION, scanSingleFile("ffmpeg.mp3", "audio/mpeg").getDescription());
    }

    @Test
    public void descriptionIsReadFromOggVorbisComments() throws Exception {
        assertEquals(UNICODE_DESCRIPTION, scanSingleFile("ffmpeg.ogg", "audio/ogg").getDescription());
    }

    @Test
    public void descriptionIsReadFromOpusComments() throws Exception {
        assertEquals(UNICODE_DESCRIPTION, scanSingleFile("ffmpeg.opus", "audio/opus").getDescription());
    }

    @Test
    public void descriptionIsReadFromFlacComments() throws Exception {
        assertEquals(UNICODE_DESCRIPTION, scanSingleFile("ffmpeg.flac", "audio/flac").getDescription());
    }

    @Test
    public void vorbisSynopsisIsUsedAsDescription() throws Exception {
        assertEquals("This is the synopsis", scanSingleFile("ffmpeg-synopsis.ogg", "audio/ogg").getDescription());
    }

    @Test
    public void descriptionIsReadFromM4aMetadata() throws Exception {
        assertEquals(UNICODE_DESCRIPTION, scanSingleFile("ffmpeg.m4a", "audio/mp4").getDescription());
    }

    @Test
    public void m4aWithoutDescriptionLeavesItemDescriptionEmpty() throws Exception {
        FeedItem item = scanSingleFile("nero-chapters.m4a", "audio/mp4");

        assertNull(item.getDescription());
        assertEquals("nero-chapters", item.getTitle());
    }

    @Test
    public void unparsableFileStillBecomesEpisodeWithoutDescription() throws Exception {
        File folder = temporaryFolder.newFolder("garbage");
        File file = new File(folder, "broken.mp3");
        Files.write(file.toPath(), "this is definitely not an mp3 file".getBytes(StandardCharsets.UTF_8));

        List<FeedItem> items = updateFeedFrom(folder, "audio/mpeg");

        assertEquals(1, items.size());
        assertEquals("broken", items.get(0).getTitle());
        assertNull(items.get(0).getDescription());
    }

    @Test
    public void metadataOfUnchangedFileSurvivesSubsequentUpdate() throws Exception {
        File folder = temporaryFolder.newFolder("rescan");
        copyFixture("ffmpeg.ogg", new File(folder, "episode.ogg"));

        List<FeedItem> firstScan = updateFeedFrom(folder, "audio/ogg");
        List<FeedItem> secondScan = updateFeedFrom(folder, "audio/ogg");

        assertEquals(1, firstScan.size());
        assertEquals(1, secondScan.size());
        assertEquals(firstScan.get(0).getId(), secondScan.get(0).getId());
        assertEquals(UNICODE_DESCRIPTION, secondScan.get(0).getDescription());
    }

    @Test
    public void everyAudioFileOfFolderBecomesEpisodeWithOwnDescription() throws Exception {
        File folder = temporaryFolder.newFolder("mixed");
        copyFixture("ultraschall5.mp3", new File(folder, "a-first.mp3"));
        copyFixture("ffmpeg-synopsis.ogg", new File(folder, "b-second.ogg"));
        copyFixture("ffmpeg.m4a", new File(folder, "c-third.m4a"));

        List<FeedItem> items = updateFeedFrom(folder, null);

        assertEquals(3, items.size());
        assertEquals("Description", descriptionOf(items, "a-first"));
        assertEquals("This is the synopsis", descriptionOf(items, "b-second"));
        assertEquals(UNICODE_DESCRIPTION, descriptionOf(items, "c-third"));
    }

    private String descriptionOf(List<FeedItem> items, String title) {
        for (FeedItem item : items) {
            if (title.equals(item.getTitle())) {
                return item.getDescription();
            }
        }
        throw new AssertionError("No episode titled " + title);
    }

    private FeedItem scanSingleFile(String fixture, String mimeType) throws Exception {
        File folder = temporaryFolder.newFolder("folder-" + fixture);
        copyFixture(fixture, new File(folder, fixture));
        List<FeedItem> items = updateFeedFrom(folder, mimeType);
        assertEquals(1, items.size());
        return items.get(0);
    }

    private void copyFixture(String fixture, File target) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(fixture)) {
            assertNotNull(in);
            Files.write(target.toPath(), IOUtils.toByteArray(in));
        }
    }

    private List<FeedItem> updateFeedFrom(File folder, String forcedMimeType) throws IOException {
        List<FastDocumentFile> files = new ArrayList<>();
        for (File file : folder.listFiles()) {
            String mimeType = forcedMimeType != null ? forcedMimeType : mimeTypeOf(file.getName());
            files.add(new FastDocumentFile(file.getName(), mimeType, Uri.parse(file.toURI().toString()),
                    file.length(), file.lastModified()));
        }
        try (MockedStatic<FastDocumentFile> documentFiles = Mockito.mockStatic(FastDocumentFile.class)) {
            documentFiles.when(() -> FastDocumentFile.list(any(), any())).thenReturn(files);
            LocalFeedUpdater.tryUpdateFeed(new Feed(FEED_URL, null), context, null, null);
        }
        Feed feed = DBReader.getFeedList().get(0);
        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Integer.MAX_VALUE);
        for (FeedItem item : items) {
            DBReader.loadDescriptionOfFeedItem(item);
        }
        return items;
    }

    private static String mimeTypeOf(String fileName) {
        if (fileName.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (fileName.endsWith(".ogg")) {
            return "audio/ogg";
        } else {
            return "audio/mp4";
        }
    }
}
