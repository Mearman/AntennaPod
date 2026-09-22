package de.test.antennapod.storage.database;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.ReleaseScheduleGuesser;
import de.danoeh.antennapod.storage.database.ReleaseScheduleGuesser.Schedule;
import de.test.antennapod.service.download.DownloadTestFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ReleaseScheduleTest {
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);
    private static final int RELEASES = 10;
    private static final int ANCHOR_YEAR = 2024;

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private TimeZone originalTimeZone;

    @Before
    public void setUp() throws Exception {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        fixture.setUp();
    }

    @After
    public void tearDown() throws Exception {
        fixture.tearDown();
        TimeZone.setDefault(originalTimeZone);
    }

    private GregorianCalendar anchor() {
        return new GregorianCalendar(ANCHOR_YEAR, Calendar.JUNE, 15);
    }

    private ReleaseScheduleGuesser.Guess guessFor(String title, List<Date> releases) throws IOException {
        Feed hosted = fixture.newFeed(title, releases.size());
        for (int i = 0; i < releases.size(); i++) {
            hosted.getItemAtIndex(i).setPubDate(releases.get(i));
        }
        hosted.setDownloadUrl(fixture.hostFeed(hosted));
        Feed stored = fixture.subscribe(hosted);
        List<FeedItem> items = DBReader.getFeedItemList(stored, FeedItemFilter.unfiltered(),
                SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE);
        List<Date> dates = new ArrayList<>();
        for (FeedItem item : items) {
            dates.add(item.getPubDate());
        }
        return ReleaseScheduleGuesser.performGuess(dates);
    }

    private List<Date> every(int stepDays, int hour, int count) {
        List<Date> dates = new ArrayList<>();
        GregorianCalendar day = anchor();
        day.add(Calendar.DAY_OF_MONTH, -stepDays * count);
        day.set(Calendar.HOUR_OF_DAY, hour);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < count; i++) {
            day.add(Calendar.DAY_OF_MONTH, stepDays);
            dates.add(day.getTime());
        }
        return dates;
    }

    @Test
    public void dailyPodcastIsRecognised() throws Exception {
        ReleaseScheduleGuesser.Guess guess = guessFor("Daily", every(1, 16, RELEASES));

        assertEquals(Schedule.DAILY, guess.schedule);
        assertFalse(guess.multipleReleasesPerDay);
        assertNotNull(guess.nextExpectedDate);
    }

    @Test
    public void weeklyPodcastIsRecognisedAndNextReleaseIsAWeekAfterTheLast() throws Exception {
        List<Date> releases = every(7, 9, RELEASES);

        ReleaseScheduleGuesser.Guess guess = guessFor("Weekly", releases);

        assertEquals(Schedule.WEEKLY, guess.schedule);
        long expected = releases.get(releases.size() - 1).getTime() + 7 * DAY_MILLIS;
        assertTrue(Math.abs(guess.nextExpectedDate.getTime() - expected) < 2 * 3600 * 1000L);
    }

    @Test
    public void biweeklyPodcastIsRecognised() throws Exception {
        assertEquals(Schedule.BIWEEKLY, guessFor("Biweekly", every(14, 9, RELEASES)).schedule);
    }

    @Test
    public void fourWeeklyPodcastIsRecognised() throws Exception {
        assertEquals(Schedule.FOURWEEKLY, guessFor("Fourweekly", every(28, 9, RELEASES)).schedule);
    }

    @Test
    public void monthlyPodcastIsRecognised() throws Exception {
        List<Date> releases = new ArrayList<>();
        GregorianCalendar month = anchor();
        month.add(Calendar.MONTH, -RELEASES);
        month.set(Calendar.DAY_OF_MONTH, 15);
        month.set(Calendar.HOUR_OF_DAY, 12);
        for (int i = 0; i < RELEASES; i++) {
            month.add(Calendar.MONTH, 1);
            releases.add(month.getTime());
        }

        assertEquals(Schedule.MONTHLY, guessFor("Monthly", releases).schedule);
    }

    @Test
    public void podcastThatReleasesOnWeekdaysIsRecognised() throws Exception {
        List<Date> releases = new ArrayList<>();
        GregorianCalendar day = anchor();
        day.add(Calendar.DAY_OF_MONTH, -30);
        day.set(Calendar.HOUR_OF_DAY, 6);
        while (releases.size() < 15) {
            day.add(Calendar.DAY_OF_MONTH, 1);
            int weekday = day.get(Calendar.DAY_OF_WEEK);
            if (weekday != Calendar.SATURDAY && weekday != Calendar.SUNDAY) {
                releases.add(day.getTime());
            }
        }

        assertEquals(Schedule.WEEKDAYS, guessFor("Weekdays", releases).schedule);
    }

    @Test
    public void podcastThatReleasesTwiceADayIsFlagged() throws Exception {
        List<Date> releases = new ArrayList<>();
        for (Date morning : every(1, 8, RELEASES)) {
            releases.add(morning);
            releases.add(new Date(morning.getTime() + 8 * 3600 * 1000L));
        }

        ReleaseScheduleGuesser.Guess guess = guessFor("Twice a day", releases);

        assertTrue(guess.multipleReleasesPerDay);
        assertEquals(Schedule.DAILY, guess.schedule);
    }

    @Test
    public void irregularPodcastHasNoKnownSchedule() throws Exception {
        List<Date> releases = new ArrayList<>();
        long[] gapsInDays = {1, 9, 2, 23, 4, 40, 3};
        long time = anchor().getTimeInMillis();
        for (long gap : gapsInDays) {
            time += gap * DAY_MILLIS;
            releases.add(new Date(time + gap * 3600 * 1000L));
        }

        ReleaseScheduleGuesser.Guess guess = guessFor("Irregular", releases);

        assertEquals(Schedule.UNKNOWN, guess.schedule);
    }

    @Test
    public void podcastWithASingleEpisodeHasNoGuess() throws Exception {
        ReleaseScheduleGuesser.Guess guess = guessFor("Single", every(1, 10, 1));

        assertEquals(Schedule.UNKNOWN, guess.schedule);
        assertNull(guess.nextExpectedDate);
        assertNull(guess.days);
    }

    @Test
    public void onlyTheMostRecentEpisodesDecideTheSchedule() throws Exception {
        List<Date> releases = every(1, 10, 20);
        for (Date old : every(30, 10, 5)) {
            releases.add(0, new Date(old.getTime() - 400 * DAY_MILLIS));
        }

        assertEquals(Schedule.DAILY, guessFor("Changed", releases).schedule);
    }
}
