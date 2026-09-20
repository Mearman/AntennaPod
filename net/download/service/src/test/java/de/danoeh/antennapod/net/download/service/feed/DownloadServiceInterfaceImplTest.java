package de.danoeh.antennapod.net.download.service.feed;

import android.content.Context;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import com.google.common.util.concurrent.Futures;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.service.WorkManagerMocks;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(RobolectricTestRunner.class)
public class DownloadServiceInterfaceImplTest {
    private static final String URL = "http://example.com/episode.mp3";
    private static final String URL_TAG = DownloadServiceInterface.WORK_TAG_EPISODE_URL + URL;

    private final Context context = RuntimeEnvironment.getApplication();
    private final DownloadServiceInterfaceImpl downloads = new DownloadServiceInterfaceImpl();
    private WorkManagerMocks workManager;
    private MockedStatic<UserPreferences> preferences;
    private MockedStatic<DBWriter> writer;

    @Before
    public void setUp() {
        workManager = new WorkManagerMocks();
        preferences = Mockito.mockStatic(UserPreferences.class);
        writer = Mockito.mockStatic(DBWriter.class);
        RxJavaPlugins.setComputationSchedulerHandler(scheduler -> Schedulers.trampoline());
    }

    @After
    public void tearDown() {
        RxJavaPlugins.reset();
        writer.close();
        preferences.close();
        workManager.close();
    }

    private static FeedItem episode() {
        Feed feed = new Feed("http://example.com/feed.xml", null, "Feed");
        FeedItem item = new FeedItem(3, "Episode", "guid", "http://example.com/link", new Date(0),
                FeedItem.UNPLAYED, feed);
        item.setMedia(new FeedMedia(8, item, 0, 0, 0, "audio/mpeg", null, URL, 0, null, 0, 0));
        return item;
    }

    private static WorkInfo workInfo(WorkInfo.State state, String... tags) {
        WorkInfo info = Mockito.mock(WorkInfo.class);
        Mockito.when(info.getState()).thenReturn(state);
        Mockito.when(info.getTags()).thenReturn(new HashSet<>(Arrays.asList(tags)));
        return info;
    }

    private OneTimeWorkRequest enqueued() {
        return workManager.enqueuedUniqueWork(URL, ExistingWorkPolicy.KEEP);
    }

    @Test
    public void downloadEnqueuesUniqueWorkPerEpisodeUrl() {
        downloads.download(context, episode());

        OneTimeWorkRequest request = enqueued();
        assertTrue(request.getTags().contains(DownloadServiceInterface.WORK_TAG));
        assertTrue(request.getTags().contains(URL_TAG));
        assertEquals(8, request.getWorkSpec().input.getLong(DownloadServiceInterface.WORK_DATA_MEDIA_ID, 0));
    }

    @Test
    public void downloadDoesNothingForAnAlreadyDownloadedEpisode() {
        FeedItem item = episode();
        item.getMedia().setDownloaded(true, 1);

        downloads.download(context, item);

        Mockito.verifyNoInteractions(workManager.manager());
    }

    @Test
    public void downloadRequiresUnmeteredNetworkUnlessMobileDownloadIsAllowed() {
        downloads.download(context, episode());
        assertEquals(NetworkType.UNMETERED, enqueued().getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test
    public void downloadRequiresAnyNetworkWhenMobileDownloadIsAllowed() {
        preferences.when(UserPreferences::isAllowMobileEpisodeDownload).thenReturn(true);

        downloads.download(context, episode());

        assertEquals(NetworkType.CONNECTED, enqueued().getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test
    public void downloadQueuesTheEpisodeWhenSettingRequestsIt() {
        preferences.when(UserPreferences::enqueueDownloadedEpisodes).thenReturn(true);
        FeedItem item = episode();

        downloads.download(context, item);

        writer.verify(() -> DBWriter.addQueueItem(eq(context), eq(item)));
        assertTrue(enqueued().getTags().contains(DownloadServiceInterface.WORK_DATA_WAS_QUEUED));
    }

    @Test
    public void downloadDoesNotQueueAnEpisodeThatIsAlreadyQueued() {
        preferences.when(UserPreferences::enqueueDownloadedEpisodes).thenReturn(true);
        FeedItem item = episode();
        item.addTag(FeedItem.TAG_QUEUE);

        downloads.download(context, item);

        writer.verify(() -> DBWriter.addQueueItem(any(Context.class), any(FeedItem.class)), Mockito.never());
        assertFalse(enqueued().getTags().contains(DownloadServiceInterface.WORK_DATA_WAS_QUEUED));
    }

    @Test
    public void downloadDoesNotQueueEpisodesWhenSettingIsOff() {
        downloads.download(context, episode());

        writer.verify(() -> DBWriter.addQueueItem(any(Context.class), any(FeedItem.class)), Mockito.never());
    }

    @Test
    public void downloadNowIsExpeditedAndHonoursNetworkConstraintsByDefault() {
        downloads.downloadNow(context, episode(), false);

        OneTimeWorkRequest request = enqueued();
        assertTrue(request.getWorkSpec().expedited);
        assertEquals(NetworkType.UNMETERED, request.getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test
    public void downloadNowCanIgnoreTheMeteredNetworkRestriction() {
        downloads.downloadNow(context, episode(), true);

        assertEquals(NetworkType.CONNECTED, enqueued().getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test
    public void downloadNowStartsWithoutInitialDelay() {
        downloads.downloadNow(context, episode(), true);

        assertEquals(0, enqueued().getWorkSpec().initialDelay);
    }

    @Test
    public void cancelAllCancelsEveryEpisodeDownload() {
        downloads.cancelAll(context);

        Mockito.verify(workManager.manager()).cancelAllWorkByTag(DownloadServiceInterface.WORK_TAG);
    }

    @Test
    public void cancelCancelsWorkOfTheEpisode() {
        FeedMedia media = episode().getMedia();
        WorkInfo running = workInfo(WorkInfo.State.RUNNING, DownloadServiceInterface.WORK_TAG, URL_TAG);
        Mockito.when(workManager.manager().getWorkInfosByTag(URL_TAG))
                .thenReturn(Futures.immediateFuture(Collections.singletonList(running)));

        downloads.cancel(context, media);

        Mockito.verify(workManager.manager()).cancelAllWorkByTag(URL_TAG);
        writer.verify(() -> DBWriter.removeQueueItem(any(Context.class), anyBoolean(), any(FeedItem.class)),
                Mockito.never());
    }

    @Test
    public void cancelRemovesEpisodeFromQueueWhenDownloadQueuedIt() {
        FeedMedia media = episode().getMedia();
        WorkInfo running = workInfo(WorkInfo.State.RUNNING, URL_TAG, DownloadServiceInterface.WORK_DATA_WAS_QUEUED);
        Mockito.when(workManager.manager().getWorkInfosByTag(URL_TAG))
                .thenReturn(Futures.immediateFuture(Collections.singletonList(running)));

        downloads.cancel(context, media);

        writer.verify(() -> DBWriter.removeQueueItem(eq(context), eq(false), eq(media.getItem())));
        Mockito.verify(workManager.manager()).cancelAllWorkByTag(URL_TAG);
    }

    @Test
    public void cancelStillCancelsWorkWhenLookingUpWorkFails() {
        FeedMedia media = episode().getMedia();
        Mockito.when(workManager.manager().getWorkInfosByTag(URL_TAG))
                .thenReturn(Futures.immediateFailedFuture(new IllegalStateException("lookup failed")));

        downloads.cancel(context, media);

        Mockito.verify(workManager.manager()).cancelAllWorkByTag(URL_TAG);
    }

    @Test
    public void unfinishedWorkCountsAsActiveDownloads() {
        List<WorkInfo> infos = Arrays.asList(
                workInfo(WorkInfo.State.RUNNING),
                workInfo(WorkInfo.State.ENQUEUED),
                workInfo(WorkInfo.State.BLOCKED),
                workInfo(WorkInfo.State.SUCCEEDED),
                workInfo(WorkInfo.State.FAILED),
                workInfo(WorkInfo.State.CANCELLED));
        Mockito.when(workManager.manager().getWorkInfosByTag(DownloadServiceInterface.WORK_TAG))
                .thenReturn(Futures.immediateFuture(infos));

        assertEquals(3, downloads.getNumberOfActiveDownloads(context));
    }

    @Test
    public void noActiveDownloadsWhenLookingUpWorkFails() {
        Mockito.when(workManager.manager().getWorkInfosByTag(DownloadServiceInterface.WORK_TAG))
                .thenReturn(Futures.immediateFailedFuture(new IllegalStateException("lookup failed")));

        assertEquals(0, downloads.getNumberOfActiveDownloads(context));
    }
}
