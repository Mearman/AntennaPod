package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class DatabaseTestBase {
    private static final long TIMEOUT_SECONDS = 10;

    protected Context context;
    protected RecordingAutoDownloadManager autoDownloadManager;
    protected RecordingFeedUpdateManager feedUpdateManager;
    protected RecordingSynchronizationQueue synchronizationQueue;
    protected EventCollector events;

    @Before
    public void setUpDatabase() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        autoDownloadManager = new RecordingAutoDownloadManager();
        AutoDownloadManager.setInstance(autoDownloadManager);
        feedUpdateManager = new RecordingFeedUpdateManager();
        FeedUpdateManager.setInstance(feedUpdateManager);
        synchronizationQueue = new RecordingSynchronizationQueue();
        SynchronizationQueue.setInstance(synchronizationQueue);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        events = new EventCollector();
        events.register();
    }

    @After
    public void tearDownDatabase() {
        events.unregister();
        DBWriter.tearDownTests();
        PodDBAdapter.tearDownTests();
    }

    protected static void await(Future<?> future) {
        try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError(e);
        }
    }

    protected Feed storeFeed(String title) {
        Feed feed = new Feed("https://example.com/" + title.replace(' ', '-') + ".xml", null, title);
        feed.setItems(new ArrayList<>());
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        return feed;
    }

    protected FeedItem storeItem(Feed feed, String title, long pubDateMillis, int playState) {
        FeedItem item = new FeedItem(0, title, "guid-" + title, "https://example.com/" + title,
                new Date(pubDateMillis), playState, feed);
        item.setMedia(new FeedMedia(item, "https://example.com/media/" + title.replace(' ', '-') + ".mp3",
                1000, "audio/mpeg"));
        feed.getItems().add(item);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setSingleFeedItem(item);
        adapter.close();
        return item;
    }

    protected FeedItem storeItem(Feed feed, String title) {
        return storeItem(feed, title, 1_000_000L, FeedItem.UNPLAYED);
    }

    protected FeedItem storeItemWithoutMedia(Feed feed, String title, long pubDateMillis) {
        FeedItem item = new FeedItem(0, title, "guid-" + title, "https://example.com/" + title,
                new Date(pubDateMillis), FeedItem.UNPLAYED, feed);
        feed.getItems().add(item);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setSingleFeedItem(item);
        adapter.close();
        return item;
    }
}
