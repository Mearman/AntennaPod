package de.danoeh.antennapod.net.download.service.episode;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.service.DownloadIntegrationTestBase;
import de.danoeh.antennapod.net.download.service.R;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.awaitility.Awaitility;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

@Category(IntegrationTest.class)
public class EpisodeDownloadWorkerTest extends DownloadIntegrationTestBase {
    private static final int LAST_ATTEMPT = 2;

    private final List<MessageEvent> messages = new ArrayList<>();

    @Subscribe
    public void onMessage(MessageEvent event) {
        messages.add(event);
    }

    @Before
    public void registerMessageSubscriber() {
        EventBus.getDefault().register(this);
    }

    @After
    public void unregisterMessageSubscriber() {
        EventBus.getDefault().unregister(this);
    }

    private static byte[] bytes(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i % 251);
        }
        return data;
    }

    private static MockResponse audioResponse(byte[] body) {
        return new MockResponse().setBody(new Buffer().write(body)).addHeader("Content-Type", "audio/mpeg");
    }

    private EpisodeDownloadWorker workerFor(FeedMedia media, int runAttemptCount) {
        Data input = new Data.Builder().putLong(DownloadServiceInterface.WORK_DATA_MEDIA_ID, media.getId()).build();
        return new EpisodeDownloadWorker(context, workerParameters(input, runAttemptCount));
    }

    private ListenableWorker.Result runWorker(FeedMedia media, int runAttemptCount) {
        ListenableWorker.Result result = workerFor(media, runAttemptCount).doWork();
        DBWriter.tearDownTests();
        return result;
    }

    private void unsubscribeMessagesToUseNotifications() {
        EventBus.getDefault().unregister(this);
    }

    @Test
    public void unknownMediaIdFails() {
        Data input = new Data.Builder().putLong(DownloadServiceInterface.WORK_DATA_MEDIA_ID, 4711).build();

        ListenableWorker.Result result = new EpisodeDownloadWorker(context, workerParameters(input, 0)).doWork();

        assertEquals(ListenableWorker.Result.failure(), result);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void successfulDownloadStoresFileAndMarksMediaDownloaded() throws Exception {
        byte[] body = bytes(30_000);
        server.enqueue(audioResponse(body));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        ListenableWorker.Result result = runWorker(media, 0);

        assertEquals(ListenableWorker.Result.success(), result);
        FeedMedia stored = DBReader.getFeedMedia(media.getId());
        assertNotNull(stored);
        assertTrue(stored.isDownloaded());
        assertNotNull(stored.getLocalFileUrl());
        assertArrayEquals(body, Files.readAllBytes(new File(stored.getLocalFileUrl()).toPath()));
        assertEquals(body.length, stored.getSize());
        assertTrue(messages.isEmpty());
    }

    @Test
    public void successfulDownloadIsLoggedAsSuccessful() {
        server.enqueue(audioResponse(bytes(1000)));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        runWorker(media, 0);

        List<DownloadResult> log = DBReader.getDownloadLog();
        assertEquals(1, log.size());
        assertTrue(log.get(0).isSuccessful());
        assertEquals(media.getId(), log.get(0).getFeedfileId());
        assertEquals(FeedMedia.FEEDFILETYPE_FEEDMEDIA, log.get(0).getFeedfileType());
    }

    @Test
    public void successfulDownloadOfSubscribedFeedEnqueuesDownloadEpisodeAction() {
        server.enqueue(audioResponse(bytes(1000)));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        runWorker(media, 0);

        verify(synchronizationQueue).enqueueEpisodeAction(any());
    }

    @Test
    public void notFoundFailsImmediatelyAndRemovesPartialFile() {
        server.enqueue(new MockResponse().setResponseCode(404));
        FeedMedia media = saveEpisode(server.url("/missing.mp3").toString());

        ListenableWorker.Result result = runWorker(media, 0);

        assertEquals(ListenableWorker.Result.failure(), result);
        FeedMedia stored = DBReader.getFeedMedia(media.getId());
        assertFalse(stored.isDownloaded());
        assertFalse(new File(stored.getLocalFileUrl()).exists());
        List<DownloadResult> log = DBReader.getDownloadLog();
        assertEquals(1, log.size());
        assertEquals(DownloadError.ERROR_NOT_FOUND, log.get(0).getReason());
    }

    @Test
    public void unauthorizedAndForbiddenFailWithoutRetryEvenOnFirstAttempt() {
        int[] statuses = {401, 403};
        DownloadError[] reasons = {DownloadError.ERROR_UNAUTHORIZED, DownloadError.ERROR_FORBIDDEN};
        for (int i = 0; i < statuses.length; i++) {
            server.enqueue(new MockResponse().setResponseCode(statuses[i]));
            FeedMedia media = saveEpisode(server.url("/protected.mp3").toString());

            ListenableWorker.Result result = runWorker(media, 0);

            assertEquals("status " + statuses[i], ListenableWorker.Result.failure(), result);
            assertEquals(reasons[i], DBReader.getDownloadLog().get(0).getReason());
        }
    }

    @Test
    public void serverErrorIsRetriedOnFirstAttempt() {
        server.enqueue(new MockResponse().setResponseCode(500));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        ListenableWorker.Result result = runWorker(media, 0);

        assertEquals(ListenableWorker.Result.retry(), result);
        assertEquals(DownloadError.ERROR_HTTP_DATA_ERROR, DBReader.getDownloadLog().get(0).getReason());
    }

    @Test
    public void serverErrorAnnouncesRetryMessageBeforeLastAttempt() {
        server.enqueue(new MockResponse().setResponseCode(500));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        runWorker(media, 1);

        assertEquals(1, messages.size());
        assertEquals(context.getString(R.string.download_error_retrying, "Episode One"), messages.get(0).message);
    }

    @Test
    public void serverErrorFailsOnLastAttemptWithFinalMessage() {
        server.enqueue(new MockResponse().setResponseCode(500));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        ListenableWorker.Result result = runWorker(media, LAST_ATTEMPT);

        assertEquals(ListenableWorker.Result.failure(), result);
        assertEquals(2, messages.size());
        assertEquals(context.getString(R.string.download_error_not_retrying, "Episode One"), messages.get(0).message);
        assertEquals(context.getString(R.string.download_error_not_retrying, "Episode One"), messages.get(1).message);
    }

    @Test
    public void longEpisodeTitlesAreTruncatedInMessages() {
        server.enqueue(new MockResponse().setResponseCode(500));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());
        media.getItem().setTitle("An extraordinarily long episode title");
        DBWriter.setFeedItem(media.getItem(), false);
        DBWriter.tearDownTests();

        runWorker(media, 1);

        assertEquals(1, messages.size());
        assertEquals(context.getString(R.string.download_error_retrying, "An extraordinarily …"),
                messages.get(0).message);
    }

    @Test
    public void failureWithoutMessageSubscriberPostsErrorNotification() {
        unsubscribeMessagesToUseNotifications();
        shadowOf((Application) context.getApplicationContext())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        server.enqueue(new MockResponse().setResponseCode(404));
        FeedMedia media = saveEpisode(server.url("/missing.mp3").toString());

        runWorker(media, 0);
        EventBus.getDefault().register(this);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = shadowOf(manager).getNotification(R.id.notification_download_report);
        assertNotNull(notification);
        assertEquals(context.getString(R.string.episode_download_failed),
                shadowOf(notification).getContentTitle().toString());
        assertEquals(context.getString(R.string.download_error_tap_for_details),
                shadowOf(notification).getContentText().toString());
        assertTrue(messages.isEmpty());
    }

    @Test
    public void failureWithoutNotificationPermissionPostsNothing() {
        unsubscribeMessagesToUseNotifications();
        server.enqueue(new MockResponse().setResponseCode(404));
        FeedMedia media = saveEpisode(server.url("/missing.mp3").toString());

        runWorker(media, 0);
        EventBus.getDefault().register(this);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNull(shadowOf(manager).getNotification(R.id.notification_download_report));
    }

    @Test
    public void rangeNotSatisfiableRestartsDownloadFromScratch() throws Exception {
        File partial = temporaryFolder.newFile("partial.mp3");
        Files.write(partial.toPath(), bytes(500));
        server.enqueue(new MockResponse().setResponseCode(416));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());
        media.setLocalFileUrl(partial.getAbsolutePath());
        DBWriter.setMediaDownloadInformation(media);
        DBWriter.tearDownTests();

        ListenableWorker.Result result = runWorker(media, 0);

        assertEquals(ListenableWorker.Result.retry(), result);
        assertEquals("bytes=500-", server.takeRequest().getHeader("Range"));
        assertFalse(partial.exists());
        assertEquals(1, messages.size());
        assertEquals(context.getString(R.string.download_error_retrying, "Episode One"), messages.get(0).message);
        assertTrue(DBReader.getDownloadLog().isEmpty());
    }

    @Test
    public void partiallyDownloadedFileIsResumedWithRangeRequest() throws Exception {
        byte[] full = bytes(4000);
        File partial = temporaryFolder.newFile("partial.mp3");
        Files.write(partial.toPath(), Arrays.copyOfRange(full, 0, 1500));
        server.enqueue(audioResponse(Arrays.copyOfRange(full, 1500, 4000)).setResponseCode(206)
                .addHeader("Content-Range", "bytes 1500-3999/4000"));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());
        media.setLocalFileUrl(partial.getAbsolutePath());
        DBWriter.setMediaDownloadInformation(media);
        DBWriter.tearDownTests();

        ListenableWorker.Result result = runWorker(media, 0);

        assertEquals(ListenableWorker.Result.success(), result);
        RecordedRequest recorded = server.takeRequest();
        assertEquals("bytes=1500-", recorded.getHeader("Range"));
        assertArrayEquals(full, Files.readAllBytes(partial.toPath()));
        assertEquals(partial.getAbsolutePath(), DBReader.getFeedMedia(media.getId()).getLocalFileUrl());
    }

    @Test
    public void workerCreatesDestinationFileAndStoresItsPathBeforeDownloading() {
        server.enqueue(new MockResponse().setResponseCode(500));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        runWorker(media, 0);

        String localFileUrl = DBReader.getFeedMedia(media.getId()).getLocalFileUrl();
        assertNotNull(localFileUrl);
        assertTrue(new File(localFileUrl).getName().endsWith("." + media.getId() + ".mp3"));
    }

    @Test
    public void foregroundInfoShowsOngoingDownloadNotification() throws Exception {
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        ForegroundInfo info = workerFor(media, 0).getForegroundInfoAsync().get();

        assertEquals(R.id.notification_downloading, info.getNotificationId());
        assertEquals(context.getString(R.string.download_notification_title_episodes),
                shadowOf(info.getNotification()).getContentTitle().toString());
        assertTrue((info.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0);
    }

    @Test
    public void stoppingWorkerBeforeItStartedDoesNotBreakLaterRun() {
        server.enqueue(audioResponse(bytes(100)));
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());
        EpisodeDownloadWorker worker = workerFor(media, 0);

        worker.onStopped();
        ListenableWorker.Result result = worker.doWork();
        DBWriter.tearDownTests();

        assertEquals(ListenableWorker.Result.success(), result);
        assertTrue(DBReader.getFeedMedia(media.getId()).isDownloaded());
    }

    @Test
    public void ongoingNotificationShowsProgressWhileDownloadingAndIsRemovedAfterwards() {
        shadowOf((Application) context.getApplicationContext())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        AtomicReference<String> textWhileDownloading = new AtomicReference<>();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Awaitility.await().atMost(10, TimeUnit.SECONDS)
                        .until(() -> shadowOf(manager).getNotification(R.id.notification_downloading) != null);
                textWhileDownloading.set(shadowOf(shadowOf(manager).getNotification(R.id.notification_downloading))
                        .getContentText().toString());
                return audioResponse(bytes(1000));
            }
        });
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        ListenableWorker.Result result = runWorker(media, 0);

        assertEquals(ListenableWorker.Result.success(), result);
        assertEquals("Episode One (0%)", textWhileDownloading.get());
        assertNull(shadowOf(manager).getNotification(R.id.notification_downloading));
    }

    @Test
    public void stoppingWorkerDuringDownloadEndsSuccessfullyWithoutMarkingMediaDownloaded() throws Exception {
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());
        EpisodeDownloadWorker worker = workerFor(media, 0);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                worker.onStopped();
                return audioResponse(bytes(50_000));
            }
        });

        ListenableWorker.Result result = worker.doWork();
        DBWriter.tearDownTests();

        assertEquals(ListenableWorker.Result.success(), result);
        assertFalse(DBReader.getFeedMedia(media.getId()).isDownloaded());
        assertTrue(DBReader.getDownloadLog().isEmpty());
        assertTrue(messages.isEmpty());
    }
}
