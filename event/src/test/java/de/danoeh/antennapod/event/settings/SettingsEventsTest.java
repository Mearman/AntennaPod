package de.danoeh.antennapod.event.settings;

import org.junit.Test;

import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;

import static org.junit.Assert.assertEquals;

public class SettingsEventsTest {

    @Test
    public void skipEventKeepsIntroEndingAndFeedApart() {
        SkipIntroEndingChangedEvent event = new SkipIntroEndingChangedEvent(15, 30, 99);
        assertEquals(15, event.getSkipIntro());
        assertEquals(30, event.getSkipEnding());
        assertEquals(99, event.getFeedId());
    }

    @Test
    public void speedPresetEventKeepsSpeedFeedAndSkipSilence() {
        SpeedPresetChangedEvent event =
                new SpeedPresetChangedEvent(1.25f, 7, FeedPreferences.SkipSilence.AGGRESSIVE);
        assertEquals(1.25f, event.getSpeed(), 0f);
        assertEquals(7, event.getFeedId());
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, event.getSkipSilence());
    }

    @Test
    public void volumeAdaptionEventKeepsSettingAndFeed() {
        VolumeAdaptionChangedEvent event = new VolumeAdaptionChangedEvent(VolumeAdaptionSetting.OFF, 8);
        assertEquals(VolumeAdaptionSetting.OFF, event.getVolumeAdaptionSetting());
        assertEquals(8, event.getFeedId());
    }
}
