package de.test.antennapod.playback;

import de.danoeh.antennapod.event.MessageEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects the messages that the playback service shows to the user.
 */
public class MessageEventRecorder {
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

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
}
