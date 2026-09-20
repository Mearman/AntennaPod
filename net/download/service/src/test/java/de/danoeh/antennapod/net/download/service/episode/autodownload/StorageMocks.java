package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

final class StorageMocks implements AutoCloseable {
    private final MockedStatic<DBReader> reader = Mockito.mockStatic(DBReader.class);
    private final MockedStatic<DBWriter> writer = Mockito.mockStatic(DBWriter.class);
    private final MockedStatic<UserPreferences> preferences = Mockito.mockStatic(UserPreferences.class);
    private final List<FeedMedia> deletedMedia = new ArrayList<>();
    private final Set<Long> failingDeletions = new HashSet<>();

    StorageMocks() {
        writer.when(() -> DBWriter.deleteFeedMediaOfItem(any(Context.class), any(FeedMedia.class)))
                .thenAnswer(invocation -> {
                    FeedMedia media = invocation.getArgument(1);
                    deletedMedia.add(media);
                    if (failingDeletions.contains(media.getId())) {
                        return CompletableFuture.failedFuture(new IllegalStateException("deletion failed"));
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }

    static FeedItem downloadedEpisode(long id, Date pubDate, Feed feed) {
        FeedItem item = new FeedItem(id, "Episode " + id, "guid" + id, "http://example.com/" + id, pubDate,
                FeedItem.UNPLAYED, feed);
        FeedMedia media = new FeedMedia(id, item, 0, 0, 0, "audio/mpeg", "/episodes/" + id + ".mp3",
                "http://example.com/" + id + ".mp3", 1, null, 0, 0);
        item.setMedia(media);
        return item;
    }

    static Feed remoteFeed() {
        return new Feed("http://example.com/feed.xml", null, "Feed");
    }

    static Feed localFeed() {
        return new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://tree/folder", null, "Local");
    }

    void setDownloadedEpisodes(List<FeedItem> items) {
        reader.when(() -> DBReader.getEpisodes(anyInt(), anyInt(), any(FeedItemFilter.class), any(SortOrder.class)))
                .thenReturn(items);
    }

    void setDownloadedCount(int count) {
        reader.when(() -> DBReader.getTotalEpisodeCount(any(FeedItemFilter.class))).thenReturn(count);
    }

    void setEpisodeCacheSize(int size) {
        preferences.when(UserPreferences::getEpisodeCacheSize).thenReturn(size);
    }

    void setAutoDeleteLocal(boolean autoDeleteLocal) {
        preferences.when(UserPreferences::isAutoDeleteLocal).thenReturn(autoDeleteLocal);
    }

    void setEpisodeCleanupValue(int value) {
        preferences.when(UserPreferences::getEpisodeCleanupValue).thenReturn(value);
    }

    void failDeletionOf(FeedItem item) {
        failingDeletions.add(item.getMedia().getId());
    }

    List<Long> deletedMediaIds() {
        List<Long> ids = new ArrayList<>();
        for (FeedMedia media : deletedMedia) {
            ids.add(media.getId());
        }
        return ids;
    }

    @Override
    public void close() {
        preferences.close();
        writer.close();
        reader.close();
    }
}
