package de.test.antennapod.ui;

import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.BoundedMatcher;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateManagerImpl;
import de.danoeh.antennapod.storage.database.DBReader;
import de.test.antennapod.EspressoTestUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.ThrowingRunnable;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickBottomNavItem;
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
        Espresso.closeSoftKeyboard();
        onView(withText(R.string.confirm_label)).perform(scrollTo(), click());
    }

    public static void subscribeToPreviewedFeed() {
        waitUntilDisplayed(withText(R.string.subscribe_label), UI_TIMEOUT_MS);
        onView(withText(R.string.subscribe_label)).perform(click());
        waitUntilDisplayed(withId(R.id.butShowSettings), UI_TIMEOUT_MS);
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
        waitUntilDisplayed(allOf(withId(R.id.txtvTitle), withText(title)), UI_TIMEOUT_MS);
        onView(allOf(withId(R.id.txtvAuthor), withText(author))).check(matches(isDisplayed()));
    }

    public static void openFeedSettings() {
        waitUntilDisplayed(withId(R.id.butShowSettings), UI_TIMEOUT_MS);
        onView(withId(R.id.butShowSettings)).perform(click());
        waitUntilDisplayed(withText(R.string.keep_updated), UI_TIMEOUT_MS);
    }

    public static void clickSetting(int titleRes) {
        onView(withId(R.id.recycler_view)).perform(RecyclerViewActions.actionOnItem(
                hasDescendant(withText(titleRes)), click()));
    }

    public static void awaitDialogText(int textRes) {
        Awaitility.await().atMost(UI_TIMEOUT_MS, TimeUnit.MILLISECONDS).pollInSameThread().ignoreExceptions()
                .until(() -> {
                    onView(withText(textRes)).inRoot(isDialog()).check(matches(isDisplayed()));
                    return true;
                });
    }

    public static void chooseOption(int textRes) {
        awaitDialogText(textRes);
        onView(withText(textRes)).inRoot(isDialog()).perform(click());
    }

    public static void confirmDialog(int buttonTextRes) {
        awaitAssertion(() -> onView(withText(buttonTextRes)).inRoot(isDialog()).perform(click()));
    }

    public static void confirmTypedDialog(int buttonTextRes) {
        Espresso.closeSoftKeyboard();
        confirmDialog(buttonTextRes);
    }

    public static void waitUntilDisplayed(Matcher<View> viewMatcher, long timeoutMillis) {
        Awaitility.await().atMost(timeoutMillis, TimeUnit.MILLISECONDS).pollInSameThread().ignoreExceptions()
                .untilAsserted(() -> onView(viewMatcher).check(matches(isDisplayed())));
    }

    public static void awaitAssertion(ThrowingRunnable assertion) {
        Awaitility.await().atMost(UI_TIMEOUT_MS, TimeUnit.MILLISECONDS).pollInSameThread().ignoreExceptions()
                .untilAsserted(assertion);
    }

    public static Matcher<View> itemAtPosition(int position, Matcher<View> itemMatcher) {
        return new BoundedMatcher<View, RecyclerView>(RecyclerView.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("item at position " + position + " matching ");
                itemMatcher.describeTo(description);
            }

            @Override
            protected boolean matchesSafely(RecyclerView recyclerView) {
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
                return holder != null && itemMatcher.matches(holder.itemView);
            }
        };
    }

    public static Matcher<View> hasItemCount(int count) {
        return new BoundedMatcher<View, RecyclerView>(RecyclerView.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("adapter with " + count + " items");
            }

            @Override
            protected boolean matchesSafely(RecyclerView recyclerView) {
                return recyclerView.getAdapter() != null && recyclerView.getAdapter().getItemCount() == count;
            }
        };
    }

    public static void assertListedTitles(String... titles) {
        awaitAssertion(() -> {
            onView(withId(R.id.recyclerView)).check(matches(hasItemCount(titles.length)));
            for (int i = 0; i < titles.length; i++) {
                onView(withId(R.id.recyclerView)).check(matches(
                        itemAtPosition(i, hasDescendant(withText(titles[i])))));
            }
        });
    }

    public static void awaitCondition(Callable<Boolean> condition) {
        Awaitility.await().atMost(DB_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(condition);
    }

    public static void openFeedMenu(int titleRes) {
        awaitAssertion(() -> {
            onView(first(EspressoTestUtils.actionBarOverflow())).perform(click());
            try {
                onView(withText(titleRes)).perform(click());
            } catch (NoMatchingViewException e) {
                if (!isOverflowReachable()) {
                    Espresso.pressBack();
                }
                throw e;
            }
        });
    }

    private static boolean isOverflowReachable() {
        try {
            onView(first(EspressoTestUtils.actionBarOverflow())).check(matches(isDisplayed()));
            return true;
        } catch (NoMatchingViewException e) {
            return false;
        }
    }

    public static void refreshFromMenu() {
        FeedUpdateManagerImpl.resetManualRefreshCooldown();
        openFeedMenu(R.string.refresh_label);
    }

    public static void refreshAllFromSubscriptions() {
        FeedUpdateManagerImpl.resetManualRefreshCooldown();
        clickBottomNavItem(R.string.subscriptions_label_short);
        openFeedMenu(R.string.refresh_label);
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
