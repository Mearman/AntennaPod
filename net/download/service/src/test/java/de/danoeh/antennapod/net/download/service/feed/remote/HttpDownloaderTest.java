package de.danoeh.antennapod.net.download.service.feed.remote;

import android.os.Bundle;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class HttpDownloaderTest {
    private static final String UNWRITABLE_DESTINATION = "/nonexistent-directory/download.tmp";
    private static final String URL = "http://example.com/podcast.mp3";

    private interface Responder {
        Response respond(Request request) throws IOException;
    }

    private final List<Request> requests = new ArrayList<>();
    private Responder responder;
    private MockedStatic<AntennapodHttpClient> httpClient;

    @Before
    public void setUp() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    requests.add(chain.request());
                    return responder.respond(chain.request());
                })
                .build();
        httpClient = Mockito.mockStatic(AntennapodHttpClient.class);
        httpClient.when(AntennapodHttpClient::getHttpClient).thenReturn(client);
    }

    @After
    public void tearDown() {
        httpClient.close();
    }

    private static Response.Builder response(Request request, int code) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("status " + code);
    }

    private static Response body(Request request, int code, String contentType, String content) {
        return response(request, code)
                .header("Content-Type", contentType)
                .body(ResponseBody.create(content.getBytes(StandardCharsets.UTF_8), MediaType.parse(contentType)))
                .build();
    }

    private static DownloadRequest request(String source, int type, String lastModified) {
        return new DownloadRequest(UNWRITABLE_DESTINATION, source, "title", 1, type, lastModified, null, null, false,
                new Bundle(), true);
    }

    private static DownloadRequest mediaRequest() {
        return request(URL, FeedMedia.FEEDFILETYPE_FEEDMEDIA, null);
    }

    private static DownloadRequest feedRequest(String lastModified) {
        return request("http://example.com/feed.xml", Feed.FEEDFILETYPE_FEED, lastModified);
    }

    private HttpDownloader download(DownloadRequest request) {
        HttpDownloader downloader = new HttpDownloader(request);
        downloader.call();
        return downloader;
    }

    private void respondWithStatus(int code) {
        responder = req -> response(req, code).body(ResponseBody.create(new byte[0], null)).build();
    }

    private void assertFailed(HttpDownloader downloader, DownloadError reason, String details) {
        DownloadResult result = downloader.getResult();
        assertFalse(result.isSuccessful());
        assertEquals(reason, result.getReason());
        assertEquals(details, result.getReasonDetailed());
    }

    private static String httpDate(long timeMillis) {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date(timeMillis));
    }

    @Test
    public void unauthorizedResponseFailsWithUnauthorized() {
        respondWithStatus(401);
        assertFailed(download(mediaRequest()), DownloadError.ERROR_UNAUTHORIZED, "401");
    }

    @Test
    public void forbiddenResponseFailsWithForbidden() {
        respondWithStatus(403);
        assertFailed(download(mediaRequest()), DownloadError.ERROR_FORBIDDEN, "403");
    }

    @Test
    public void notFoundResponseFailsWithNotFound() {
        respondWithStatus(404);
        assertFailed(download(mediaRequest()), DownloadError.ERROR_NOT_FOUND, "404");
    }

    @Test
    public void goneResponseFailsWithNotFound() {
        respondWithStatus(410);
        assertFailed(download(mediaRequest()), DownloadError.ERROR_NOT_FOUND, "410");
    }

    @Test
    public void serverErrorFailsWithHttpDataErrorAndStatusCode() {
        respondWithStatus(503);
        assertFailed(download(mediaRequest()), DownloadError.ERROR_HTTP_DATA_ERROR, "503");
    }

    @Test
    public void unsatisfiableRangeFailsWithHttpDataErrorAndStatusCode() {
        respondWithStatus(416);
        assertFailed(download(mediaRequest()), DownloadError.ERROR_HTTP_DATA_ERROR, "416");
    }

    @Test
    public void notModifiedResponseCancelsTheDownload() {
        respondWithStatus(304);

        HttpDownloader downloader = download(feedRequest("etag-1"));

        assertTrue(downloader.cancelled);
        assertFalse(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.ERROR_DOWNLOAD_CANCELLED, downloader.getResult().getReason());
    }

    @Test
    public void smallTextResponseForMediaFailsWithFileTypeError() {
        responder = req -> body(req, 200, "text/html", "<html>Sorry, not an episode</html>");

        HttpDownloader downloader = download(mediaRequest());

        assertEquals(DownloadError.ERROR_FILE_TYPE, downloader.getResult().getReason());
        assertFalse(downloader.getResult().isSuccessful());
    }

    @Test
    public void textResponseForFeedIsNotRejectedAsWrongFileType() {
        responder = req -> body(req, 200, "text/xml", "<rss></rss>");

        HttpDownloader downloader = download(feedRequest(null));

        assertNotEquals(DownloadError.ERROR_FILE_TYPE, downloader.getResult().getReason());
    }

    @Test
    public void failureToCreateTheDestinationFileFailsWithIoError() {
        responder = req -> body(req, 200, "audio/mpeg", "audio data");

        HttpDownloader downloader = download(mediaRequest());

        assertFalse(downloader.getResult().isSuccessful());
        assertEquals(DownloadError.ERROR_IO_ERROR, downloader.getResult().getReason());
    }

    @Test
    public void mediaRequestsDisableTransparentCompressionAndCaching() {
        respondWithStatus(404);

        download(mediaRequest());

        Request sent = requests.get(0);
        assertEquals("identity", sent.header("Accept-Encoding"));
        assertEquals("no-cache", sent.header("Cache-Control"));
    }

    @Test
    public void feedRequestsAreNeverStoredInTheHttpCache() {
        respondWithStatus(404);

        download(feedRequest(null));

        Request sent = requests.get(0);
        assertNull(sent.header("Accept-Encoding"));
        assertEquals("no-store", sent.header("Cache-Control"));
    }

    @Test
    public void insecureRequestsAskForUpgrade() {
        respondWithStatus(404);

        download(mediaRequest());

        assertEquals("1", requests.get(0).header("Upgrade-Insecure-Requests"));
    }

    @Test
    public void secureRequestsDoNotAskForUpgrade() {
        respondWithStatus(404);

        download(request("https://example.com/podcast.mp3", FeedMedia.FEEDFILETYPE_FEEDMEDIA, null));

        assertNull(requests.get(0).header("Upgrade-Insecure-Requests"));
    }

    @Test
    public void recentLastModifiedDateIsSentAsIfModifiedSince() {
        respondWithStatus(404);
        String recent = httpDate(System.currentTimeMillis() - 60 * 60 * 1000);

        download(feedRequest(recent));

        assertEquals(recent, requests.get(0).header("If-Modified-Since"));
        assertNull(requests.get(0).header("If-None-Match"));
    }

    @Test
    public void oldLastModifiedDateIsNotSent() {
        respondWithStatus(404);

        download(feedRequest("Wed, 21 Oct 2015 07:28:00 +0000"));

        assertNull(requests.get(0).header("If-Modified-Since"));
        assertNull(requests.get(0).header("If-None-Match"));
    }

    @Test
    public void lastModifiedValueThatIsNoDateIsSentAsIfNoneMatch() {
        respondWithStatus(404);

        download(feedRequest("etag-value"));

        assertEquals("etag-value", requests.get(0).header("If-None-Match"));
        assertNull(requests.get(0).header("If-Modified-Since"));
    }

    @Test
    public void noConditionalHeadersAreSentWithoutLastModified() {
        respondWithStatus(404);

        download(feedRequest(null));

        assertNull(requests.get(0).header("If-Modified-Since"));
        assertNull(requests.get(0).header("If-None-Match"));
    }

    @Test
    public void requestIsTaggedWithTheDownloadRequest() {
        respondWithStatus(404);
        DownloadRequest downloadRequest = mediaRequest();

        download(downloadRequest);

        assertEquals(downloadRequest, requests.get(0).tag());
    }

    @Test
    public void socketTimeoutFailsWithConnectionError() {
        responder = req -> {
            throw new SocketTimeoutException("timed out");
        };
        assertFailed(download(mediaRequest()), DownloadError.ERROR_CONNECTION_ERROR, "timed out");
    }

    @Test
    public void unknownHostFailsWithUnknownHost() {
        responder = req -> {
            throw new UnknownHostException("example.com");
        };
        assertFailed(download(mediaRequest()), DownloadError.ERROR_UNKNOWN_HOST, "example.com");
    }

    @Test
    public void connectionToLoopbackAddressFailsAsBlocked() {
        responder = req -> {
            throw new IOException("failed to connect to /127.0.0.1 (port 80)");
        };
        assertEquals(DownloadError.ERROR_IO_BLOCKED, download(mediaRequest()).getResult().getReason());
    }

    @Test
    public void untrustedCertificateFailsWithCertificateError() {
        responder = req -> {
            throw new IOException("Trust anchor for certification path not found.");
        };
        assertEquals(DownloadError.ERROR_CERTIFICATE, download(mediaRequest()).getResult().getReason());
    }

    @Test
    public void outOfSpaceIoExceptionFailsWithNotEnoughSpace() {
        responder = req -> {
            throw new IOException("write failed: ENOSPC (No space left on device)");
        };
        assertEquals(DownloadError.ERROR_NOT_ENOUGH_SPACE, download(mediaRequest()).getResult().getReason());
    }

    @Test
    public void otherIoExceptionFailsWithIoErrorAndMessage() {
        responder = req -> {
            throw new IOException("connection reset");
        };
        assertFailed(download(mediaRequest()), DownloadError.ERROR_IO_ERROR, "connection reset");
    }

    @Test
    public void malformedUrlFailsWithMalformedUrl() {
        respondWithStatus(200);
        HttpDownloader downloader = download(request("ht!tp://example.com/a.mp3", FeedMedia.FEEDFILETYPE_FEEDMEDIA,
                null));
        assertEquals(DownloadError.ERROR_MALFORMED_URL, downloader.getResult().getReason());
        assertTrue(requests.isEmpty());
    }

    @Test
    public void protocolErrorIsRetriedOnceWithHttp11() {
        responder = req -> {
            if (requests.size() == 1) {
                throw new IOException("stream was reset: PROTOCOL_ERROR");
            }
            return response(req, 404).body(ResponseBody.create(new byte[0], null)).build();
        };

        HttpDownloader downloader = download(mediaRequest());

        assertEquals(2, requests.size());
        assertFailed(downloader, DownloadError.ERROR_NOT_FOUND, "404");
    }

    @Test
    public void permanentRedirectIsRememberedForTheCaller() {
        responder = req -> {
            Response moved = response(req, 301).header("Location", "http://example.com/new.mp3").build();
            Request followUp = req.newBuilder().url("http://example.com/new.mp3").build();
            return body(followUp, 200, "audio/mpeg", "audio data").newBuilder().priorResponse(moved).build();
        };

        HttpDownloader downloader = download(mediaRequest());

        assertEquals("http://example.com/new.mp3", downloader.permanentRedirectUrl);
    }

    @Test
    public void downloaderIsFinishedAfterFailureToo() {
        respondWithStatus(404);
        HttpDownloader downloader = new HttpDownloader(mediaRequest());
        assertFalse(downloader.isFinished());

        downloader.call();

        assertTrue(downloader.isFinished());
    }
}
