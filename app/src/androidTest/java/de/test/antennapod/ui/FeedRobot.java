package de.test.antennapod.ui;

import androidx.test.espresso.Espresso;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateManagerImpl;
import de.danoeh.antennapod.storage.database.DBReader;
import de.test.antennapod.EspressoTestUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import java.lang.reflect.Field;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickBottomNavItem;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static de.test.antennapod.NthMatcher.first;
import static org.hamcrest.Matchers.allOf;

public class FeedRobot {
    public static final long UI_TIMEOUT_MS = 30000;
    public static final long DB_TIMEOUT_SECONDS = 30;

    private FeedRobot() {
    }

    public static void addFeedByUrl(String url) {
        onView(withId(R.id.addViaUrlButton)).perform(scrollTo(), click());
        onView(withId(R.id.textInput)).perform(replaceText(url));
        onView(withText(R.string.confirm_label)).perform(scrollTo(), click());
        Espresso.closeSoftKeyboard();
    }

    public static void subscribeToPreviewedFeed() {
        waitForViewGlobally(withText(R.string.subscribe_label), UI_TIMEOUT_MS);
        onView(withText(R.string.subscribe_label)).perform(click());
        waitForViewGlobally(withId(R.id.butShowSettings), UI_TIMEOUT_MS);
    }

    public static void subscribeByUrl(String url) {
        addFeedByUrl(url);
        subscribeToPreviewedFeed();
    }

    public static long utc(int year, int month, int day, int hour, int minute, int second) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, second);
        return calendar.getTimeInMillis();
    }

    public static void assertPreviewHeader(String title, String author) {
        waitForViewGlobally(allOf(withId(R.id.txtvTitle), withText(title)), UI_TIMEOUT_MS);
        onView(allOf(withId(R.id.txtvAuthor), withText(author))).check(matches(isDisplayed()));
    }

    public static void openFeedMenu(int titleRes) {
        onView(first(EspressoTestUtils.actionBarOverflow())).perform(click());
        onView(withText(titleRes)).perform(click());
    }

    public static void refreshFromMenu() throws Exception {
        resetRefreshCooldown();
        openFeedMenu(R.string.refresh_label);
    }

    public static void refreshAllFromSubscriptions() throws Exception {
        resetRefreshCooldown();
        clickBottomNavItem(R.string.subscriptions_label_short);
        openFeedMenu(R.string.refresh_label);
    }

    private static void resetRefreshCooldown() throws Exception {
        Field lastRefresh = FeedUpdateManagerImpl.class.getDeclaredField("lastManualRefreshTime");
        lastRefresh.setAccessible(true);
        lastRefresh.setLong(null, 0);
    }

    public static Feed awaitFeed(String downloadUrl) {
        try {
            Awaitility.await().atMost(DB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .until(() -> findFeed(downloadUrl) != null);
        } catch (ConditionTimeoutException e) {
            throw new AssertionError("No feed with URL " + downloadUrl + " but " + DBReader.getFeedListDownloadUrls(false));
        }
        return findFeed(downloadUrl);
    }

    public static Feed findFeed(String downloadUrl) {
        for (Feed feed : DBReader.getFeedList()) {
            if (downloadUrl.equals(feed.getDownloadUrl())) {
                return DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
            }
        }
        return null;
    }

    public static Feed reload(Feed feed) {
        return DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
    }

    public static FeedItem itemByTitle(Feed feed, String title) {
        for (FeedItem item : feed.getItems()) {
            if (title.equals(item.getTitle())) {
                DBReader.loadDescriptionOfFeedItem(item);
                return item;
            }
        }
        throw new AssertionError("No episode titled '" + title + "' in " + titles(feed.getItems()));
    }

    public static FeedItem itemByGuid(Feed feed, String guid) {
        for (FeedItem item : feed.getItems()) {
            if (guid.equals(item.getItemIdentifier())) {
                DBReader.loadDescriptionOfFeedItem(item);
                return item;
            }
        }
        throw new AssertionError("No episode with guid '" + guid + "' in " + titles(feed.getItems()));
    }

    private static String titles(List<FeedItem> items) {
        StringBuilder titles = new StringBuilder();
        for (FeedItem item : items) {
            titles.append('[').append(item.getTitle()).append(']');
        }
        return titles.toString();
    }
}
