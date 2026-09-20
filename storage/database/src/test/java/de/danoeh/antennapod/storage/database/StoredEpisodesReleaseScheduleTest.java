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
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class StoredEpisodesReleaseScheduleTest extends DatabaseTestBase {
    private static final long DAY = 24L * 3600L * 1000L;

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
    public void episodesStoredInScrambledOrderAreGuessedByTheirReleaseDates() {
        List<Date> dates = spacedBy(monday(), 8, 7 * DAY);
        List<Date> scrambled = new ArrayList<>(dates);
        Collections.shuffle(scrambled, new Random(7));

        ReleaseScheduleGuesser.Guess guess = guessFor(scrambled);

        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKLY, guess.schedule);
        assertEquals(new Date(dates.get(7).getTime() + 7 * DAY), guess.nextExpectedDate);
    }
}
