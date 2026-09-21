package de.test.antennapod.service.download;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.service.episode.EpisodeDownloadWorker;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.test.antennapod.util.service.download.HTTPBin;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class EpisodeDownloadWorkerTest {
    private static final int LAST_RUN_ATTEMPT = 2;

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private final List<MessageEvent> messages = new CopyOnWriteArrayList<>();
    private Context context;
    private Feed feed;
    private FeedMedia media;
    private File source;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
        feed = fixture.subscribe("Worker", 1);
        media = feed.getItemAtIndex(0).getMedia();
        source = fixture.server().accessFile(Integer.parseInt(media.getDownloadUrl()
                .substring(media.getDownloadUrl().lastIndexOf('/') + 1)));
        EventBus.getDefault().register(this);
    }

    @After
    public void tearDown() throws Exception {
        EventBus.getDefault().unregister(this);
        fixture.tearDown();
    }

    @Subscribe
    public void onMessage(MessageEvent event) {
        messages.add(event);
    }

    private ListenableWorker.Result runWorker(int runAttempt) throws Exception {
        ListenableWorker worker = TestListenableWorkerBuilder.from(context, EpisodeDownloadWorker.class)
                .setInputData(new Data.Builder().putLong(DownloadServiceInterface.WORK_DATA_MEDIA_ID, media.getId())
                        .build())
                .setRunAttemptCount(runAttempt)
                .build();
        return worker.startWork().get(60, TimeUnit.SECONDS);
    }

    private void pointMediaAt(String url) {
        FeedMedia stored = DownloadTestFixture.reload(media);
        FeedMedia changed = new FeedMedia(stored.getId(), stored.getItem(), 0, 0, stored.getSize(),
                stored.getMimeType(), null, url, 0, null, 0, 0);
        DBWriter.setFeedMedia(changed);
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> url.equals(DownloadTestFixture.reload(media).getDownloadUrl()));
        media = DownloadTestFixture.reload(media);
    }

    private DownloadResult awaitLogEntry(DownloadError reason) {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> findLogEntry(reason) != null);
        return findLogEntry(reason);
    }

    private DownloadResult findLogEntry(DownloadError reason) {
        for (DownloadResult result : DBReader.getDownloadLog()) {
            if (result.getReason() == reason) {
                return result;
            }
        }
        return null;
    }

    private List<String> messageTexts() {
        List<String> texts = new ArrayList<>();
        for (MessageEvent message : messages) {
            texts.add(message.message);
        }
        return texts;
    }

    private void assertNotDownloaded() {
        assertFalse(DownloadTestFixture.reload(media).isDownloaded());
    }

    private void assertNoFileLeftBehind() {
        String path = DownloadTestFixture.reload(media).getLocalFileUrl();
        assertFalse(path != null && new File(path).exists());
    }

    @Test
    public void successfulDownloadStoresTheEpisode() throws Exception {
        assertEquals(ListenableWorker.Result.success(), runWorker(0));

        DownloadTestFixture.awaitDownloaded(media);
        FeedMedia stored = DownloadTestFixture.reload(media);
        File downloaded = new File(stored.getLocalFileUrl());
        assertTrue(downloaded.exists());
        assertArrayEquals(FileUtils.readFileToByteArray(source), FileUtils.readFileToByteArray(downloaded));
        assertEquals(downloaded.length(), stored.getSize());
        assertTrue(stored.getDuration() > 0);
        assertTrue(stored.getDownloadDate() > 0);
        assertFalse(stored.getItem().isAutoDownloadEnabled());
        DownloadResult logged = awaitLogEntry(DownloadError.SUCCESS);
        assertEquals(stored.getEpisodeTitle(), logged.getTitle());
        assertTrue(logged.isSuccessful());
    }

    @Test
    public void episodeCanBeDownloadedAgainAfterItsFileWasDeleted() throws Exception {
        assertEquals(ListenableWorker.Result.success(), runWorker(0));
        DownloadTestFixture.awaitDownloaded(media);
        String firstPath = DownloadTestFixture.reload(media).getLocalFileUrl();

        DBWriter.deleteFeedMediaOfItem(context, DownloadTestFixture.reload(media)).get();

        assertNotDownloaded();
        assertFalse(new File(firstPath).exists());
        assertEquals(ListenableWorker.Result.success(), runWorker(0));
        DownloadTestFixture.awaitDownloaded(media);
        assertTrue(new File(DownloadTestFixture.reload(media).getLocalFileUrl()).exists());
        assertEquals(2, fixture.requestsFor(media.getDownloadUrl()).size());
    }

    @Test
    public void notFoundFailsWithoutRetry() throws Exception {
        pointMediaAt(fixture.url("/status/404"));

        assertEquals(ListenableWorker.Result.failure(), runWorker(0));

        assertNotDownloaded();
        assertNoFileLeftBehind();
        DownloadResult logged = awaitLogEntry(DownloadError.ERROR_NOT_FOUND);
        assertFalse(logged.isSuccessful());
        assertEquals("404", logged.getReasonDetailed());
    }

    @Test
    public void forbiddenFailsWithoutRetry() throws Exception {
        pointMediaAt(fixture.url("/status/403"));

        assertEquals(ListenableWorker.Result.failure(), runWorker(0));

        assertNotDownloaded();
        assertNoFileLeftBehind();
        assertEquals("403", awaitLogEntry(DownloadError.ERROR_FORBIDDEN).getReasonDetailed());
    }

    @Test
    public void unauthorizedWithoutCredentialsFailsWithoutRetry() throws Exception {
        pointMediaAt(fixture.url("/basic-auth-file/user/secret/" + fixture.fileId(source)));

        assertEquals(ListenableWorker.Result.failure(), runWorker(0));

        assertNotDownloaded();
        assertNoFileLeftBehind();
        awaitLogEntry(DownloadError.ERROR_UNAUTHORIZED);
        assertFalse(fixture.server().getRequestsForPrefix("/basic-auth-file").isEmpty());
    }

    @Test
    public void serverErrorIsRetriedAndFailsOnTheLastAttempt() throws Exception {
        pointMediaAt(fixture.url("/status/500"));

        assertEquals(ListenableWorker.Result.retry(), runWorker(0));
        assertEquals(ListenableWorker.Result.failure(), runWorker(LAST_RUN_ATTEMPT));

        assertNotDownloaded();
        assertNoFileLeftBehind();
        assertEquals("500", awaitLogEntry(DownloadError.ERROR_HTTP_DATA_ERROR).getReasonDetailed());
        assertEquals(2, fixture.server().getRequestsForPrefix("/status/500").size());
    }

    @Test
    public void retryAnnouncesThatTheDownloadWillBeTriedAgain() throws Exception {
        pointMediaAt(fixture.url("/status/500"));

        runWorker(0);
        runWorker(LAST_RUN_ATTEMPT);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> messages.size() >= 2);
        assertEquals(context.getString(R.string.download_error_retrying,
                media.getEpisodeTitle()), messages.get(0).message);
        assertEquals(context.getString(R.string.download_error_not_retrying,
                media.getEpisodeTitle()), messages.get(messages.size() - 1).message);
    }

    @Test
    public void textResponseIsNotAcceptedAsEpisode() throws Exception {
        pointMediaAt(fixture.url("/status/200"));

        assertEquals(ListenableWorker.Result.retry(), runWorker(0));
        assertEquals(ListenableWorker.Result.failure(), runWorker(LAST_RUN_ATTEMPT));

        assertNotDownloaded();
        assertNoFileLeftBehind();
        awaitLogEntry(DownloadError.ERROR_FILE_TYPE);
    }

    @Test
    public void incompleteBodyIsReportedAsWrongSize() throws Exception {
        pointMediaAt(fixture.url("/truncated/" + fixture.fileId(source)));

        assertEquals(ListenableWorker.Result.failure(), runWorker(LAST_RUN_ATTEMPT));

        assertNotDownloaded();
        assertNoFileLeftBehind();
        assertNotNull(awaitLogEntry(DownloadError.ERROR_IO_WRONG_SIZE).getReasonDetailed());
    }

    @Test
    public void unreachableServerIsReportedAsBlockedAddress() throws Exception {
        String url = fixture.url("/files/" + fixture.fileId(source));
        pointMediaAt(url);
        fixture.server().stop();

        assertEquals(ListenableWorker.Result.failure(), runWorker(0));

        assertNotDownloaded();
        assertNoFileLeftBehind();
        awaitLogEntry(DownloadError.ERROR_IO_BLOCKED);
    }

    @Test
    public void malformedUrlIsRetriedAndReported() throws Exception {
        pointMediaAt("http://[not a url");

        assertEquals(ListenableWorker.Result.retry(), runWorker(0));

        assertNotDownloaded();
        awaitLogEntry(DownloadError.ERROR_MALFORMED_URL);
    }

    @Test
    public void temporaryRedirectIsFollowed() throws Exception {
        pointMediaAt(fixture.url("/moved/302/" + fixture.fileId(source)));

        assertEquals(ListenableWorker.Result.success(), runWorker(0));

        DownloadTestFixture.awaitDownloaded(media);
        assertEquals(1, fixture.server().getRequestsForPrefix("/moved/302").size());
        assertEquals(1, fixture.server().getRequestsForPrefix("/files").size());
    }

    @Test
    public void permanentRedirectIsFollowed() throws Exception {
        pointMediaAt(fixture.url("/moved/301/" + fixture.fileId(source)));

        assertEquals(ListenableWorker.Result.success(), runWorker(0));

        DownloadTestFixture.awaitDownloaded(media);
        assertArrayEquals(FileUtils.readFileToByteArray(source),
                FileUtils.readFileToByteArray(new File(DownloadTestFixture.reload(media).getLocalFileUrl())));
    }

    @Test
    public void feedCredentialsAreUsedWhenTheServerAsksForThem() throws Exception {
        feed.getPreferences().setUsername("user");
        feed.getPreferences().setPassword("secret");
        DBWriter.setFeedPreferences(feed.getPreferences()).get();
        pointMediaAt(fixture.url("/basic-auth-file/user/secret/" + fixture.fileId(source)));

        assertEquals(ListenableWorker.Result.success(), runWorker(0));

        DownloadTestFixture.awaitDownloaded(media);
        List<HTTPBin.RecordedRequest> requests = fixture.server().getRequestsForPrefix("/basic-auth-file");
        assertFalse(requests.get(0).headers.containsKey("authorization"));
        assertTrue(requests.get(requests.size() - 1).headers.containsKey("authorization"));
    }

    @Test
    public void wrongFeedCredentialsAreRejected() throws Exception {
        feed.getPreferences().setUsername("user");
        feed.getPreferences().setPassword("wrong");
        DBWriter.setFeedPreferences(feed.getPreferences()).get();
        pointMediaAt(fixture.url("/basic-auth-file/user/secret/" + fixture.fileId(source)));

        assertEquals(ListenableWorker.Result.failure(), runWorker(0));

        assertNotDownloaded();
        assertNoFileLeftBehind();
        awaitLogEntry(DownloadError.ERROR_UNAUTHORIZED);
    }

    @Test
    public void partialFileIsResumedWithARangeRequest() throws Exception {
        byte[] full = FileUtils.readFileToByteArray(source);
        int alreadyDownloaded = full.length / 3;
        File partial = fixture.file("partial-episode.mp3");
        FileUtils.writeByteArrayToFile(partial, full, 0, alreadyDownloaded);
        FeedMedia stored = DownloadTestFixture.reload(media);
        stored.setLocalFileUrl(partial.getAbsolutePath());
        DBWriter.setMediaDownloadInformation(stored).get();

        assertEquals(ListenableWorker.Result.success(), runWorker(0));

        DownloadTestFixture.awaitDownloaded(media);
        File downloaded = new File(DownloadTestFixture.reload(media).getLocalFileUrl());
        assertEquals(partial.getAbsolutePath(), downloaded.getAbsolutePath());
        assertArrayEquals(full, FileUtils.readFileToByteArray(downloaded));
        List<HTTPBin.RecordedRequest> requests =
                fixture.server().getRequestsForPrefix("/files/" + fixture.fileId(source));
        assertEquals("bytes=" + alreadyDownloaded + "-", requests.get(0).headers.get("range"));
    }

    @Test
    public void unsatisfiableRangeRestartsTheDownloadFromTheBeginning() throws Exception {
        byte[] full = FileUtils.readFileToByteArray(source);
        File oversized = fixture.file("oversized-episode.mp3");
        FileUtils.writeByteArrayToFile(oversized, new byte[full.length + 10]);
        FeedMedia stored = DownloadTestFixture.reload(media);
        stored.setLocalFileUrl(oversized.getAbsolutePath());
        DBWriter.setMediaDownloadInformation(stored).get();

        assertEquals(ListenableWorker.Result.retry(), runWorker(0));

        assertFalse(oversized.exists());
        assertEquals(ListenableWorker.Result.success(), runWorker(1));
        DownloadTestFixture.awaitDownloaded(media);
        assertArrayEquals(full,
                FileUtils.readFileToByteArray(new File(DownloadTestFixture.reload(media).getLocalFileUrl())));
        assertEquals(Collections.singletonList(context.getString(R.string.download_error_retrying,
                media.getEpisodeTitle())), messageTexts());
    }

    @Test
    public void missingEpisodeMakesTheWorkerFail() throws Exception {
        ListenableWorker worker = TestListenableWorkerBuilder.from(context, EpisodeDownloadWorker.class)
                .setInputData(new Data.Builder().putLong(DownloadServiceInterface.WORK_DATA_MEDIA_ID, -1).build())
                .build();

        assertEquals(ListenableWorker.Result.failure(), worker.startWork().get(30, TimeUnit.SECONDS));
    }
}
