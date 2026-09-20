package de.test.antennapod.service.download;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DownloadServiceInterfaceTest {
    private static final String PREF_ENQUEUE_DOWNLOADED = "prefEnqueueDownloaded";
    private static final long TIMEOUT_SECONDS = 60;

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Context context;
    private WorkManager workManager;
    private DownloadServiceInterface downloads;
    private Feed feed;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
        UserPreferences.setAllowMobileEpisodeDownload(true);
        workManager = WorkManager.getInstance(context);
        downloads = DownloadServiceInterface.get();
        feed = fixture.subscribe("Service", 2);
    }

    @After
    public void tearDown() throws Exception {
        downloads.cancelAll(context);
        fixture.server().releaseStalled();
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> activeDownloads() == 0);
        workManager.pruneWork().getResult().get();
        fixture.tearDown();
    }

    private int activeDownloads() {
        return downloads.getNumberOfActiveDownloads(context);
    }

    private FeedItem item(int index) {
        return DBReader.getFeedItem(feed.getItemAtIndex(index).getId());
    }

    private FeedMedia media(int index) {
        return DBReader.getFeedMedia(feed.getItemAtIndex(index).getMedia().getId());
    }

    private List<WorkInfo> workOf(FeedMedia media) throws Exception {
        return workManager.getWorkInfosByTag(DownloadServiceInterface.WORK_TAG_EPISODE_URL + media.getDownloadUrl())
                .get();
    }

    private void pointMediaAtStall(int index) throws Exception {
        FeedMedia stored = media(index);
        String url = fixture.url("/stall/" + fixture.fileId(fixture.hostedFile(stored.getDownloadUrl())));
        DBWriter.setFeedMedia(new FeedMedia(stored.getId(), stored.getItem(), 0, 0, stored.getSize(),
                stored.getMimeType(), null, url, 0, null, 0, 0)).get();
    }

    private void awaitPartialFile(int index) {
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> {
            String path = media(index).getLocalFileUrl();
            return path != null && new File(path).length() > 0;
        });
    }

    @Test
    public void downloadNowFetchesTheEpisode() throws Exception {
        downloads.downloadNow(context, item(0), true);

        DownloadTestFixture.awaitDownloaded(media(0));
        File downloaded = new File(media(0).getLocalFileUrl());
        assertTrue(downloaded.exists());
        assertTrue(FileUtils.contentEquals(fixture.hostedFile(media(0).getDownloadUrl()), downloaded));
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> activeDownloads() == 0);
        assertEquals(WorkInfo.State.SUCCEEDED, workOf(media(0)).get(0).getState());
    }

    @Test
    public void downloadFetchesTheEpisodeWhenTheNetworkAllowsIt() throws Exception {
        downloads.download(context, item(1));

        DownloadTestFixture.awaitDownloaded(media(1));
        assertEquals(1, fixture.requestsFor(media(1).getDownloadUrl()).size());
    }

    @Test
    public void downloadIgnoresEpisodesThatAreAlreadyDownloaded() throws Exception {
        downloads.downloadNow(context, item(0), true);
        DownloadTestFixture.awaitDownloaded(media(0));
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> activeDownloads() == 0);

        downloads.download(context, item(0));
        downloads.download(context, item(1));

        DownloadTestFixture.awaitDownloaded(media(1));
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> activeDownloads() == 0);
        assertEquals(1, fixture.requestsFor(media(0).getDownloadUrl()).size());
    }

    @Test
    public void downloadingAnEpisodeAddsItToTheQueue() throws Exception {
        assertTrue(DBReader.getQueue().isEmpty());

        downloads.downloadNow(context, item(0), true);

        DownloadTestFixture.awaitDownloaded(media(0));
        assertEquals(1, DBReader.getQueue().size());
        assertEquals(item(0).getId(), DBReader.getQueue().get(0).getId());
    }

    @Test
    public void downloadingLeavesTheQueueAloneWhenTheUserDisabledEnqueueing() throws Exception {
        DownloadTestFixture.putBoolean(PREF_ENQUEUE_DOWNLOADED, false);

        downloads.downloadNow(context, item(0), true);

        DownloadTestFixture.awaitDownloaded(media(0));
        assertTrue(DBReader.getQueue().isEmpty());
    }

    @Test
    public void failedDownloadIsLoggedAndStopsBeingActive() throws Exception {
        FeedMedia stored = media(0);
        DBWriter.setFeedMedia(new FeedMedia(stored.getId(), stored.getItem(), 0, 0, stored.getSize(),
                stored.getMimeType(), null, fixture.url("/status/404"), 0, null, 0, 0)).get();

        downloads.downloadNow(context, item(0), true);

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> {
            for (DownloadResult result : DBReader.getDownloadLog()) {
                if (result.getReason() == DownloadError.ERROR_NOT_FOUND) {
                    return true;
                }
            }
            return false;
        });
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> activeDownloads() == 0);
        assertFalse(media(0).isDownloaded());
    }

    @Test
    public void cancelDiscardsThePartialFileAndTheQueueEntry() throws Exception {
        pointMediaAtStall(0);
        downloads.downloadNow(context, item(0), true);
        assertTrue(fixture.server().awaitStalled(1, TIMEOUT_SECONDS, TimeUnit.SECONDS));
        awaitPartialFile(0);
        File partial = new File(media(0).getLocalFileUrl());
        assertEquals(1, DBReader.getQueue().size());

        downloads.cancel(context, media(0));

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> workOf(media(0)).get(0).getState() == WorkInfo.State.CANCELLED);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> DBReader.getQueue().isEmpty());
        assertEquals(0, activeDownloads());
        assertFalse(media(0).isDownloaded());
        assertFalse(partial.exists());
    }

    @Test
    public void cancelAllStopsEveryDownload() throws Exception {
        pointMediaAtStall(0);
        pointMediaAtStall(1);
        downloads.downloadNow(context, item(0), true);
        downloads.downloadNow(context, item(1), true);
        assertTrue(fixture.server().awaitStalled(2, TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(2, activeDownloads());

        downloads.cancelAll(context);

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> activeDownloads() == 0);
        assertFalse(media(0).isDownloaded());
        assertFalse(media(1).isDownloaded());
    }

    @Test
    public void slowDownloadCompletesOnceTheServerContinues() throws Exception {
        pointMediaAtStall(0);
        downloads.downloadNow(context, item(0), true);
        assertTrue(fixture.server().awaitStalled(1, TIMEOUT_SECONDS, TimeUnit.SECONDS));
        awaitPartialFile(0);
        assertFalse(media(0).isDownloaded());

        fixture.server().releaseStalled();

        DownloadTestFixture.awaitDownloaded(media(0));
        assertTrue(FileUtils.contentEquals(fixture.hostedFile(media(0).getDownloadUrl()),
                new File(media(0).getLocalFileUrl())));
    }
}
