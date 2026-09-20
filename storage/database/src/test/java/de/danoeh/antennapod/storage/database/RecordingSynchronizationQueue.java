package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingSynchronizationQueue extends SynchronizationQueue {
    private final List<String> addedFeeds = new CopyOnWriteArrayList<>();
    private final List<String> removedFeeds = new CopyOnWriteArrayList<>();
    private final List<EpisodeAction> episodeActions = new CopyOnWriteArrayList<>();
    private final List<FeedMedia> playedMedia = new CopyOnWriteArrayList<>();

    @Override
    public void sync() {
    }

    @Override
    public void syncImmediately() {
    }

    @Override
    public void fullSync() {
    }

    @Override
    public void syncIfNotSyncedRecently() {
    }

    @Override
    public void clear() {
    }

    @Override
    public void enqueueFeedAdded(String downloadUrl) {
        addedFeeds.add(downloadUrl);
    }

    @Override
    public void enqueueFeedRemoved(String downloadUrl) {
        removedFeeds.add(downloadUrl);
    }

    @Override
    public void enqueueEpisodeAction(EpisodeAction action) {
        episodeActions.add(action);
    }

    @Override
    public void enqueueEpisodePlayed(FeedMedia media, boolean completed) {
        playedMedia.add(media);
    }

    public List<String> getAddedFeeds() {
        return addedFeeds;
    }

    public List<String> getRemovedFeeds() {
        return removedFeeds;
    }

    public List<EpisodeAction> getEpisodeActions() {
        return episodeActions;
    }

    public List<FeedMedia> getPlayedMedia() {
        return playedMedia;
    }
}
