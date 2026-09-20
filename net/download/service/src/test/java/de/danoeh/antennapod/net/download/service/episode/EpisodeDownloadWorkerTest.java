package de.danoeh.antennapod.net.download.service.episode;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker;
import androidx.work.ProgressUpdater;
import androidx.work.WorkerParameters;
import com.google.common.util.concurrent.Futures;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.service.R;
import de.danoeh.antennapod.net.download.service.feed.remote.DefaultDownloaderFactory;
import de.danoeh.antennapod.net.download.service.feed.remote.Downloader;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequestBuilder;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequestCreator;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class EpisodeDownloadWorkerTest {
    private static final String DESTINATION = "/nonexistent-directory/episode.mp3";
    private static final long MEDIA_ID = 8;
    private static final String TITLE = "Episode One";

    private final Context context = RuntimeEnvironment.getApplication();
    private final Downloader downloader = Mockito.mock(Downloader.class);
    private final List<MessageEvent> messages = new ArrayList<>();
    private final Subscriber subscriber = new Subscriber(messages);
    private MockedStatic<DBReader> reader;
    private MockedStatic<DBWriter> writer;
    private MockedStatic<DownloadRequestCreator> requestCreator;
    private MockedConstruction<DefaultDownloaderFactory> factory;
    private MockedConstruction<MediaDownloadedHandler> completionHandler;
    private FeedMedia media;
    private DownloadRequest downloadRequest;
    private DownloadResult handlerStatus;

    public static class Subscriber {
        private final List<MessageEvent> messages;

        Subscriber(List<MessageEvent> messages) {
            this.messages = messages;
        }

        @Subscribe
        public void onMessage(MessageEvent event) {
            messages.add(event);
        }
    }

    @Before
    public void setUp() {
        Feed feed = new Feed("http://example.com/feed.xml", null, "Feed");
        FeedItem item = new FeedItem(3, TITLE, "guid", "http://example.com/link", new Date(0), FeedItem.UNPLAYED, feed);
        media = new FeedMedia(MEDIA_ID, item, 0, 0, 0, "audio/mpeg", null, "http://example.com/a.mp3", 0, null, 0, 0);
        item.setMedia(media);
        downloadRequest = new DownloadRequestBuilder(DESTINATION, media).build();
        handlerStatus = new DownloadResult(TITLE, MEDIA_ID, FeedMedia.FEEDFILETYPE_FEEDMEDIA, true,
                DownloadError.SUCCESS, null);

        reader = Mockito.mockStatic(DBReader.class);
        writer = Mockito.mockStatic(DBWriter.class);
        requestCreator = Mockito.mockStatic(DownloadRequestCreator.class);
        reader.when(() -> DBReader.getFeedMedia(MEDIA_ID)).thenReturn(media);
        requestCreator.when(() -> DownloadRequestCreator.create(any(FeedMedia.class)))
                .thenAnswer(invocation -> new DownloadRequestBuilder(DESTINATION, media));
        factory = Mockito.mockConstruction(DefaultDownloaderFactory.class, (mock, ctx) ->
                Mockito.when(mock.create(any(DownloadRequest.class))).thenReturn(downloader));
        completionHandler = Mockito.mockConstruction(MediaDownloadedHandler.class, (mock, ctx) ->
                Mockito.when(mock.getUpdatedStatus()).thenReturn(handlerStatus));
        Mockito.when(downloader.getDownloadRequest()).thenReturn(downloadRequest);
    }

    @After
    public void tearDown() {
        if (EventBus.getDefault().isRegistered(subscriber)) {
            EventBus.getDefault().unregister(subscriber);
        }
        completionHandler.close();
        factory.close();
        requestCreator.close();
        writer.close();
        reader.close();
    }

    private static DownloadResult failure(DownloadError reason, String details) {
        return new DownloadResult(TITLE, MEDIA_ID, FeedMedia.FEEDFILETYPE_FEEDMEDIA, false, reason, details);
    }

    private EpisodeDownloadWorker worker(long mediaId, int runAttempt) {
        WorkerParameters params = Mockito.mock(WorkerParameters.class);
        ProgressUpdater progressUpdater = Mockito.mock(ProgressUpdater.class);
        Mockito.when(progressUpdater.updateProgress(any(Context.class), any(UUID.class), any(Data.class)))
                .thenReturn(Futures.<Void>immediateFuture(null));
        Mockito.when(params.getInputData())
                .thenReturn(new Data.Builder().putLong(DownloadServiceInterface.WORK_DATA_MEDIA_ID, mediaId).build());
        Mockito.when(params.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(params.getRunAttemptCount()).thenReturn(runAttempt);
        Mockito.when(params.getProgressUpdater()).thenReturn(progressUpdater);
        return new EpisodeDownloadWorker(context, params);
    }

    private EpisodeDownloadWorker worker(int runAttempt) {
        return worker(MEDIA_ID, runAttempt);
    }

    private void subscribeToMessages() {
        EventBus.getDefault().register(subscriber);
    }

    private void downloaderReturns(DownloadResult result) {
        Mockito.when(downloader.getResult()).thenReturn(result);
    }

    @Test
    public void unknownMediaFailsWithoutDownloading() {
        reader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(null);

        assertEquals(ListenableWorker.Result.failure(), worker(0).doWork());

        assertTrue(factory.constructed().isEmpty());
    }

    @Test
    public void sourceThatNoDownloaderSupportsFailsTheWork() {
        factory.close();
        factory = Mockito.mockConstruction(DefaultDownloaderFactory.class, (mock, ctx) ->
                Mockito.when(mock.create(any(DownloadRequest.class))).thenReturn(null));

        assertEquals(ListenableWorker.Result.failure(), worker(0).doWork());
    }

    @Test
    public void successfulDownloadIsHandledAndLogged() {
        Mockito.when(downloader.call()).thenReturn(downloader);
        downloaderReturns(new DownloadResult(TITLE, MEDIA_ID, FeedMedia.FEEDFILETYPE_FEEDMEDIA, true,
                DownloadError.SUCCESS, null));

        assertEquals(ListenableWorker.Result.success(), worker(0).doWork());

        Mockito.verify(completionHandler.constructed().get(0)).run();
        writer.verify(() -> DBWriter.addDownloadStatus(handlerStatus));
    }

    @Test
    public void cancelledDownloadSucceedsWithoutLoggingFailure() {
        downloader.cancelled = true;
        downloaderReturns(failure(DownloadError.ERROR_DOWNLOAD_CANCELLED, null));

        assertEquals(ListenableWorker.Result.success(), worker(0).doWork());

        writer.verify(() -> DBWriter.addDownloadStatus(any(DownloadResult.class)), Mockito.never());
        assertTrue(completionHandler.constructed().isEmpty());
    }

    @Test
    public void unrecoverableErrorsFailImmediatelyAndAreLogged() {
        DownloadError[] unrecoverable = {DownloadError.ERROR_FORBIDDEN, DownloadError.ERROR_NOT_FOUND,
                DownloadError.ERROR_UNAUTHORIZED, DownloadError.ERROR_IO_BLOCKED};
        for (DownloadError error : unrecoverable) {
            DownloadResult status = failure(error, "403");
            downloaderReturns(status);

            assertEquals(error.name(), ListenableWorker.Result.failure(), worker(0).doWork());

            writer.verify(() -> DBWriter.addDownloadStatus(status));
        }
    }

    @Test
    public void recoverableErrorIsRetriedOnEarlyAttempts() {
        downloaderReturns(failure(DownloadError.ERROR_IO_ERROR, "connection reset"));

        assertEquals(ListenableWorker.Result.retry(), worker(0).doWork());
        assertEquals(ListenableWorker.Result.retry(), worker(1).doWork());
    }

    @Test
    public void recoverableErrorFailsOnTheLastAttempt() {
        downloaderReturns(failure(DownloadError.ERROR_IO_ERROR, "connection reset"));

        assertEquals(ListenableWorker.Result.failure(), worker(2).doWork());
    }

    @Test
    public void retryableFailureAnnouncesThatTheDownloadWillBeRetried() {
        subscribeToMessages();
        downloaderReturns(failure(DownloadError.ERROR_IO_ERROR, "connection reset"));

        worker(0).doWork();

        assertEquals(1, messages.size());
        assertEquals(context.getString(R.string.download_error_retrying, TITLE), messages.get(0).message);
    }

    @Test
    public void unsatisfiableRangeRestartsTheDownloadInsteadOfFailing() {
        downloaderReturns(failure(DownloadError.ERROR_HTTP_DATA_ERROR, "416"));

        assertEquals(ListenableWorker.Result.retry(), worker(0).doWork());

        writer.verify(() -> DBWriter.addDownloadStatus(any(DownloadResult.class)), Mockito.never());
    }

    @Test
    public void otherHttpErrorsAreLoggedAndRetried() {
        DownloadResult status = failure(DownloadError.ERROR_HTTP_DATA_ERROR, "503");
        downloaderReturns(status);

        assertEquals(ListenableWorker.Result.retry(), worker(0).doWork());

        writer.verify(() -> DBWriter.addDownloadStatus(status));
    }

    @Test
    public void downloaderExceptionIsLoggedAndFailsTheWork() {
        DownloadResult status = failure(DownloadError.ERROR_IO_ERROR, "boom");
        Mockito.when(downloader.call()).thenThrow(new IllegalStateException("boom"));
        downloaderReturns(status);

        assertEquals(ListenableWorker.Result.failure(), worker(0).doWork());

        writer.verify(() -> DBWriter.addDownloadStatus(status));
    }

    @Test
    public void finalFailureWithoutMessageSubscriberPostsAnErrorNotification() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        downloaderReturns(failure(DownloadError.ERROR_NOT_FOUND, "404"));

        worker(0).doWork();

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification[] posted = shadowOf(manager).getAllNotifications().toArray(new Notification[0]);
        assertEquals(1, posted.length);
        assertEquals(context.getString(R.string.episode_download_failed),
                posted[0].extras.getString(Notification.EXTRA_TITLE));
    }

    @Test
    public void failureOnTheLastAttemptWithMessageSubscriberShowsANonRetryingMessage() {
        subscribeToMessages();
        downloaderReturns(failure(DownloadError.ERROR_IO_ERROR, "connection reset"));

        worker(2).doWork();

        String notRetrying = context.getString(R.string.download_error_not_retrying, TITLE);
        assertFalse(messages.isEmpty());
        for (MessageEvent message : messages) {
            assertEquals(notRetrying, message.message);
        }
    }

    @Test
    public void stoppingTheWorkerCancelsTheRunningDownload() {
        downloaderReturns(failure(DownloadError.ERROR_NOT_FOUND, "404"));
        EpisodeDownloadWorker worker = worker(0);
        worker.doWork();

        worker.onStopped();

        Mockito.verify(downloader).cancel();
    }

    @Test
    public void foregroundInfoShowsTheDownloadNotification() throws Exception {
        ForegroundInfo info = worker(0).getForegroundInfoAsync().get();

        assertEquals(R.id.notification_downloading, info.getNotificationId());
        assertNotNull(info.getNotification());
        assertEquals(context.getString(R.string.download_notification_title_episodes),
                info.getNotification().extras.getString(Notification.EXTRA_TITLE));
    }
}
