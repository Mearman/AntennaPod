package de.danoeh.antennapod.net.download.service;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.DefaultWorkerFactory;
import androidx.work.ProgressUpdater;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.taskexecutor.TaskExecutor;
import com.google.common.util.concurrent.Futures;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import kotlin.coroutines.EmptyCoroutineContext;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowStatFs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertTrue;

/**
 * Sets up the real database, the shared HTTP client and a local HTTP server for tests of the download service.
 */
@RunWith(RobolectricTestRunner.class)
public abstract class DownloadIntegrationTestBase {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected Context context;
    protected MockWebServer server;
    protected SynchronizationQueue synchronizationQueue;

    @Before
    public void setUpDownloadEnvironment() throws IOException {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        SynchronizationSettings.init(context);
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        synchronizationQueue = Mockito.mock(SynchronizationQueue.class);
        SynchronizationQueue.setInstance(synchronizationQueue);
        AutoDownloadManager.setInstance(Mockito.mock(AutoDownloadManager.class));

        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();

        int freeBlocks = 1_000_000;
        ShadowStatFs.registerStats(UserPreferences.getDataFolder(null), freeBlocks, freeBlocks, freeBlocks);
        AntennapodHttpClient.setCacheDirectory(temporaryFolder.newFolder("http-cache"));
        AntennapodHttpClient.reinit();
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDownDownloadEnvironment() throws IOException {
        server.shutdown();
        DBWriter.tearDownTests();
        PodDBAdapter.tearDownTests();
    }

    /**
     * Stores a subscribed feed containing one episode whose media is downloadable from the given URL.
     */
    protected FeedMedia saveEpisode(String mediaUrl) {
        Feed feed = new Feed(0, null, "Test Feed", "link", "descr", null, null, null, null, "id", null, null,
                server.url("/feed.xml").toString(), System.currentTimeMillis());
        feed.setItems(new ArrayList<>());
        FeedItem item = new FeedItem(0, "Episode One", "guid-1", "link", new Date(), FeedItem.NEW, feed);
        item.setMedia(new FeedMedia(item, mediaUrl, 0, "audio/mpeg"));
        feed.getItems().add(item);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        assertTrue(item.getMedia().getId() != 0);
        return DBReader.getFeedMedia(item.getMedia().getId());
    }

    protected WorkerParameters workerParameters(Data inputData, int runAttemptCount) {
        ProgressUpdater progressUpdater = (ctx, id, data) -> Futures.immediateFuture(null);
        return new WorkerParameters(UUID.randomUUID(), inputData, Collections.emptyList(),
                new WorkerParameters.RuntimeExtras(), runAttemptCount, 0, Runnable::run,
                EmptyCoroutineContext.INSTANCE, Mockito.mock(TaskExecutor.class),
                DefaultWorkerFactory.INSTANCE, progressUpdater,
                (ctx, id, foregroundInfo) -> Futures.immediateFuture(null));
    }
}
