package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SleepTimerPreferencesTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        SleepTimerPreferences.init(context);
    }

    @Test
    public void defaultsAreClockTimerWithFifteenMinutes() {
        assertEquals(SleepTimerType.CLOCK, SleepTimerPreferences.getSleepTimerType());
        assertEquals("15", SleepTimerPreferences.lastTimerValue());
        assertFalse(SleepTimerPreferences.vibrate());
        assertTrue(SleepTimerPreferences.shakeToReset());
        assertFalse(SleepTimerPreferences.autoEnable());
        assertEquals(22, SleepTimerPreferences.autoEnableFrom());
        assertEquals(6, SleepTimerPreferences.autoEnableTo());
    }

    @Test
    public void lastTimerValueIsRememberedSeparatelyPerTimerType() {
        SleepTimerPreferences.setLastTimer("40");
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.EPISODES);
        assertEquals("1", SleepTimerPreferences.lastTimerValue());

        SleepTimerPreferences.setLastTimer("3");
        assertEquals("3", SleepTimerPreferences.lastTimerValue());

        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        assertEquals("40", SleepTimerPreferences.lastTimerValue());
    }

    @Test
    public void timerMillisOrEpisodesConvertsMinutesToMillisForClockTimer() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        SleepTimerPreferences.setLastTimer("20");
        assertEquals(TimeUnit.MINUTES.toMillis(20), SleepTimerPreferences.timerMillisOrEpisodes());
    }

    @Test
    public void timerMillisOrEpisodesReturnsEpisodeCountForEpisodeTimer() {
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.EPISODES);
        SleepTimerPreferences.setLastTimer("4");
        assertEquals(4, SleepTimerPreferences.timerMillisOrEpisodes());
    }

    @Test
    public void booleanSettingsArePersisted() {
        SleepTimerPreferences.setVibrate(true);
        SleepTimerPreferences.setShakeToReset(false);
        SleepTimerPreferences.setAutoEnable(true);

        assertTrue(SleepTimerPreferences.vibrate());
        assertFalse(SleepTimerPreferences.shakeToReset());
        assertTrue(SleepTimerPreferences.autoEnable());
    }

    @Test
    public void autoEnableHoursArePersisted() {
        SleepTimerPreferences.setAutoEnableFrom(20);
        SleepTimerPreferences.setAutoEnableTo(7);

        assertEquals(20, SleepTimerPreferences.autoEnableFrom());
        assertEquals(7, SleepTimerPreferences.autoEnableTo());
    }

    @Test
    public void autoEnableDurationSpansMidnightWhenFromIsAfterTo() {
        SleepTimerPreferences.setAutoEnableFrom(22);
        SleepTimerPreferences.setAutoEnableTo(6);
        assertEquals(8, SleepTimerPreferences.autoEnableDuration());
    }

    @Test
    public void autoEnableDurationStaysWithinOneDayWhenFromIsBeforeTo() {
        SleepTimerPreferences.setAutoEnableFrom(6);
        SleepTimerPreferences.setAutoEnableTo(22);
        assertEquals(16, SleepTimerPreferences.autoEnableDuration());
    }

    @Test
    public void autoEnableDurationIsFullDayWhenFromEqualsTo() {
        SleepTimerPreferences.setAutoEnableFrom(9);
        SleepTimerPreferences.setAutoEnableTo(9);
        assertEquals(24, SleepTimerPreferences.autoEnableDuration());
    }

    @Test
    public void isInTimeRangeIncludesStartAndExcludesEndWithinOneDay() {
        assertTrue(SleepTimerPreferences.isInTimeRange(8, 17, 8));
        assertTrue(SleepTimerPreferences.isInTimeRange(8, 17, 16));
        assertFalse(SleepTimerPreferences.isInTimeRange(8, 17, 17));
        assertFalse(SleepTimerPreferences.isInTimeRange(8, 17, 7));
    }

    @Test
    public void isInTimeRangeWrapsAroundMidnight() {
        assertTrue(SleepTimerPreferences.isInTimeRange(22, 6, 22));
        assertTrue(SleepTimerPreferences.isInTimeRange(22, 6, 23));
        assertTrue(SleepTimerPreferences.isInTimeRange(22, 6, 0));
        assertTrue(SleepTimerPreferences.isInTimeRange(22, 6, 5));
        assertFalse(SleepTimerPreferences.isInTimeRange(22, 6, 6));
        assertFalse(SleepTimerPreferences.isInTimeRange(22, 6, 12));
    }
}
