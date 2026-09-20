package de.danoeh.antennapod.storage.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingFeedUpdateManager extends FeedUpdateManager {
    private final List<Feed> updatedFeeds = new CopyOnWriteArrayList<>();
    private final List<Feed> askedFeeds = new CopyOnWriteArrayList<>();

    @Override
    public void restartUpdateAlarm(Context context, boolean replace) {
    }

    @Override
    public void runOnce(Context context) {
    }

    @Override
    public void runOnce(Context context, Feed feed) {
        updatedFeeds.add(feed);
    }

    @Override
    public void runOnce(Context context, Feed feed, boolean nextPage) {
        updatedFeeds.add(feed);
    }

    @Override
    public void runOnceOrAsk(@NonNull Context context) {
    }

    @Override
    public void runOnceOrAsk(@NonNull Context context, @Nullable Feed feed) {
        askedFeeds.add(feed);
    }

    public List<Feed> getUpdatedFeeds() {
        return updatedFeeds;
    }

    public List<Feed> getAskedFeeds() {
        return askedFeeds;
    }
}
