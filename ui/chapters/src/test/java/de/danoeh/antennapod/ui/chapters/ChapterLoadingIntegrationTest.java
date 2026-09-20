package de.danoeh.antennapod.ui.chapters;

import android.content.Context;
import android.os.Parcel;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.EmbeddedChapterImage;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class ChapterLoadingIntegrationTest {
    private static final String PODCAST_INDEX_JSON = """
            {"version": "1.2.0", "chapters": [
              {"startTime": 0, "title": "Index intro", "url": "https://example.com/index-1",
               "img": "https://example.com/1.png"},
              {"startTime": 30, "title": "Index main"},
              {"startTime": 90, "title": "Index outro", "img": "https://example.com/3.png"}
            ]}
            """;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context context;
    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        context = RuntimeEnvironment.getApplication();
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
        AntennapodHttpClient.setCacheDirectory(temporaryFolder.newFolder("http-cache"));
        AntennapodHttpClient.reinit();
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
        PodDBAdapter.tearDownTests();
    }

    @Test
    public void readsId3ChaptersOfDownloadedFile() throws Exception {
        FeedMedia media = storeEpisode("auphonic.mp3", "audio/mpeg", null, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals("Chapter 1 - ❤️😊", chapters.get(0).getTitle());
        assertEquals("Chapter 4", chapters.get(3).getTitle());
        assertEquals(9000, chapters.get(3).getStart());
        assertEquals("https://example.com", chapters.get(1).getLink());
        assertEquals(EmbeddedChapterImage.makeUrl(1271, 308), chapters.get(1).getImageUrl());
    }

    @Test
    public void readsChaptersWithEmbeddedImagesOfDownloadedFile() throws Exception {
        FeedMedia media = storeEpisode("ultraschall5.mp3", "audio/mpeg", null, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(3, chapters.size());
        assertEquals(Arrays.asList(0L, 4004L, 7999L),
                Arrays.asList(chapters.get(0).getStart(), chapters.get(1).getStart(), chapters.get(2).getStart()));
        assertEquals(EmbeddedChapterImage.makeUrl(2766765, 15740), chapters.get(1).getImageUrl());
    }

    @Test
    public void readsOggVorbisChaptersOfDownloadedFile() throws Exception {
        FeedMedia media = storeEpisode("ffmpeg.ogg", "audio/ogg", null, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals("第2章 – 日本語", chapters.get(1).getTitle());
        assertEquals("https://example.com/kefalaio-3", chapters.get(2).getLink());
    }

    @Test
    public void readsOpusChaptersOfDownloadedFile() throws Exception {
        FeedMedia media = storeEpisode("auphonic.opus", "audio/opus", null, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals(6000, chapters.get(2).getStart());
        assertEquals("Chapter 3 - 爱", chapters.get(2).getTitle());
    }

    @Test
    public void readsFlacChaptersOfDownloadedFile() throws Exception {
        FeedMedia media = storeEpisode("ffmpeg.flac", "audio/flac", null, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals("الفصل ٤ – العربية", chapters.get(3).getTitle());
    }

    @Test
    public void readsM4aChaptersOfDownloadedFile() throws Exception {
        FeedMedia media = storeEpisode("nero-chapters.m4a", "audio/mp4", null, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals(3000, chapters.get(1).getStart());
        assertEquals("Chapter 2 - ßöÄ", chapters.get(1).getTitle());
    }

    @Test
    public void formatIsDetectedFromContentWhenMimeTypeIsWrong() throws Exception {
        FeedMedia media = storeEpisode("mp3chaps-py.mp3", "audio/ogg", null, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals("Start", chapters.get(0).getTitle());
        assertEquals("Chapter 3", chapters.get(3).getTitle());
    }

    @Test
    public void readsChaptersOfEpisodeStreamedFromServer() throws Exception {
        server.enqueue(new MockResponse().setBody(loadFixtureAsBuffer("auphonic.mp3")));
        FeedMedia media = storeStreamedEpisode(server.url("/episode.mp3").toString(), "audio/mpeg");

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals("Chapter 2 - ßöÄ", chapters.get(1).getTitle());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void unreadableStreamedFileTriesEveryFallbackFormatOnFreshStreams() throws Exception {
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setBody("this is not an audio file at all"));
        }
        FeedMedia media = storeStreamedEpisode(server.url("/episode.mp3").toString(), "audio/mpeg");

        ChapterUtils.loadChapters(media, context, false);

        assertNotNull(media.getChapters());
        assertTrue(media.getChapters().isEmpty());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void streamWithoutMimeTypeIsRecognisedFromContent() throws Exception {
        server.enqueue(new MockResponse().setBody(loadFixtureAsBuffer("auphonic.mp3")));
        FeedMedia media = storeStreamedEpisode(server.url("/episode").toString(), null);

        ChapterUtils.loadChapters(media, context, false);

        assertEquals(4, media.getChapters().size());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void missingLocalFileLeavesEpisodeWithoutChapters() throws Exception {
        FeedMedia media = storeEpisode("auphonic.mp3", "audio/mpeg", null, null);
        assertTrue(new File(media.getLocalFileUrl()).delete());

        ChapterUtils.loadChapters(media, context, false);

        assertNotNull(media.getChapters());
        assertTrue(media.getChapters().isEmpty());
    }

    @Test
    public void loadsChaptersStoredInDatabaseWhenFileHasNone() throws Exception {
        List<Chapter> stored = Arrays.asList(
                new Chapter(0, "Stored one", "https://example.com/s1", null),
                new Chapter(45000, "Stored two", null, "https://example.com/s2.png"));
        FeedMedia media = storeEpisode("ffmpeg-txxx-comment.mp3", "audio/mpeg", stored, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(2, chapters.size());
        assertEquals("Stored one", chapters.get(0).getTitle());
        assertEquals("https://example.com/s1", chapters.get(0).getLink());
        assertEquals(45000, chapters.get(1).getStart());
        assertEquals("https://example.com/s2.png", chapters.get(1).getImageUrl());
    }

    @Test
    public void storedChapterDetailsFillGapsInFileChaptersWithSameTimings() throws Exception {
        List<Chapter> stored = Arrays.asList(
                new Chapter(0, "Stored start", null, "https://example.com/start.png"),
                new Chapter(7000, "", "https://example.com/stored-1", null),
                new Chapter(9000, null, null, null),
                new Chapter(11000, "Stored end", null, null));
        FeedMedia media = storeEpisode("mp3chaps-py.mp3", "audio/mpeg", stored, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals("Start", chapters.get(0).getTitle());
        assertEquals("https://example.com/start.png", chapters.get(0).getImageUrl());
        assertEquals("Chapter 1", chapters.get(1).getTitle());
        assertEquals("https://example.com/stored-1", chapters.get(1).getLink());
        assertEquals("Chapter 3", chapters.get(3).getTitle());
    }

    @Test
    public void chapterListWithMoreEntriesWinsOverStoredChapters() throws Exception {
        List<Chapter> stored = Arrays.asList(
                new Chapter(0, "Stored one", null, null),
                new Chapter(12000, "Stored two", null, null));
        FeedMedia media = storeEpisode("mp3chaps-py.mp3", "audio/mpeg", stored, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals("Start", chapters.get(0).getTitle());
    }

    @Test
    public void chapterListsWithDifferentTimingsAreNotMerged() throws Exception {
        List<Chapter> stored = Arrays.asList(
                new Chapter(0, "Stored one", "https://example.com/1", "https://example.com/1.png"),
                new Chapter(20000, "Stored two", "https://example.com/2", "https://example.com/2.png"),
                new Chapter(40000, "Stored three", "https://example.com/3", "https://example.com/3.png"),
                new Chapter(60000, "Stored four", "https://example.com/4", "https://example.com/4.png"));
        FeedMedia media = storeEpisode("mp3chaps-py.mp3", "audio/mpeg", stored, null);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals("Stored one", chapters.get(0).getTitle());
        assertEquals(60000, chapters.get(3).getStart());
        assertEquals("https://example.com/4.png", chapters.get(3).getImageUrl());
    }

    @Test
    public void loadsChaptersFromPodcastIndexUrl() throws Exception {
        server.enqueue(new MockResponse().setBody(PODCAST_INDEX_JSON));
        String chapterUrl = server.url("/chapters.json").toString();
        FeedMedia media = storeEpisode("ffmpeg-txxx-comment.mp3", "audio/mpeg", null, chapterUrl);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(3, chapters.size());
        assertEquals("Index intro", chapters.get(0).getTitle());
        assertEquals("https://example.com/index-1", chapters.get(0).getLink());
        assertEquals("https://example.com/1.png", chapters.get(0).getImageUrl());
        assertEquals(30000, chapters.get(1).getStart());
        assertEquals(90000, chapters.get(2).getStart());
        assertEquals("https://example.com/3.png", chapters.get(2).getImageUrl());
    }

    @Test
    public void podcastIndexChaptersWinOverShorterFileChapters() throws Exception {
        server.enqueue(new MockResponse().setBody(PODCAST_INDEX_JSON));
        String chapterUrl = server.url("/chapters.json").toString();
        FeedMedia media = storeEpisode("hindenburg-journalist-pro.mp3", "audio/mpeg", null, chapterUrl);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(3, chapters.size());
        assertEquals("Index main", chapters.get(1).getTitle());
    }

    @Test
    public void podcastIndexChaptersWithSameTimingsAreEnrichedByFileChapters() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"version": "1.2.0", "chapters": [
                  {"startTime": 0, "title": ""},
                  {"startTime": 3, "title": "Index chapter two", "url": "https://example.com/index-2"},
                  {"startTime": 6, "title": ""},
                  {"startTime": 9, "title": "Index chapter four"}
                ]}
                """));
        String chapterUrl = server.url("/chapters.json").toString();
        FeedMedia media = storeEpisode("auphonic.mp3", "audio/mpeg", null, chapterUrl);

        ChapterUtils.loadChapters(media, context, false);

        List<Chapter> chapters = media.getChapters();
        assertEquals(4, chapters.size());
        assertEquals("Chapter 1 - ❤️😊", chapters.get(0).getTitle());
        assertEquals("Index chapter two", chapters.get(1).getTitle());
        assertEquals("https://example.com/index-2", chapters.get(1).getLink());
        assertEquals("Chapter 3 - 爱", chapters.get(2).getTitle());
        assertEquals(EmbeddedChapterImage.makeUrl(1771, 308), chapters.get(2).getImageUrl());
    }

    @Test
    public void failedPodcastIndexRequestKeepsFileChapters() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));
        String chapterUrl = server.url("/chapters.json").toString();
        FeedMedia media = storeEpisode("nero-chapters.m4a", "audio/mp4", null, chapterUrl);

        ChapterUtils.loadChapters(media, context, false);

        assertEquals(4, media.getChapters().size());
        assertEquals("Chapter 1 - ❤️😊", media.getChapters().get(0).getTitle());
    }

    @Test
    public void malformedPodcastIndexResponseYieldsNoChapters() throws Exception {
        server.enqueue(new MockResponse().setBody("this is not json"));
        String chapterUrl = server.url("/chapters.json").toString();
        FeedMedia media = storeEpisode("ffmpeg-txxx-comment.mp3", "audio/mpeg", null, chapterUrl);

        ChapterUtils.loadChapters(media, context, false);

        assertNotNull(media.getChapters());
        assertTrue(media.getChapters().isEmpty());
    }

    @Test
    public void podcastIndexChaptersAreServedFromCacheUnlessRefreshIsForced() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control", "max-age=3600")
                .setBody(PODCAST_INDEX_JSON));
        server.enqueue(new MockResponse().setBody("""
                {"version": "1.2.0", "chapters": [
                  {"startTime": 0, "title": "Fresh one"},
                  {"startTime": 10, "title": "Fresh two"},
                  {"startTime": 20, "title": "Fresh three"},
                  {"startTime": 30, "title": "Fresh four"}
                ]}
                """));
        String chapterUrl = server.url("/chapters.json").toString();

        List<Chapter> first = ChapterUtils.loadChaptersFromUrl(chapterUrl, false);
        List<Chapter> cached = ChapterUtils.loadChaptersFromUrl(chapterUrl, false);
        List<Chapter> forced = ChapterUtils.loadChaptersFromUrl(chapterUrl, true);

        assertEquals(3, first.size());
        assertEquals(3, cached.size());
        assertEquals("Index main", cached.get(1).getTitle());
        assertEquals(4, forced.size());
        assertEquals("Fresh four", forced.get(3).getTitle());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void singleDummyChapterInCacheIsRefreshedFromNetwork() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control", "max-age=3600")
                .setBody("{\"chapters\": [{\"startTime\": 0, \"title\": \"Dummy\"}]}"));
        server.enqueue(new MockResponse().setBody(PODCAST_INDEX_JSON));
        String chapterUrl = server.url("/chapters.json").toString();

        List<Chapter> dummy = ChapterUtils.loadChaptersFromUrl(chapterUrl, false);
        List<Chapter> refreshed = ChapterUtils.loadChaptersFromUrl(chapterUrl, false);

        assertEquals(1, dummy.size());
        assertEquals(3, refreshed.size());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void unreachablePodcastIndexServerYieldsNoChapters() throws Exception {
        String chapterUrl = server.url("/chapters.json").toString();
        server.shutdown();

        assertNull(ChapterUtils.loadChaptersFromUrl(chapterUrl, true));
    }

    @Test
    public void alreadyLoadedChaptersAreOnlyReloadedWhenRefreshIsForced() throws Exception {
        FeedMedia media = storeEpisode("auphonic.mp3", "audio/mpeg", null, null);
        List<Chapter> preset = new ArrayList<>();
        preset.add(new Chapter(0, "Preset", null, null));
        media.setChapters(preset);

        ChapterUtils.loadChapters(media, context, false);
        assertSame(preset, media.getChapters());

        ChapterUtils.loadChapters(media, context, true);
        assertEquals(4, media.getChapters().size());
        assertEquals("Chapter 1 - ❤️😊", media.getChapters().get(0).getTitle());
    }

    @Test
    public void episodeWithoutAnyChapterSourceGetsEmptyChapterList() throws Exception {
        FeedMedia media = storeEpisode("ffmpeg-txxx-comment.mp3", "audio/mpeg", null, null);

        ChapterUtils.loadChapters(media, context, false);

        assertNotNull(media.getChapters());
        assertTrue(media.getChapters().isEmpty());
    }

    @Test
    public void mediaWithoutItemResolvesItemFromDatabase() throws Exception {
        List<Chapter> stored = Arrays.asList(
                new Chapter(0, "Stored one", null, null),
                new Chapter(20000, "Stored two", null, null));
        FeedMedia stored1 = storeEpisode("ffmpeg-txxx-comment.mp3", "audio/mpeg", stored, null);
        FeedMedia detached = copyThroughParcel(stored1);
        assertNull(detached.getItem());

        ChapterUtils.loadChapters(detached, context, false);

        assertNotNull(detached.getItem());
        assertEquals(stored1.getItemId(), detached.getItem().getId());
        assertEquals(2, detached.getItem().getChapters().size());
        assertEquals("Stored two", detached.getItem().getChapters().get(1).getTitle());
    }

    private FeedMedia storeEpisode(String fixture, String mimeType, List<Chapter> chapters,
                                   String podcastIndexChapterUrl) throws Exception {
        File file = temporaryFolder.newFile(fixture);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(fixture)) {
            Files.write(file.toPath(), IOUtils.toByteArray(in));
        }
        Feed feed = new Feed("https://example.com/feed.xml", null, "Feed");
        feed.setItems(new ArrayList<>());
        FeedItem item = new FeedItem(0, "Episode", "episode-id", "https://example.com/episode", new Date(),
                FeedItem.UNPLAYED, feed);
        item.setChapters(chapters);
        item.setPodcastIndexChapterUrl(podcastIndexChapterUrl);
        FeedMedia media = new FeedMedia(item, "https://example.com/" + fixture, file.length(), mimeType);
        media.setLocalFileUrl(file.getAbsolutePath());
        media.setDownloaded(true, 1);
        item.setMedia(media);
        feed.getItems().add(item);
        return persistAndReload(feed, item);
    }

    private FeedMedia storeStreamedEpisode(String streamUrl, String mimeType) {
        Feed feed = new Feed("https://example.com/feed.xml", null, "Feed");
        feed.setItems(new ArrayList<>());
        FeedItem item = new FeedItem(0, "Episode", "episode-id", "https://example.com/episode", new Date(),
                FeedItem.UNPLAYED, feed);
        item.setMedia(new FeedMedia(item, streamUrl, 1000, mimeType));
        feed.getItems().add(item);
        return persistAndReload(feed, item);
    }

    private FeedMedia persistAndReload(Feed feed, FeedItem item) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        FeedItem reloaded = DBReader.getFeedItem(item.getId());
        assertNotNull(reloaded);
        assertNotNull(reloaded.getMedia());
        return reloaded.getMedia();
    }

    private FeedMedia copyThroughParcel(FeedMedia media) {
        Parcel parcel = Parcel.obtain();
        media.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return FeedMedia.CREATOR.createFromParcel(parcel);
    }

    private Buffer loadFixtureAsBuffer(String fixture) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(fixture)) {
            return new Buffer().write(IOUtils.toByteArray(in));
        }
    }
}
