package de.danoeh.antennapod.net.download.service.feed.remote;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowStatFs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class HttpDownloaderTest {
    private static final int BLOCK_SIZE = 4096;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private MockWebServer server;
    private File destination;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserPreferences.init(context);
        registerFreeSpace(1_000_000);
        AntennapodHttpClient.setCacheDirectory(temporaryFolder.newFolder("http-cache"));
        AntennapodHttpClient.reinit();
        server = new MockWebServer();
        server.start();
        destination = new File(temporaryFolder.getRoot(), "download.bin");
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private void registerFreeSpace(int freeBlocks) {
        ShadowStatFs.registerStats(UserPreferences.getDataFolder(null), freeBlocks, freeBlocks, freeBlocks);
    }

    private static byte[] bytes(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i % 251);
        }
        return data;
    }

    private static MockResponse binaryResponse(byte[] body) {
        return new MockResponse().setBody(new Buffer().write(body)).addHeader("Content-Type", "audio/mpeg");
    }

    private DownloadRequest mediaRequest(String path) {
        return new DownloadRequest(destination.getAbsolutePath(), server.url(path).toString(), "Episode", 1,
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, null, null, false, null, true);
    }

    private DownloadRequest mediaRequest(String path, String username, String password) {
        return new DownloadRequest(destination.getAbsolutePath(), server.url(path).toString(), "Episode", 1,
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, username, password, false, null, true);
    }

    private DownloadRequest feedRequest(String path, String lastModified) {
        return new DownloadRequest(destination.getAbsolutePath(), server.url(path).toString(), "Feed", 1,
                Feed.FEEDFILETYPE_FEED, lastModified, null, null, false, null, true);
    }

    private Downloader run(DownloadRequest request) {
        Downloader downloader = new HttpDownloader(request);
        downloader.call();
        return downloader;
    }

    private static byte[] gzip(byte[] plain) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(compressed)) {
            out.write(plain);
        }
        return compressed.toByteArray();
    }

    @Test
    public void successfulMediaDownloadWritesBodyAndReportsSize() throws Exception {
        byte[] body = bytes(50_000);
        server.enqueue(binaryResponse(body));
        DownloadRequest request = mediaRequest("/episode.mp3");

        Downloader downloader = run(request);

        assertTrue(downloader.isFinished());
        assertTrue(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.SUCCESS, downloader.getResult().getReason());
        assertArrayEquals(body, Files.readAllBytes(destination.toPath()));
        assertEquals(body.length, request.getSize());
        assertEquals(body.length, request.getSoFar());
        assertEquals(100, request.getProgressPercent());
    }

    @Test
    public void downloaderResultCarriesRequestIdentity() {
        server.enqueue(binaryResponse(bytes(10)));

        Downloader downloader = run(mediaRequest("/episode.mp3"));

        assertEquals("Episode", downloader.getResult().getTitle());
        assertEquals(1, downloader.getResult().getFeedfileId());
        assertEquals(FeedMedia.FEEDFILETYPE_FEEDMEDIA, downloader.getResult().getFeedfileType());
    }

    @Test
    public void mediaRequestDisablesTransparentCompressionAndCaching() throws Exception {
        server.enqueue(binaryResponse(bytes(10)));

        run(mediaRequest("/episode.mp3"));

        RecordedRequest recorded = server.takeRequest();
        assertEquals("identity", recorded.getHeader("Accept-Encoding"));
        assertEquals("no-cache", recorded.getHeader("Cache-Control"));
        assertEquals("1", recorded.getHeader("Upgrade-Insecure-Requests"));
        assertEquals("AntennaPod/0.0.0", recorded.getHeader("User-Agent"));
    }

    @Test
    public void feedRequestUsesNoStoreAndAllowsTransparentCompression() throws Exception {
        server.enqueue(new MockResponse().setBody("<rss/>").addHeader("Content-Type", "application/rss+xml"));

        run(feedRequest("/feed.xml", null));

        RecordedRequest recorded = server.takeRequest();
        assertEquals("no-store", recorded.getHeader("Cache-Control"));
        assertEquals("gzip", recorded.getHeader("Accept-Encoding"));
    }

    @Test
    public void gzipEncodedFeedIsStoredDecompressedWithUnknownSize() throws Exception {
        byte[] plain = "<rss><channel><title>t</title></channel></rss>".getBytes(StandardCharsets.UTF_8);
        server.enqueue(new MockResponse().setBody(new Buffer().write(gzip(plain)))
                .addHeader("Content-Encoding", "gzip").addHeader("Content-Type", "application/rss+xml"));
        DownloadRequest request = feedRequest("/feed.xml", null);

        Downloader downloader = run(request);

        assertTrue(downloader.getResult().isSuccessful());
        assertArrayEquals(plain, Files.readAllBytes(destination.toPath()));
        assertEquals(DownloadResult.SIZE_UNKNOWN, request.getSize());
    }

    @Test
    public void successfulDownloadStoresLastModifiedHeaderOnRequest() {
        server.enqueue(new MockResponse().setBody("<rss/>")
                .addHeader("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT")
                .addHeader("ETag", "\"etag-1\""));
        DownloadRequest request = feedRequest("/feed.xml", null);

        run(request);

        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", request.getLastModified());
    }

    @Test
    public void successfulDownloadFallsBackToETagWhenNoLastModifiedHeader() {
        server.enqueue(new MockResponse().setBody("<rss/>").addHeader("ETag", "\"etag-1\""));
        DownloadRequest request = feedRequest("/feed.xml", null);

        run(request);

        assertEquals("\"etag-1\"", request.getLastModified());
    }

    @Test
    public void recentLastModifiedDateIsSentAsIfModifiedSince() throws Exception {
        String recent = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        server.enqueue(new MockResponse().setResponseCode(304));

        run(feedRequest("/feed.xml", recent));

        RecordedRequest recorded = server.takeRequest();
        assertEquals(recent, recorded.getHeader("If-Modified-Since"));
        assertNull(recorded.getHeader("If-None-Match"));
    }

    @Test
    public void lastModifiedDateOlderThanThreeDaysIsNotSent() throws Exception {
        String old = ZonedDateTime.now(ZoneOffset.UTC).minusDays(10).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        server.enqueue(new MockResponse().setBody("<rss/>"));

        run(feedRequest("/feed.xml", old));

        RecordedRequest recorded = server.takeRequest();
        assertNull(recorded.getHeader("If-Modified-Since"));
        assertNull(recorded.getHeader("If-None-Match"));
    }

    @Test
    public void nonDateLastModifiedValueIsSentAsIfNoneMatch() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(304));

        run(feedRequest("/feed.xml", "\"etag-1\""));

        RecordedRequest recorded = server.takeRequest();
        assertEquals("\"etag-1\"", recorded.getHeader("If-None-Match"));
        assertNull(recorded.getHeader("If-Modified-Since"));
    }

    @Test
    public void notModifiedResponseCancelsDownloadWithoutWritingFile() {
        server.enqueue(new MockResponse().setResponseCode(304));

        Downloader downloader = run(feedRequest("/feed.xml", "\"etag-1\""));

        assertTrue(downloader.cancelled);
        assertFalse(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.ERROR_DOWNLOAD_CANCELLED, downloader.getResult().getReason());
        assertFalse(destination.exists());
    }

    @Test
    public void errorStatusesAreMappedToDownloadErrors() {
        int[] statuses = {401, 403, 404, 410, 416, 500, 503};
        DownloadError[] expected = {
            DownloadError.ERROR_UNAUTHORIZED, DownloadError.ERROR_FORBIDDEN, DownloadError.ERROR_NOT_FOUND,
            DownloadError.ERROR_NOT_FOUND, DownloadError.ERROR_HTTP_DATA_ERROR, DownloadError.ERROR_HTTP_DATA_ERROR,
            DownloadError.ERROR_HTTP_DATA_ERROR};
        for (int i = 0; i < statuses.length; i++) {
            server.enqueue(new MockResponse().setResponseCode(statuses[i]));
            Downloader downloader = run(mediaRequest("/episode.mp3"));

            assertFalse("status " + statuses[i], downloader.getResult().isSuccessful());
            assertEquals("status " + statuses[i], expected[i], downloader.getResult().getReason());
            assertEquals(String.valueOf(statuses[i]), downloader.getResult().getReasonDetailed());
        }
    }

    @Test
    public void resumeSendsRangeHeaderAndAppendsToExistingFile() throws Exception {
        byte[] full = bytes(2000);
        Files.write(destination.toPath(), Arrays.copyOfRange(full, 0, 1000));
        server.enqueue(binaryResponse(Arrays.copyOfRange(full, 1000, 2000)).setResponseCode(206)
                .addHeader("Content-Range", "bytes 1000-1999/2000"));
        DownloadRequest request = mediaRequest("/episode.mp3");

        Downloader downloader = run(request);

        RecordedRequest recorded = server.takeRequest();
        assertEquals("bytes=1000-", recorded.getHeader("Range"));
        assertNull(recorded.getHeader("If-Range"));
        assertTrue(downloader.getResult().isSuccessful());
        assertArrayEquals(full, Files.readAllBytes(destination.toPath()));
        assertEquals(2000, request.getSize());
        assertEquals(2000, request.getSoFar());
    }

    @Test
    public void resumeWithLastModifiedSendsIfRangeHeader() throws Exception {
        Files.write(destination.toPath(), bytes(100));
        server.enqueue(binaryResponse(bytes(100)));
        DownloadRequest request = new DownloadRequest(destination.getAbsolutePath(),
                server.url("/episode.mp3").toString(), "Episode", 1, FeedMedia.FEEDFILETYPE_FEEDMEDIA,
                "\"etag-1\"", null, null, false, null, true);

        run(request);

        RecordedRequest recorded = server.takeRequest();
        assertEquals("bytes=100-", recorded.getHeader("Range"));
        assertEquals("\"etag-1\"", recorded.getHeader("If-Range"));
    }

    @Test
    public void resumeWhereServerIgnoresRangeRestartsFromBeginning() throws Exception {
        byte[] full = bytes(3000);
        Files.write(destination.toPath(), Arrays.copyOfRange(full, 0, 1000));
        server.enqueue(binaryResponse(full));
        DownloadRequest request = mediaRequest("/episode.mp3");

        Downloader downloader = run(request);

        assertTrue(downloader.getResult().isSuccessful());
        assertArrayEquals(full, Files.readAllBytes(destination.toPath()));
        assertEquals("bytes=1000-", server.takeRequest().getHeader("Range"));
    }

    @Test
    public void emptyExistingFileDoesNotSendRangeHeader() throws Exception {
        assertTrue(destination.createNewFile());
        server.enqueue(binaryResponse(bytes(10)));

        run(mediaRequest("/episode.mp3"));

        assertNull(server.takeRequest().getHeader("Range"));
    }

    @Test
    public void rangeNotSatisfiableIsReportedWithStatusDetail() throws Exception {
        Files.write(destination.toPath(), bytes(500));
        server.enqueue(new MockResponse().setResponseCode(416));

        Downloader downloader = run(mediaRequest("/episode.mp3"));

        assertEquals(DownloadError.ERROR_HTTP_DATA_ERROR, downloader.getResult().getReason());
        assertEquals("416", downloader.getResult().getReasonDetailed());
    }

    @Test
    public void truncatedBodyIsReportedAsWrongSize() {
        server.enqueue(binaryResponse(bytes(20_000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        DownloadRequest request = mediaRequest("/episode.mp3");

        Downloader downloader = run(request);

        assertFalse(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.ERROR_IO_WRONG_SIZE, downloader.getResult().getReason());
        assertEquals(20_000, request.getSize());
        assertTrue(request.getSoFar() < 20_000);
    }

    @Test
    public void smallTextResponseForMediaIsRejectedAsWrongFileType() {
        server.enqueue(new MockResponse().setBody("<html>Not here</html>").addHeader("Content-Type", "text/html"));

        Downloader downloader = run(mediaRequest("/episode.mp3"));

        assertFalse(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.ERROR_FILE_TYPE, downloader.getResult().getReason());
    }

    @Test
    public void largeTextResponseForMediaIsAccepted() throws Exception {
        byte[] body = new byte[150 * 1024];
        Arrays.fill(body, (byte) 'a');
        server.enqueue(new MockResponse().setBody(new Buffer().write(body)).addHeader("Content-Type", "text/plain"));

        Downloader downloader = run(mediaRequest("/episode.txt"));

        assertTrue(downloader.getResult().isSuccessful());
        assertEquals(body.length, destination.length());
    }

    @Test
    public void textResponseForFeedIsAccepted() {
        server.enqueue(new MockResponse().setBody("<rss/>").addHeader("Content-Type", "text/xml"));

        Downloader downloader = run(feedRequest("/feed.xml", null));

        assertTrue(downloader.getResult().isSuccessful());
    }

    @Test
    public void cancelledBeforeStartEndsCancelledWithoutBody() {
        server.enqueue(binaryResponse(bytes(50_000)));
        Downloader downloader = new HttpDownloader(mediaRequest("/episode.mp3"));
        downloader.cancel();

        downloader.call();

        assertTrue(downloader.isFinished());
        assertTrue(downloader.cancelled);
        assertEquals(DownloadError.ERROR_DOWNLOAD_CANCELLED, downloader.getResult().getReason());
        assertEquals(0, destination.length());
    }

    @Test
    public void insufficientFreeSpaceFailsBeforeReadingBody() {
        registerFreeSpace(1);
        server.enqueue(binaryResponse(bytes(2 * BLOCK_SIZE)));

        Downloader downloader = run(mediaRequest("/episode.mp3"));

        assertFalse(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.ERROR_NOT_ENOUGH_SPACE, downloader.getResult().getReason());
    }

    @Test
    public void downloadOfExactlyAvailableSpaceSucceeds() {
        registerFreeSpace(2);
        server.enqueue(binaryResponse(bytes(2 * BLOCK_SIZE)));

        Downloader downloader = run(mediaRequest("/episode.mp3"));

        assertTrue(downloader.getResult().isSuccessful());
    }

    @Test
    public void permanentRedirectIsRecordedAsNewUrl() {
        server.enqueue(new MockResponse().setResponseCode(301).addHeader("Location", server.url("/new.mp3")));
        server.enqueue(binaryResponse(bytes(10)));

        Downloader downloader = run(mediaRequest("/old.mp3"));

        assertTrue(downloader.getResult().isSuccessful());
        assertEquals(server.url("/new.mp3").toString(), downloader.permanentRedirectUrl);
    }

    @Test
    public void permanentRedirectStatus308IsRecordedAsNewUrl() {
        server.enqueue(new MockResponse().setResponseCode(308).addHeader("Location", server.url("/new.mp3")));
        server.enqueue(binaryResponse(bytes(10)));

        Downloader downloader = run(mediaRequest("/old.mp3"));

        assertEquals(server.url("/new.mp3").toString(), downloader.permanentRedirectUrl);
    }

    @Test
    public void temporaryRedirectIsFollowedWithoutRecordingNewUrl() throws Exception {
        byte[] body = bytes(64);
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/temp.mp3")));
        server.enqueue(binaryResponse(body));

        Downloader downloader = run(mediaRequest("/old.mp3"));

        assertTrue(downloader.getResult().isSuccessful());
        assertNull(downloader.permanentRedirectUrl);
        assertArrayEquals(body, Files.readAllBytes(destination.toPath()));
    }

    @Test
    public void redirectChainIsFollowedToFinalBody() throws Exception {
        byte[] body = bytes(64);
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/second")));
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/third")));
        server.enqueue(binaryResponse(body));

        Downloader downloader = run(mediaRequest("/first"));

        assertTrue(downloader.getResult().isSuccessful());
        assertArrayEquals(body, Files.readAllBytes(destination.toPath()));
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void redirectToMissingResourceReportsNotFound() {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/gone")));
        server.enqueue(new MockResponse().setResponseCode(404));

        Downloader downloader = run(mediaRequest("/old.mp3"));

        assertEquals(DownloadError.ERROR_NOT_FOUND, downloader.getResult().getReason());
    }

    private Dispatcher basicAuthDispatcher(String expectedHeader, byte[] body) {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) {
                if (expectedHeader.equals(recordedRequest.getHeader("Authorization"))) {
                    return binaryResponse(body);
                }
                return new MockResponse().setResponseCode(401).addHeader("WWW-Authenticate", "Basic realm=\"feed\"");
            }
        };
    }

    @Test
    public void unauthorizedResponseIsRetriedWithBasicCredentialsFromRequest() throws Exception {
        byte[] body = bytes(100);
        server.setDispatcher(basicAuthDispatcher("Basic dXNlcjpwYXNz", body));

        Downloader downloader = run(mediaRequest("/private.mp3", "user", "pass"));

        assertTrue(downloader.getResult().isSuccessful());
        assertArrayEquals(body, Files.readAllBytes(destination.toPath()));
    }

    @Test
    public void nonAsciiCredentialsAreRetriedInIsoEncodingFirst() throws Exception {
        byte[] body = bytes(100);
        String isoHeader = "Basic " + Base64.getEncoder()
                .encodeToString("usér:päss".getBytes(StandardCharsets.ISO_8859_1));
        server.setDispatcher(basicAuthDispatcher(isoHeader, body));

        Downloader downloader = run(mediaRequest("/private.mp3", "usér", "päss"));

        assertTrue(downloader.getResult().isSuccessful());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void nonAsciiCredentialsFallBackToUtf8Encoding() throws Exception {
        byte[] body = bytes(100);
        String utf8Header = "Basic " + Base64.getEncoder()
                .encodeToString("usér:päss".getBytes(StandardCharsets.UTF_8));
        server.setDispatcher(basicAuthDispatcher(utf8Header, body));

        Downloader downloader = run(mediaRequest("/private.mp3", "usér", "päss"));

        assertTrue(downloader.getResult().isSuccessful());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void credentialsEmbeddedInUrlAreUsedForAuthorization() throws Exception {
        byte[] body = bytes(100);
        server.setDispatcher(basicAuthDispatcher("Basic dXNlcjpwYXNz", body));
        String source = server.url("/private.mp3").toString().replace("http://", "http://user:pass@");
        DownloadRequest request = new DownloadRequest(destination.getAbsolutePath(), source, "Episode", 1,
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, null, null, false, null, true);

        Downloader downloader = run(request);

        assertTrue(downloader.getResult().isSuccessful());
    }

    @Test
    public void wrongCredentialsEndInUnauthorizedError() {
        server.setDispatcher(basicAuthDispatcher("Basic dXNlcjpwYXNz", bytes(100)));

        Downloader downloader = run(mediaRequest("/private.mp3", "user", "wrong"));

        assertFalse(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.ERROR_UNAUTHORIZED, downloader.getResult().getReason());
        assertEquals("401", downloader.getResult().getReasonDetailed());
    }

    @Test
    public void missingCredentialsEndInUnauthorizedErrorWithoutRetry() {
        server.setDispatcher(basicAuthDispatcher("Basic dXNlcjpwYXNz", bytes(100)));

        Downloader downloader = run(mediaRequest("/private.mp3"));

        assertEquals(DownloadError.ERROR_UNAUTHORIZED, downloader.getResult().getReason());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void userInfoWithoutPasswordSeparatorIsNotRetried() {
        server.setDispatcher(basicAuthDispatcher("Basic dXNlcjpwYXNz", bytes(100)));
        String source = server.url("/private.mp3").toString().replace("http://", "http://token@");
        DownloadRequest request = new DownloadRequest(destination.getAbsolutePath(), source, "Episode", 1,
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, null, null, false, null, true);

        Downloader downloader = run(request);

        assertEquals(DownloadError.ERROR_UNAUTHORIZED, downloader.getResult().getReason());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void unauthorizedAfterRedirectIsRetriedAtRedirectTarget() throws Exception {
        byte[] body = bytes(100);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) {
                if ("/start".equals(recordedRequest.getPath())) {
                    return new MockResponse().setResponseCode(302).addHeader("Location", server.url("/protected"));
                }
                if ("Basic dXNlcjpwYXNz".equals(recordedRequest.getHeader("Authorization"))) {
                    return binaryResponse(body);
                }
                return new MockResponse().setResponseCode(401);
            }
        });

        Downloader downloader = run(mediaRequest("/start", "user", "pass"));

        assertTrue(downloader.getResult().isSuccessful());
        assertArrayEquals(body, Files.readAllBytes(destination.toPath()));
    }

    @Test
    public void connectionRefusedOnLoopbackIsReportedAsBlocked() throws Exception {
        String loopbackUrl = server.url("/episode.mp3").newBuilder().host("127.0.0.1").build().toString();
        DownloadRequest request = new DownloadRequest(destination.getAbsolutePath(), loopbackUrl, "Episode", 1,
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, null, null, false, null, true);
        server.shutdown();

        Downloader downloader = run(request);

        assertFalse(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.ERROR_IO_BLOCKED, downloader.getResult().getReason());
    }

    @Test
    public void destinationInMissingDirectoryReportsIoError() {
        server.enqueue(binaryResponse(bytes(10)));
        File missingDirectory = new File(temporaryFolder.getRoot(), "missing/dir/file.bin");
        DownloadRequest request = new DownloadRequest(missingDirectory.getAbsolutePath(),
                server.url("/episode.mp3").toString(), "Episode", 1, FeedMedia.FEEDFILETYPE_FEEDMEDIA,
                null, null, null, false, null, true);

        Downloader downloader = run(request);

        assertFalse(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.ERROR_IO_ERROR, downloader.getResult().getReason());
    }

    @Test
    public void unparseableSourceReportsMalformedUrl() {
        DownloadRequest request = new DownloadRequest(destination.getAbsolutePath(), "http://exa mple.com:bad/x",
                "Episode", 1, FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, null, null, false, null, true);

        Downloader downloader = run(request);

        assertFalse(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.ERROR_MALFORMED_URL, downloader.getResult().getReason());
    }

    @Test
    public void sourceWithUnencodedSpaceIsEncodedAndDownloaded() throws Exception {
        byte[] body = bytes(10);
        server.enqueue(binaryResponse(body));
        DownloadRequest request = new DownloadRequest(destination.getAbsolutePath(),
                server.url("/").toString() + "my episode.mp3", "Episode", 1, FeedMedia.FEEDFILETYPE_FEEDMEDIA,
                null, null, null, false, null, true);

        Downloader downloader = run(request);

        assertTrue(downloader.getResult().isSuccessful());
        assertEquals("/my%20episode.mp3", server.takeRequest().getPath());
        assertArrayEquals(body, Files.readAllBytes(destination.toPath()));
    }

    @Test
    public void factoryCreatesHttpDownloaderForHttpSources() {
        server.enqueue(binaryResponse(bytes(10)));

        Downloader downloader = new DefaultDownloaderFactory().create(mediaRequest("/episode.mp3"));

        assertNotNull(downloader);
        downloader.call();
        assertTrue(downloader.getResult().isSuccessful());
    }

    @Test
    public void factoryRejectsNonHttpSources() {
        DownloadRequest request = new DownloadRequest(destination.getAbsolutePath(), "ftp://example.com/a.mp3",
                "Episode", 1, FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, null, null, false, null, true);

        assertNull(new DefaultDownloaderFactory().create(request));
    }
}
