package de.danoeh.antennapod.storage.preferences;

import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class SleepTimerPreferencesStoredValuesTest extends StoredPreferencesTestBase {

    @Test
    public void timerDefaultsToFifteenMinutesOnTheClock() {
        assertEquals(SleepTimerType.CLOCK, SleepTimerPreferences.getSleepTimerType());
        assertEquals("15", SleepTimerPreferences.lastTimerValue());
        assertEquals(TimeUnit.MINUTES.toMillis(15), SleepTimerPreferences.timerMillisOrEpisodes());
    }

    @Test
    public void clockAndEpisodeTimerValuesAreRememberedSeparately() {
        SleepTimerPreferences.setLastTimer("45");
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.EPISODES);

        assertEquals("1", SleepTimerPreferences.lastTimerValue());

        SleepTimerPreferences.setLastTimer("3");

        assertEquals(3, SleepTimerPreferences.timerMillisOrEpisodes());
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        assertEquals("45", SleepTimerPreferences.lastTimerValue());
        assertEquals(TimeUnit.MINUTES.toMillis(45), SleepTimerPreferences.timerMillisOrEpisodes());
    }

    @Test
    public void unknownTimerTypeIndexIsTreatedAsEpisodes() {
        assertEquals(SleepTimerType.CLOCK, SleepTimerType.fromIndex(0));
        assertEquals(SleepTimerType.EPISODES, SleepTimerType.fromIndex(1));
        assertEquals(SleepTimerType.EPISODES, SleepTimerType.fromIndex(99));
    }

    @Test
    public void vibrateShakeAndAutoEnableFlagsRoundTrip() {
        assertFalse(SleepTimerPreferences.vibrate());
        assertTrue(SleepTimerPreferences.shakeToReset());
        assertFalse(SleepTimerPreferences.autoEnable());

        SleepTimerPreferences.setVibrate(true);
        SleepTimerPreferences.setShakeToReset(false);
        SleepTimerPreferences.setAutoEnable(true);

        assertTrue(SleepTimerPreferences.vibrate());
        assertFalse(SleepTimerPreferences.shakeToReset());
        assertTrue(SleepTimerPreferences.autoEnable());
    }

    @Test
    public void autoEnableWindowDefaultsToTenAtNightUntilSixInTheMorning() {
        assertEquals(22, SleepTimerPreferences.autoEnableFrom());
        assertEquals(6, SleepTimerPreferences.autoEnableTo());
        assertEquals(8, SleepTimerPreferences.autoEnableDuration());
    }

    @Test
    public void autoEnableDurationHandlesWindowsWithinOneDayAndAcrossMidnight() {
        SleepTimerPreferences.setAutoEnableFrom(9);
        SleepTimerPreferences.setAutoEnableTo(17);
        assertEquals(9, SleepTimerPreferences.autoEnableFrom());
        assertEquals(17, SleepTimerPreferences.autoEnableTo());
        assertEquals(8, SleepTimerPreferences.autoEnableDuration());

        SleepTimerPreferences.setAutoEnableFrom(23);
        SleepTimerPreferences.setAutoEnableTo(1);
        assertEquals(2, SleepTimerPreferences.autoEnableDuration());

        SleepTimerPreferences.setAutoEnableFrom(5);
        SleepTimerPreferences.setAutoEnableTo(5);
        assertEquals(24, SleepTimerPreferences.autoEnableDuration());
    }

    @Test
    public void timeRangeCoversHoursWithinOneDayAndWrapsPastMidnight() {
        assertTrue(SleepTimerPreferences.isInTimeRange(9, 17, 9));
        assertTrue(SleepTimerPreferences.isInTimeRange(9, 17, 16));
        assertFalse(SleepTimerPreferences.isInTimeRange(9, 17, 17));
        assertFalse(SleepTimerPreferences.isInTimeRange(9, 17, 8));

        assertTrue(SleepTimerPreferences.isInTimeRange(22, 6, 23));
        assertTrue(SleepTimerPreferences.isInTimeRange(22, 6, 22));
        assertTrue(SleepTimerPreferences.isInTimeRange(22, 6, 0));
        assertTrue(SleepTimerPreferences.isInTimeRange(22, 6, 5));
        assertFalse(SleepTimerPreferences.isInTimeRange(22, 6, 6));
        assertFalse(SleepTimerPreferences.isInTimeRange(22, 6, 12));
    }
}
