package de.danoeh.antennapod.net.download.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.DefaultWorkerFactory;
import androidx.work.ProgressUpdater;
import androidx.work.WorkerParameters;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.utils.taskexecutor.TaskExecutor;
import com.google.common.util.concurrent.Futures;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.common.NetworkUtils;
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
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.shadows.ShadowStatFs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public abstract class DownloadIntegrationTestBase {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected Context context;
    protected MockWebServer server;
    protected SynchronizationQueue synchronizationQueue;
    protected WorkManagerImpl workManager;

    @Before
    public void setUpDownloadEnvironment() throws IOException {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserPreferences.init(context);
        NetworkUtils.init(context);
        PlaybackPreferences.init(context);
        SynchronizationSettings.init(context);
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        synchronizationQueue = Mockito.mock(SynchronizationQueue.class);
        SynchronizationQueue.setInstance(synchronizationQueue);
        AutoDownloadManager.setInstance(Mockito.mock(AutoDownloadManager.class));
        workManager = Mockito.mock(WorkManagerImpl.class);
        WorkManagerImpl.setDelegate(workManager);

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
        WorkManagerImpl.setDelegate(null);
        DBWriter.tearDownTests();
        PodDBAdapter.tearDownTests();
    }

    protected Feed newFeed(String title, String downloadUrl) {
        Feed feed = new Feed(0, null, title, null, "link", "descr", null, null, null, "type", "id-" + title, null,
                null, downloadUrl, 0, false, null, null, null, false, Feed.STATE_SUBSCRIBED);
        feed.setItems(new ArrayList<>());
        return feed;
    }

    protected Feed saveFeed(Feed feed) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        assertTrue(feed.getId() != 0);
        return DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
    }

    protected FeedMedia saveEpisode(String mediaUrl) {
        Feed feed = newFeed("Test Feed", server.url("/feed.xml").toString());
        FeedItem item = new FeedItem(0, "Episode One", "guid-1", "link", new Date(), FeedItem.NEW, feed);
        item.setMedia(new FeedMedia(item, mediaUrl, 0, "audio/mpeg"));
        feed.getItems().add(item);
        saveFeed(feed);
        assertTrue(item.getMedia().getId() != 0);
        return DBReader.getFeedMedia(item.getMedia().getId());
    }

    protected static String rss(String feedTitle, String... episodeTitles) {
        StringBuilder xml = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel>");
        if (feedTitle != null) {
            xml.append("<title>").append(feedTitle).append("</title>");
        }
        xml.append("<link>http://example.com</link><description>d</description>");
        for (int i = 0; i < episodeTitles.length; i++) {
            xml.append("<item><title>").append(episodeTitles[i]).append("</title><guid>")
                    .append(feedTitle).append("-guid-").append(i).append("</guid>")
                    .append("<pubDate>Mon, 0").append(i + 1).append(" Jan 2124 10:00:00 +0000</pubDate>")
                    .append("<enclosure url=\"http://example.com/").append(i).append(".mp3\" length=\"100\" ")
                    .append("type=\"audio/mpeg\"/></item>");
        }
        return xml.append("</channel></rss>").toString();
    }

    protected WorkerParameters workerParameters(Data inputData, int runAttemptCount) {
        ProgressUpdater progressUpdater = (ctx, id, data) -> Futures.immediateFuture(null);
        return new WorkerParameters(UUID.randomUUID(), inputData, Collections.emptyList(),
                new WorkerParameters.RuntimeExtras(), runAttemptCount, 0, Runnable::run,
                EmptyCoroutineContext.INSTANCE, Mockito.mock(TaskExecutor.class),
                DefaultWorkerFactory.INSTANCE, progressUpdater,
                (ctx, id, foregroundInfo) -> Futures.immediateFuture(null));
    }

    protected void setNetwork(int type) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(connectivityManager).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED, type, 0, true, NetworkInfo.State.CONNECTED));
    }

    protected void setNoNetwork() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(connectivityManager).setActiveNetworkInfo(null);
    }

    protected static byte[] bytes(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i % 251);
        }
        return data;
    }

    protected static MockResponse audioResponse(byte[] body) {
        return new MockResponse().setBody(new Buffer().write(body)).addHeader("Content-Type", "audio/mpeg");
    }
}
