package de.danoeh.antennapod.model.playback;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimerValueTest {

    @Test
    public void displayValueAndMillisValue_areKeptIndependently() {
        TimerValue value = new TimerValue(3, 180000);
        assertEquals(3, value.getDisplayValue());
        assertEquals(180000, value.getMillisValue());
    }
}
