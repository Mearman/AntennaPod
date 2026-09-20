package de.danoeh.antennapod.storage.database;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;

public class EventCollector {
    private final List<Object> events = new ArrayList<>();

    public void register() {
        EventBus.getDefault().register(this);
    }

    public void unregister() {
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public synchronized void onEvent(Object event) {
        events.add(event);
    }

    public synchronized <T> List<T> eventsOfType(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Object event : events) {
            if (type.isInstance(event)) {
                result.add(type.cast(event));
            }
        }
        return result;
    }

    public synchronized void clear() {
        events.clear();
    }
}
