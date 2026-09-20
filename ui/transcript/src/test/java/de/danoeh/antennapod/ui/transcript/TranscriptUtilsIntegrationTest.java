package de.danoeh.antennapod.ui.transcript;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class TranscriptUtilsIntegrationTest {
    private static final String TYPE_VTT = "text/vtt";
    private static final String TYPE_SRT = "application/srt";
    private static final String TYPE_JSON = "application/json";

    private static final String VTT_TWO_SPEAKERS = """
            WEBVTT

            00:00.000 --> 00:02.000
            <v Alice>Welcome to the show.

            00:02.000 --> 00:04.000
            <v Alice>Today we talk about parsers.

            00:04.000 --> 00:07.000
            <v Bob>Thanks for having me.
            """;

    private static final String SRT_TWO_SPEAKERS = """
            1
            00:00:00,000 --> 00:00:03,000
            Alice: Hello there.

            2
            00:00:03,000 --> 00:00:06,500
            Alice: Second line.

            3
            00:00:06,500 --> 00:00:09,000
            Bob: Reply.
            """;

    private static final String JSON_TWO_SPEAKERS = """
            {"version": "1.0.0", "segments": [
              {"speaker": "Alice", "startTime": 0, "endTime": 3, "body": "Hello and welcome."},
              {"speaker": "Alice", "startTime": 3, "endTime": 6, "body": "Today: parsers."},
              {"speaker": "Bob", "startTime": 6, "endTime": 8, "body": "Glad to be here."}
            ]}
            """;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        AntennapodHttpClient.setCacheDirectory(temporaryFolder.newFolder("http-cache"));
        AntennapodHttpClient.reinit();
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void loadsVttTranscriptFromServer() throws Exception {
        server.enqueue(new MockResponse().setBody(VTT_TWO_SPEAKERS));

        Transcript transcript = TranscriptUtils.loadTranscript(createMedia(TYPE_VTT, null), false);

        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals("Welcome to the show. Today we talk about parsers.", transcript.getSegmentAt(0).getWords());
        assertEquals("Alice", transcript.getSegmentAt(0).getSpeaker());
        assertEquals(0L, transcript.getSegmentAt(0).getStartTime());
        assertEquals(4000L, transcript.getSegmentAt(0).getEndTime());
        assertEquals("Thanks for having me.", transcript.getSegmentAt(1).getWords());
        assertEquals("Bob", transcript.getSegmentAt(1).getSpeaker());
        assertEquals(Set.of("Alice", "Bob"), transcript.getSpeakers());
    }

    @Test
    public void loadsSrtTranscriptFromServer() throws Exception {
        server.enqueue(new MockResponse().setBody(SRT_TWO_SPEAKERS));

        Transcript transcript = TranscriptUtils.loadTranscript(createMedia(TYPE_SRT, null), false);

        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals("Hello there. Second line.", transcript.getSegmentAt(0).getWords());
        assertEquals("Alice", transcript.getSegmentAt(0).getSpeaker());
        assertEquals(0L, transcript.getSegmentAt(0).getStartTime());
        assertEquals(6500L, transcript.getSegmentAt(0).getEndTime());
        assertEquals("Reply.", transcript.getSegmentAt(1).getWords());
        assertEquals("Bob", transcript.getSegmentAt(1).getSpeaker());
        assertEquals(Set.of("Alice", "Bob"), transcript.getSpeakers());
    }

    @Test
    public void loadsJsonTranscriptFromServer() throws Exception {
        server.enqueue(new MockResponse().setBody(JSON_TWO_SPEAKERS));

        Transcript transcript = TranscriptUtils.loadTranscript(createMedia(TYPE_JSON, null), false);

        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals("Hello and welcome. Today: parsers.", transcript.getSegmentAt(0).getWords());
        assertEquals("Alice", transcript.getSegmentAt(0).getSpeaker());
        assertEquals(0L, transcript.getSegmentAt(0).getStartTime());
        assertEquals(6000L, transcript.getSegmentAt(0).getEndTime());
        assertEquals("Glad to be here.", transcript.getSegmentAt(1).getWords());
        assertEquals("Bob", transcript.getSegmentAt(1).getSpeaker());
        assertEquals(Set.of("Alice", "Bob"), transcript.getSpeakers());
    }

    @Test
    public void failedDownloadYieldsNoTranscript() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));

        Transcript transcript = TranscriptUtils.loadTranscript(createMedia(TYPE_VTT, null), false);

        assertNull(transcript);
    }

    @Test
    public void unparsableVttYieldsNoTranscript() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                WEBVTT

                not-a-time --> also-not-a-time
                Broken cue
                """));

        Transcript transcript = TranscriptUtils.loadTranscript(createMedia(TYPE_VTT, null), false);

        assertNull(transcript);
    }

    @Test
    public void unparsableJsonYieldsNoTranscript() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"version\": \"1.0.0\"}"));

        Transcript transcript = TranscriptUtils.loadTranscript(createMedia(TYPE_JSON, null), false);

        assertNull(transcript);
    }

    @Test
    public void srtWithoutTimecodesYieldsNoTranscript() throws Exception {
        server.enqueue(new MockResponse().setBody("1\nHello\n\n2\nWorld\n"));

        Transcript transcript = TranscriptUtils.loadTranscript(createMedia(TYPE_SRT, null), false);

        assertNull(transcript);
    }

    @Test
    public void storedTranscriptIsLoadedFromFileWithoutNetworkAccess() throws Exception {
        File mediaFile = temporaryFolder.newFile("episode.mp3");
        FeedMedia media = createMedia(TYPE_VTT, mediaFile);
        TranscriptUtils.storeTranscript(media, VTT_TWO_SPEAKERS);

        Transcript transcript = TranscriptUtils.loadTranscript(media, false);

        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals("Bob", transcript.getSegmentAt(1).getSpeaker());
        assertEquals(0, server.getRequestCount());
        assertSame(transcript, media.getTranscript());
    }

    @Test
    public void storeTranscriptReplacesPreviousFileContent() throws Exception {
        File mediaFile = temporaryFolder.newFile("episode.mp3");
        FeedMedia media = createMedia(TYPE_VTT, mediaFile);
        TranscriptUtils.storeTranscript(media, "first version");

        TranscriptUtils.storeTranscript(media, VTT_TWO_SPEAKERS);

        assertEquals(VTT_TWO_SPEAKERS,
                FileUtils.readFileToString(new File(media.getTranscriptFileUrl()), StandardCharsets.UTF_8));
    }

    @Test
    public void forcedRefreshIgnoresStoredTranscriptFile() throws Exception {
        File mediaFile = temporaryFolder.newFile("episode.mp3");
        FeedMedia media = createMedia(TYPE_VTT, mediaFile);
        TranscriptUtils.storeTranscript(media, VTT_TWO_SPEAKERS);
        server.enqueue(new MockResponse().setBody("""
                WEBVTT

                00:00.000 --> 00:03.000
                <v Carol>Fresh content from the server.
                """));

        Transcript transcript = TranscriptUtils.loadTranscript(media, true);

        assertNotNull(transcript);
        assertEquals(1, transcript.getSegmentCount());
        assertEquals("Fresh content from the server.", transcript.getSegmentAt(0).getWords());
        assertEquals("Carol", transcript.getSegmentAt(0).getSpeaker());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void emptyStoredTranscriptFileFallsBackToServer() throws Exception {
        File mediaFile = temporaryFolder.newFile("episode.mp3");
        FeedMedia media = createMedia(TYPE_VTT, mediaFile);
        TranscriptUtils.storeTranscript(media, "");
        server.enqueue(new MockResponse().setBody(VTT_TWO_SPEAKERS));

        Transcript transcript = TranscriptUtils.loadTranscript(media, false);

        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void missingTranscriptFileFallsBackToServer() throws Exception {
        File mediaFile = temporaryFolder.newFile("episode.mp3");
        FeedMedia media = createMedia(TYPE_SRT, mediaFile);
        server.enqueue(new MockResponse().setBody(SRT_TWO_SPEAKERS));

        Transcript transcript = TranscriptUtils.loadTranscript(media, false);

        assertNotNull(transcript);
        assertEquals("Reply.", transcript.getSegmentAt(1).getWords());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void transcriptAlreadyAttachedToItemIsReturnedWithoutLoading() throws Exception {
        FeedMedia media = createMedia(TYPE_VTT, null);
        Transcript attached = new Transcript();
        media.getItem().setTranscript(attached);

        Transcript transcript = TranscriptUtils.loadTranscript(media, false);

        assertSame(attached, transcript);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void cacheableTranscriptIsServedFromHttpCacheUnlessRefreshIsForced() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control", "max-age=3600")
                .setBody(VTT_TWO_SPEAKERS));
        server.enqueue(new MockResponse().setBody("second download"));
        String url = server.url("/transcript.vtt").toString();

        String firstLoad = TranscriptUtils.loadTranscriptFromUrl(url, false);
        String cachedLoad = TranscriptUtils.loadTranscriptFromUrl(url, false);
        String forcedLoad = TranscriptUtils.loadTranscriptFromUrl(url, true);

        assertEquals(VTT_TWO_SPEAKERS, firstLoad);
        assertEquals(VTT_TWO_SPEAKERS, cachedLoad);
        assertEquals("second download", forcedLoad);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void placeholderTranscriptInCacheIsRefreshedFromNetwork() throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Cache-Control", "max-age=3600")
                .setBody("x"));
        server.enqueue(new MockResponse().setBody(VTT_TWO_SPEAKERS));
        String url = server.url("/transcript.vtt").toString();

        String placeholder = TranscriptUtils.loadTranscriptFromUrl(url, false);
        String refreshed = TranscriptUtils.loadTranscriptFromUrl(url, false);

        assertEquals("x", placeholder);
        assertEquals(VTT_TWO_SPEAKERS, refreshed);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void unreachableServerYieldsNoTranscriptText() throws Exception {
        String url = server.url("/transcript.vtt").toString();
        server.shutdown();

        String transcript = TranscriptUtils.loadTranscriptFromUrl(url, true);

        assertNull(transcript);
    }

    private FeedMedia createMedia(String transcriptType, File mediaFile) {
        FeedItem item = new FeedItem();
        item.setTranscriptUrl(transcriptType, server.url("/transcript").toString());
        String localPath = mediaFile == null ? null : mediaFile.getAbsolutePath();
        FeedMedia media = new FeedMedia(1, item, 0, 0, 0, "audio/mpeg", localPath,
                "https://example.com/episode.mp3", 1, null, 0, 0);
        item.setMedia(media);
        return media;
    }
}
