package de.danoeh.antennapod.storage.database;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReleaseScheduleGuesserScenariosTest {
    private static final long HOUR = 3600L * 1000L;
    private static final long DAY = 24L * HOUR;

    private TimeZone originalTimeZone;

    @Before
    public void useUtcTimeZone() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    private static Date localDate(int year, int month, int day, int hour) {
        return new GregorianCalendar(year, month, day, hour, 0, 0).getTime();
    }

    private static Date monday() {
        return localDate(2020, Calendar.JANUARY, 6, 12);
    }

    private static List<Date> spacedBy(Date start, int count, long... gaps) {
        List<Date> dates = new ArrayList<>();
        long time = start.getTime();
        for (int i = 0; i < count; i++) {
            dates.add(new Date(time));
            time += gaps[i % gaps.length];
        }
        return dates;
    }

    private static Date last(List<Date> dates) {
        return dates.get(dates.size() - 1);
    }

    @Test
    public void episodesReleasedEveryTwoWeeksAreGuessedAsBiweekly() {
        List<Date> dates = spacedBy(monday(), 8, 14 * DAY);

        ReleaseScheduleGuesser.Guess guess = ReleaseScheduleGuesser.performGuess(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.BIWEEKLY, guess.schedule);
        assertEquals(Collections.singletonList(Calendar.MONDAY), guess.days);
        assertEquals(new Date(last(dates).getTime() + 14 * DAY), guess.nextExpectedDate);
    }

    @Test
    public void episodesReleasedEveryFourWeeksAreGuessedAsFourWeekly() {
        List<Date> dates = spacedBy(monday(), 8, 28 * DAY);

        ReleaseScheduleGuesser.Guess guess = ReleaseScheduleGuesser.performGuess(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.FOURWEEKLY, guess.schedule);
        assertEquals(Collections.singletonList(Calendar.MONDAY), guess.days);
        assertEquals(new Date(last(dates).getTime() + 28 * DAY), guess.nextExpectedDate);
    }

    @Test
    public void episodesOnFixedDaysOfWeekAreGuessedAsSpecificDays() {
        List<Date> dates = new ArrayList<>();
        for (int week = 0; week < 6; week++) {
            dates.add(new Date(monday().getTime() + week * 7L * DAY));
            dates.add(new Date(monday().getTime() + (week * 7L + 3) * DAY));
        }

        ReleaseScheduleGuesser.Guess guess = ReleaseScheduleGuesser.performGuess(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.SPECIFIC_DAYS, guess.schedule);
        assertEquals(Arrays.asList(Calendar.MONDAY, Calendar.THURSDAY), guess.days);
        assertEquals(new Date(monday().getTime() + (5 * 7L + 7) * DAY), guess.nextExpectedDate);
    }

    @Test
    public void weeklyEpisodesWithIrregularGapsStillAreGuessedAsWeekly() {
        List<Date> dates = spacedBy(monday(), 9, 7 * DAY, 7 * DAY, 14 * DAY, 14 * DAY, 14 * DAY, 21 * DAY,
                21 * DAY, 28 * DAY);

        ReleaseScheduleGuesser.Guess guess = ReleaseScheduleGuesser.performGuess(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKLY, guess.schedule);
        assertEquals(Collections.singletonList(Calendar.MONDAY), guess.days);
        assertEquals(new Date(last(dates).getTime() + 7 * DAY), guess.nextExpectedDate);
    }

    @Test
    public void onlyTheMostRecentReleasesInfluenceTheGuess() {
        List<Date> dates = new ArrayList<>(spacedBy(localDate(2018, Calendar.JANUARY, 1, 12), 15, 3 * DAY));
        dates.addAll(spacedBy(new Date(last(dates).getTime() + 60 * DAY), 22, 7 * DAY));

        ReleaseScheduleGuesser.Guess guess = ReleaseScheduleGuesser.performGuess(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKLY, guess.schedule);
        assertEquals(new Date(last(dates).getTime() + 7 * DAY), guess.nextExpectedDate);
    }

    @Test
    public void irregularReleasesAreUnknownAndNextReleaseIsExpectedAfterSixTenthsOfMedianGap() {
        List<Date> dates = new ArrayList<>();
        long[] offsetsInDays = {0, 1, 4, 5, 9, 11, 12, 17, 18, 21, 25, 26};
        for (long offset : offsetsInDays) {
            dates.add(new Date(monday().getTime() + offset * DAY));
        }
        long medianGap = 2 * DAY;
        long expectedNext = last(dates).getTime() + (long) (0.6 * medianGap);

        ReleaseScheduleGuesser.Guess guess = ReleaseScheduleGuesser.performGuess(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.UNKNOWN, guess.schedule);
        assertNull(guess.days);
        assertEquals(expectedNext, guess.nextExpectedDate.getTime(), 1000);
    }

    @Test
    public void manyReleasesOnLastDayPushNextReleaseToTheFollowingDay() {
        List<Date> dates = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            dates.add(new Date(monday().getTime() + day * DAY));
        }
        for (int i = 0; i < 4; i++) {
            dates.add(new Date(monday().getTime() + 7 * DAY + i * HOUR));
        }

        ReleaseScheduleGuesser.Guess guess = ReleaseScheduleGuesser.performGuess(dates);

        assertTrue(guess.multipleReleasesPerDay);
        assertEquals(ReleaseScheduleGuesser.Schedule.DAILY, guess.schedule);
        assertEquals(localDate(2020, Calendar.JANUARY, 14, 12), guess.nextExpectedDate);
    }

    @Test
    public void averageReleasesOnLastDayPlaceNextReleaseOneMedianGapLater() {
        List<Date> dates = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            dates.add(new Date(monday().getTime() + day * DAY));
            dates.add(new Date(monday().getTime() + day * DAY + 2 * HOUR));
        }

        ReleaseScheduleGuesser.Guess guess = ReleaseScheduleGuesser.performGuess(dates);

        assertTrue(guess.multipleReleasesPerDay);
        assertEquals(localDate(2020, Calendar.JANUARY, 12, 16), guess.nextExpectedDate);
    }
}
