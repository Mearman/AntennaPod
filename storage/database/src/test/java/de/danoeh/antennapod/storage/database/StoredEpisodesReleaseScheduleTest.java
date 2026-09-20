package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class StoredEpisodesReleaseScheduleTest extends DatabaseTestBase {
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

    private ReleaseScheduleGuesser.Guess guessFor(List<Date> releaseDates) {
        Feed feed = storeFeed("Schedule");
        List<FeedItem> items = new ArrayList<>();
        for (int i = 0; i < releaseDates.size(); i++) {
            items.add(new FeedItem(0, "episode " + i, "guid-" + i, "link", releaseDates.get(i), FeedItem.UNPLAYED,
                    feed));
        }
        await(DBWriter.setItemList(items));
        List<FeedItem> stored = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD,
                0, Integer.MAX_VALUE);
        List<Date> dates = new ArrayList<>();
        for (FeedItem item : stored) {
            dates.add(item.getPubDate());
        }
        return ReleaseScheduleGuesser.performGuess(dates);
    }

    @Test
    public void episodesReleasedEveryDayAreGuessedAsDaily() {
        List<Date> dates = spacedBy(monday(), 10, DAY);

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.DAILY, guess.schedule);
        assertFalse(guess.multipleReleasesPerDay);
        assertEquals(7, guess.days.size());
        assertEquals(new Date(dates.get(9).getTime() + DAY), guess.nextExpectedDate);
    }

    @Test
    public void episodesReleasedEveryWeekAreGuessedAsWeeklyOnThatDay() {
        List<Date> dates = spacedBy(monday(), 8, 7 * DAY);

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKLY, guess.schedule);
        assertEquals(Collections.singletonList(Calendar.MONDAY), guess.days);
        assertEquals(new Date(dates.get(7).getTime() + 7 * DAY), guess.nextExpectedDate);
    }

    @Test
    public void episodesReleasedEveryTwoWeeksAreGuessedAsBiweekly() {
        List<Date> dates = spacedBy(monday(), 8, 14 * DAY);

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.BIWEEKLY, guess.schedule);
        assertEquals(Collections.singletonList(Calendar.MONDAY), guess.days);
        assertEquals(new Date(dates.get(7).getTime() + 14 * DAY), guess.nextExpectedDate);
    }

    @Test
    public void episodesReleasedOnSameDayOfMonthAreGuessedAsMonthly() {
        List<Date> dates = new ArrayList<>();
        for (int month = Calendar.JANUARY; month <= Calendar.AUGUST; month++) {
            dates.add(localDate(2020, month, 15, 12));
        }

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.MONTHLY, guess.schedule);
        assertNull(guess.days);
        assertEquals(localDate(2020, Calendar.SEPTEMBER, 15, 12), guess.nextExpectedDate);
    }

    @Test
    public void episodesReleasedEveryFourWeeksAreGuessedAsFourWeekly() {
        List<Date> dates = spacedBy(monday(), 8, 28 * DAY);

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.FOURWEEKLY, guess.schedule);
        assertEquals(Collections.singletonList(Calendar.MONDAY), guess.days);
        assertEquals(new Date(dates.get(7).getTime() + 28 * DAY), guess.nextExpectedDate);
    }

    @Test
    public void episodesOnEveryWorkingDayAreGuessedAsWeekdays() {
        List<Date> dates = new ArrayList<>();
        for (int week = 0; week < 3; week++) {
            for (int day = 0; day < 5; day++) {
                dates.add(new Date(monday().getTime() + (week * 7L + day) * DAY));
            }
        }

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKDAYS, guess.schedule);
        assertEquals(Arrays.asList(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY), guess.days);
        assertEquals(new Date(monday().getTime() + 3 * 7L * DAY), guess.nextExpectedDate);
    }

    @Test
    public void episodesOnFixedDaysOfWeekAreGuessedAsSpecificDays() {
        List<Date> dates = new ArrayList<>();
        for (int week = 0; week < 6; week++) {
            dates.add(new Date(monday().getTime() + week * 7L * DAY));
            dates.add(new Date(monday().getTime() + (week * 7L + 3) * DAY));
        }

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.SPECIFIC_DAYS, guess.schedule);
        assertEquals(Arrays.asList(Calendar.MONDAY, Calendar.THURSDAY), guess.days);
        assertEquals(new Date(monday().getTime() + (5 * 7L + 7) * DAY), guess.nextExpectedDate);
    }

    @Test
    public void weeklyEpisodesWithIrregularGapsStillAreGuessedAsWeekly() {
        List<Date> dates = spacedBy(monday(), 9, 7 * DAY, 7 * DAY, 14 * DAY, 14 * DAY, 14 * DAY, 21 * DAY,
                21 * DAY, 28 * DAY);

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKLY, guess.schedule);
        assertEquals(Collections.singletonList(Calendar.MONDAY), guess.days);
        assertEquals(new Date(dates.get(8).getTime() + 7 * DAY), guess.nextExpectedDate);
    }

    @Test
    public void irregularReleasesAreUnknown() {
        List<Date> dates = new ArrayList<>();
        long[] offsetsInDays = {0, 1, 4, 5, 9, 11, 12, 17, 18, 21, 25, 26};
        for (long offset : offsetsInDays) {
            dates.add(new Date(monday().getTime() + offset * DAY));
        }

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.UNKNOWN, guess.schedule);
        assertNull(guess.days);
        assertTrue(guess.nextExpectedDate.after(dates.get(dates.size() - 1)));
    }

    @Test
    public void singleEpisodeCannotBeGuessed() {
        ReleaseScheduleGuesser.Guess guess = guessFor(Collections.singletonList(monday()));

        assertEquals(ReleaseScheduleGuesser.Schedule.UNKNOWN, guess.schedule);
        assertNull(guess.nextExpectedDate);
        assertNull(guess.days);
    }

    @Test
    public void onlyTheMostRecentReleasesInfluenceTheGuess() {
        List<Date> dates = new ArrayList<>();
        dates.addAll(spacedBy(localDate(2018, Calendar.JANUARY, 1, 12), 15, 3 * DAY));
        Date lastOfOldPhase = dates.get(dates.size() - 1);
        dates.addAll(spacedBy(new Date(lastOfOldPhase.getTime() + 60 * DAY), 22, 7 * DAY));

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKLY, guess.schedule);
    }

    @Test
    public void severalReleasesPerDayOnEveryDayAreGuessedAsDailyWithMultipleReleases() {
        List<Date> dates = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            dates.add(new Date(monday().getTime() + day * DAY));
            dates.add(new Date(monday().getTime() + day * DAY + 2 * HOUR));
        }

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertTrue(guess.multipleReleasesPerDay);
        assertEquals(ReleaseScheduleGuesser.Schedule.DAILY, guess.schedule);
        assertEquals(7, guess.days.size());
    }

    @Test
    public void severalReleasesPerDayOnWorkingDaysAreGuessedAsWeekdays() {
        List<Date> dates = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            dates.add(new Date(monday().getTime() + day * DAY));
            dates.add(new Date(monday().getTime() + day * DAY + 2 * HOUR));
        }

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertTrue(guess.multipleReleasesPerDay);
        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKDAYS, guess.schedule);
    }

    @Test
    public void severalReleasesPerDayOnSomeDaysAreGuessedAsSpecificDays() {
        List<Date> dates = new ArrayList<>();
        for (int week = 0; week < 2; week++) {
            for (int day : new int[] {0, 3}) {
                long base = monday().getTime() + (week * 7L + day) * DAY;
                dates.add(new Date(base));
                dates.add(new Date(base + 2 * HOUR));
            }
        }

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertTrue(guess.multipleReleasesPerDay);
        assertEquals(ReleaseScheduleGuesser.Schedule.SPECIFIC_DAYS, guess.schedule);
        assertEquals(Arrays.asList(Calendar.MONDAY, Calendar.THURSDAY), guess.days);
    }

    @Test
    public void manyReleasesOnLastDayPushNextReleaseToNextAllowedDay() {
        List<Date> dates = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            dates.add(new Date(monday().getTime() + day * DAY));
        }
        for (int i = 0; i < 4; i++) {
            dates.add(new Date(monday().getTime() + 7 * DAY + i * HOUR));
        }

        ReleaseScheduleGuesser.Guess guess = guessFor(dates);

        assertTrue(guess.multipleReleasesPerDay);
        assertTrue(guess.nextExpectedDate.getTime() >= monday().getTime() + 8 * DAY);
    }
}
