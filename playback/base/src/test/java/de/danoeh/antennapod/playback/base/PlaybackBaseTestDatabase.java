package de.danoeh.antennapod.playback.base;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public final class PlaybackBaseTestDatabase {
    private PlaybackBaseTestDatabase() {
    }

    public static void setUp(Context context) {
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        AutoDownloadManager.setInstance(new AutoDownloadManager() {
            @Override
            public Future<?> autodownloadUndownloadedItems(Context ctx) {
                FutureTask<Void> done = new FutureTask<>(() -> null);
                done.run();
                return done;
            }

            @Override
            public void performAutoCleanup(Context ctx) {
            }
        });
    }

    public static void tearDown() {
        PodDBAdapter.tearDownTests();
    }

    public static Feed storeFeed(Context context, String url, String title, int numItems) {
        Feed feed = new Feed(url, null, title);
        feed.setImageUrl(url + "/image.png");
        feed.setAuthor("Author of " + title);
        feed.setItems(new ArrayList<>());
        for (int i = 0; i < numItems; i++) {
            FeedItem item = new FeedItem(0, "Episode " + i, "id-" + i, "link",
                    new Date(1000L * (i + 1)), FeedItem.UNPLAYED, feed);
            FeedMedia media = new FeedMedia(item, url + "/media" + i + ".mp3", 1024, "audio/mp3");
            media.setDuration(600000);
            item.setMedia(media);
            feed.getItems().add(item);
        }
        return FeedDatabaseWriter.updateFeed(context, feed, false);
    }

    public static List<FeedItem> storedItems(long feedId) {
        return DBReader.getFeed(feedId, true, 0, Integer.MAX_VALUE).getItems();
    }

    public static FeedMedia storedMedia(long feedId, String identifier) {
        for (FeedItem item : storedItems(feedId)) {
            if (item.getItemIdentifier().equals(identifier)) {
                return item.getMedia();
            }
        }
        throw new AssertionError("No stored episode with identifier " + identifier);
    }
}
