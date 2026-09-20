package de.danoeh.antennapod.net.download.service.feed;

import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import com.google.common.util.concurrent.Futures;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.service.DownloadIntegrationTestBase;
import de.danoeh.antennapod.net.download.service.episode.EpisodeDownloadWorker;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Category(IntegrationTest.class)
public class DownloadServiceInterfaceImplTest extends DownloadIntegrationTestBase {
    private static final String PREF_ENQUEUE_DOWNLOADED = "prefEnqueueDownloaded";
    private static final long ASYNC_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    private DownloadServiceInterfaceImpl downloadService;
    private FeedItem item;
    private String mediaUrl;

    @Before
    public void createItem() {
        downloadService = new DownloadServiceInterfaceImpl();
        mediaUrl = server.url("/episode.mp3").toString();
        FeedMedia media = saveEpisode(mediaUrl);
        item = DBReader.getFeedItem(media.getItem().getId());
    }

    private OneTimeWorkRequest captureEnqueuedRequest(ExistingWorkPolicy expectedPolicy) {
        ArgumentCaptor<OneTimeWorkRequest> captor = ArgumentCaptor.forClass(OneTimeWorkRequest.class);
        verify(workManager).enqueueUniqueWork(eq(mediaUrl), eq(expectedPolicy), captor.capture());
        return captor.getValue();
    }

    private static WorkInfo workInfo(WorkInfo.State state, Set<String> tags) {
        return new WorkInfo(UUID.randomUUID(), state, tags, Data.EMPTY, Data.EMPTY, 0, 0, Constraints.NONE, 0, null,
                Long.MAX_VALUE, WorkInfo.STOP_REASON_NOT_STOPPED);
    }

    @Test
    public void downloadEnqueuesUniqueWorkKeepingExistingRequest() {
        downloadService.download(context, item);

        OneTimeWorkRequest request = captureEnqueuedRequest(ExistingWorkPolicy.KEEP);
        assertEquals(EpisodeDownloadWorker.class.getName(), request.getWorkSpec().workerClassName);
        assertEquals(item.getMedia().getId(),
                request.getWorkSpec().input.getLong(DownloadServiceInterface.WORK_DATA_MEDIA_ID, 0));
        assertTrue(request.getTags().contains(DownloadServiceInterface.WORK_TAG));
        assertTrue(request.getTags().contains(DownloadServiceInterface.WORK_TAG_EPISODE_URL + mediaUrl));
    }

    @Test
    public void downloadRequiresUnmeteredNetworkUnlessMobileDownloadsAreAllowed() {
        downloadService.download(context, item);

        OneTimeWorkRequest request = captureEnqueuedRequest(ExistingWorkPolicy.KEEP);
        assertEquals(NetworkType.UNMETERED, request.getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test
    public void downloadAcceptsAnyConnectionWhenMobileDownloadsAreAllowed() {
        UserPreferences.setAllowMobileEpisodeDownload(true);

        downloadService.download(context, item);

        OneTimeWorkRequest request = captureEnqueuedRequest(ExistingWorkPolicy.KEEP);
        assertEquals(NetworkType.CONNECTED, request.getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test
    public void downloadOfAlreadyDownloadedEpisodeEnqueuesNothing() {
        item.getMedia().setDownloaded(true, System.currentTimeMillis());

        downloadService.download(context, item);

        verify(workManager, never()).enqueueUniqueWork(any(String.class), any(ExistingWorkPolicy.class),
                any(OneTimeWorkRequest.class));
    }

    @Test
    public void downloadQueuesEpisodeAndTagsRequestWhenSettingEnabled() {
        downloadService.download(context, item);
        DBWriter.tearDownTests();

        OneTimeWorkRequest request = captureEnqueuedRequest(ExistingWorkPolicy.KEEP);
        assertTrue(request.getTags().contains(DownloadServiceInterface.WORK_DATA_WAS_QUEUED));
        assertEquals(1, DBReader.getQueue().size());
        assertEquals(item.getId(), DBReader.getQueue().get(0).getId());
    }

    @Test
    public void downloadLeavesQueueUntouchedWhenSettingDisabled() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(PREF_ENQUEUE_DOWNLOADED, false).commit();

        downloadService.download(context, item);
        DBWriter.tearDownTests();

        OneTimeWorkRequest request = captureEnqueuedRequest(ExistingWorkPolicy.KEEP);
        assertFalse(request.getTags().contains(DownloadServiceInterface.WORK_DATA_WAS_QUEUED));
        assertTrue(DBReader.getQueue().isEmpty());
    }

    @Test
    public void downloadDoesNotTagRequestWhenEpisodeIsAlreadyQueued() {
        DBWriter.addQueueItem(context, item);
        DBWriter.tearDownTests();
        FeedItem queuedItem = DBReader.getFeedItem(item.getId());

        downloadService.download(context, queuedItem);

        OneTimeWorkRequest request = captureEnqueuedRequest(ExistingWorkPolicy.KEEP);
        assertFalse(request.getTags().contains(DownloadServiceInterface.WORK_DATA_WAS_QUEUED));
        assertEquals(1, DBReader.getQueue().size());
    }

    @Test
    public void downloadNowIgnoringConstraintsOnlyRequiresConnectivity() {
        downloadService.downloadNow(context, item, true);

        OneTimeWorkRequest request = captureEnqueuedRequest(ExistingWorkPolicy.KEEP);
        assertEquals(NetworkType.CONNECTED, request.getWorkSpec().constraints.getRequiredNetworkType());
        assertTrue(request.getWorkSpec().expedited);
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, request.getWorkSpec().outOfQuotaPolicy);
    }

    @Test
    public void downloadNowRespectingConstraintsUsesConfiguredNetworkType() {
        downloadService.downloadNow(context, item, false);

        OneTimeWorkRequest request = captureEnqueuedRequest(ExistingWorkPolicy.KEEP);
        assertEquals(NetworkType.UNMETERED, request.getWorkSpec().constraints.getRequiredNetworkType());
        assertTrue(request.getWorkSpec().expedited);
    }

    @Test
    public void cancelAllCancelsEveryEpisodeDownload() {
        downloadService.cancelAll(context);

        verify(workManager).cancelAllWorkByTag(DownloadServiceInterface.WORK_TAG);
    }

    @Test
    public void cancelRemovesPartialFileAndCancelsWork() throws Exception {
        File partial = temporaryFolder.newFile("partial.mp3");
        FeedMedia media = item.getMedia();
        media.setLocalFileUrl(partial.getAbsolutePath());
        String tag = DownloadServiceInterface.WORK_TAG_EPISODE_URL + mediaUrl;
        when(workManager.getWorkInfosByTag(tag)).thenReturn(Futures.immediateFuture(List.of(
                workInfo(WorkInfo.State.RUNNING, Set.of(tag)))));

        downloadService.cancel(context, media);

        verify(workManager, timeout(ASYNC_TIMEOUT_MS)).cancelAllWorkByTag(tag);
        DBWriter.tearDownTests();
        assertFalse(partial.exists());
    }

    @Test
    public void cancelDequeuesEpisodeThatWasQueuedByTheDownload() {
        DBWriter.addQueueItem(context, item);
        DBWriter.tearDownTests();
        FeedItem queuedItem = DBReader.getFeedItem(item.getId());
        String tag = DownloadServiceInterface.WORK_TAG_EPISODE_URL + mediaUrl;
        when(workManager.getWorkInfosByTag(tag)).thenReturn(Futures.immediateFuture(List.of(
                workInfo(WorkInfo.State.ENQUEUED, Set.of(tag, DownloadServiceInterface.WORK_DATA_WAS_QUEUED)))));

        downloadService.cancel(context, queuedItem.getMedia());

        verify(workManager, timeout(ASYNC_TIMEOUT_MS)).cancelAllWorkByTag(tag);
        DBWriter.tearDownTests();
        assertTrue(DBReader.getQueue().isEmpty());
    }

    @Test
    public void cancelKeepsQueueEntryOfWorkThatWasNotQueuedByTheDownload() {
        DBWriter.addQueueItem(context, item);
        DBWriter.tearDownTests();
        FeedItem queuedItem = DBReader.getFeedItem(item.getId());
        String tag = DownloadServiceInterface.WORK_TAG_EPISODE_URL + mediaUrl;
        when(workManager.getWorkInfosByTag(tag)).thenReturn(Futures.immediateFuture(List.of(
                workInfo(WorkInfo.State.ENQUEUED, Set.of(tag)))));

        downloadService.cancel(context, queuedItem.getMedia());

        verify(workManager, timeout(ASYNC_TIMEOUT_MS)).cancelAllWorkByTag(tag);
        DBWriter.tearDownTests();
        assertEquals(1, DBReader.getQueue().size());
    }

    @Test
    public void cancelStillCancelsWorkWhenLookingUpWorkInfosFails() {
        String tag = DownloadServiceInterface.WORK_TAG_EPISODE_URL + mediaUrl;
        when(workManager.getWorkInfosByTag(tag)).thenReturn(
                Futures.immediateFailedFuture(new IllegalStateException("lookup failed")));

        downloadService.cancel(context, item.getMedia());

        verify(workManager, timeout(ASYNC_TIMEOUT_MS)).cancelAllWorkByTag(tag);
    }

    @Test
    public void activeDownloadsCountRunningEnqueuedAndBlockedWork() {
        Set<String> tags = Set.of(DownloadServiceInterface.WORK_TAG);
        when(workManager.getWorkInfosByTag(DownloadServiceInterface.WORK_TAG)).thenReturn(Futures.immediateFuture(
                List.of(workInfo(WorkInfo.State.RUNNING, tags), workInfo(WorkInfo.State.ENQUEUED, tags),
                        workInfo(WorkInfo.State.BLOCKED, tags), workInfo(WorkInfo.State.SUCCEEDED, tags),
                        workInfo(WorkInfo.State.FAILED, tags), workInfo(WorkInfo.State.CANCELLED, tags))));

        assertEquals(3, downloadService.getNumberOfActiveDownloads(context));
    }

    @Test
    public void activeDownloadsAreZeroWhenLookupFails() {
        when(workManager.getWorkInfosByTag(DownloadServiceInterface.WORK_TAG)).thenReturn(
                Futures.immediateFailedFuture(new IllegalStateException("lookup failed")));

        assertEquals(0, downloadService.getNumberOfActiveDownloads(context));
    }
}
