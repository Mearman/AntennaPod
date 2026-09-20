package de.danoeh.antennapod.storage.preferences;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SleepTimerTypeTest {

    @Test
    public void fromIndexReturnsTypeWithMatchingIndex() {
        assertEquals(SleepTimerType.CLOCK, SleepTimerType.fromIndex(SleepTimerType.CLOCK.index));
        assertEquals(SleepTimerType.EPISODES, SleepTimerType.fromIndex(SleepTimerType.EPISODES.index));
    }

    @Test
    public void fromIndexFallsBackToEpisodesForUnknownIndex() {
        assertEquals(SleepTimerType.EPISODES, SleepTimerType.fromIndex(-1));
        assertEquals(SleepTimerType.EPISODES, SleepTimerType.fromIndex(42));
    }
}
