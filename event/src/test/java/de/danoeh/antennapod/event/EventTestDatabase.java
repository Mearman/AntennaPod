package de.danoeh.antennapod.event;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

final class EventTestDatabase {
    private EventTestDatabase() {
    }

    static void setUp(Context context) {
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
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

    static void tearDown() {
        PodDBAdapter.tearDownTests();
    }

    static Feed storeFeed(Context context, String url, String title, int numItems) {
        Feed feed = new Feed(url, null, title);
        feed.setItems(new ArrayList<>());
        for (int i = 0; i < numItems; i++) {
            FeedItem item = new FeedItem(0, "Item " + i, url + "/item" + i, "link",
                    new Date(1000L * (i + 1)), FeedItem.UNPLAYED, feed);
            FeedMedia media = new FeedMedia(item, url + "/media" + i, 1024, "audio/mp3");
            media.setDuration(1000000);
            item.setMedia(media);
            feed.getItems().add(item);
        }
        return FeedDatabaseWriter.updateFeed(context, feed, false);
    }

    static List<FeedItem> itemsOf(Feed feed) {
        return feed.getItems();
    }
}
