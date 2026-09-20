package de.danoeh.antennapod.event.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SettingsEventsTest {

    @Test
    public void skipEventKeepsIntroEndingAndFeedApart() {
        SkipIntroEndingChangedEvent event = new SkipIntroEndingChangedEvent(15, 30, 99);
        assertEquals(15, event.getSkipIntro());
        assertEquals(30, event.getSkipEnding());
        assertEquals(99, event.getFeedId());
    }
}
