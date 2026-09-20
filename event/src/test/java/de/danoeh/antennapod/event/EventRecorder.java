package de.danoeh.antennapod.event;

import de.danoeh.antennapod.event.playback.PlaybackHistoryEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;

public class EventRecorder {
    private final List<Object> received = new ArrayList<>();

    public void register() {
        EventBus.getDefault().register(this);
    }

    public void unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    public void clear() {
        received.clear();
    }

    @Subscribe
    public void onQueueEvent(QueueEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onFeedItemEvent(FeedItemEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onFeedEvent(FeedEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onFeedListUpdateEvent(FeedListUpdateEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onEpisodeDownloadEvent(EpisodeDownloadEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onMessageEvent(MessageEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onPlaybackHistoryEvent(PlaybackHistoryEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onDownloadLogEvent(DownloadLogEvent event) {
        received.add(event);
    }

    public <T> List<T> of(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Object event : received) {
            if (type.isInstance(event)) {
                result.add(type.cast(event));
            }
        }
        return result;
    }

    public <T> T single(Class<T> type) {
        List<T> matching = of(type);
        if (matching.size() != 1) {
            throw new AssertionError("Expected exactly one " + type.getSimpleName()
                    + " but received " + matching.size());
        }
        return matching.get(0);
    }
}
