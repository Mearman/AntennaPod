package de.danoeh.antennapod.net.download.service.episode;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import de.danoeh.antennapod.net.download.service.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAccessibilityManager;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class DownloadAnnouncerTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final AccessibilityManager manager =
            (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    private final ShadowAccessibilityManager shadow = shadowOf(manager);

    @Test
    public void startIsAnnouncedWhenAccessibilityIsEnabled() {
        shadow.setEnabled(true);

        DownloadAnnouncer.announceStart(context, "Episode One");

        List<AccessibilityEvent> events = shadow.getSentAccessibilityEvents();
        assertEquals(1, events.size());
        assertEquals(AccessibilityEvent.TYPE_ANNOUNCEMENT, events.get(0).getEventType());
        assertEquals(context.getString(R.string.download_started_talkback, "Episode One"),
                events.get(0).getText().get(0).toString());
    }

    @Test
    public void completionIsAnnouncedWithItsOwnMessage() {
        shadow.setEnabled(true);

        DownloadAnnouncer.announceCompleted(context, "Episode One");

        List<AccessibilityEvent> events = shadow.getSentAccessibilityEvents();
        assertEquals(1, events.size());
        assertEquals(context.getString(R.string.download_completed_talkback, "Episode One"),
                events.get(0).getText().get(0).toString());
    }

    @Test
    public void nothingIsAnnouncedWhenAccessibilityIsDisabled() {
        shadow.setEnabled(false);

        DownloadAnnouncer.announceStart(context, "Episode One");
        DownloadAnnouncer.announceCompleted(context, "Episode One");

        assertTrue(shadow.getSentAccessibilityEvents().isEmpty());
    }
}
