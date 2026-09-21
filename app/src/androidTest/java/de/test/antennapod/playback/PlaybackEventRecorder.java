package de.test.antennapod.playback;

import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.PlayerErrorEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaybackEventRecorder {
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

    public void register() {
        EventBus.getDefault().register(this);
    }

    public void unregister() {
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessage(MessageEvent event) {
        messages.add(event.message);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerError(PlayerErrorEvent event) {
        errors.add(event.getMessage());
    }

    public boolean received(String message) {
        synchronized (messages) {
            return messages.contains(message);
        }
    }

    public List<String> getMessages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    public List<String> getErrors() {
        synchronized (errors) {
            return new ArrayList<>(errors);
        }
    }
}
