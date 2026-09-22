package de.test.antennapod;

import android.content.Context;
import android.content.Intent;
import android.widget.EditText;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.util.HumanReadables;
import androidx.test.espresso.util.TreeIterables;
import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.storage.database.PodDBAdapter;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.drawer.NavDrawerFragment;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.hamcrest.Matcher;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;

public class EspressoTestUtils {
    public static void performWhenReady(@NonNull Matcher<View> viewMatcher, @NonNull ViewAction action,
                                        long timeoutMillis) {
        Awaitility.await().atMost(timeoutMillis, TimeUnit.MILLISECONDS).pollInSameThread().ignoreExceptions()
                .untilAsserted(() -> onView(viewMatcher).perform(action));
    }

    /**
     * Perform action of waiting for a specific view id.
     * https://stackoverflow.com/a/49814995/
     * @param viewMatcher The view to wait for.
     * @param millis The timeout of until when to wait for.
     */
    public static ViewAction waitForView(final Matcher<View> viewMatcher, final long millis) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isRoot();
            }

            @Override
            public String getDescription() {
                return "wait for a specific view for " + millis + " millis.";
            }

            @Override
            public void perform(final UiController uiController, final View view) {
                uiController.loopMainThreadUntilIdle();
                final long startTime = System.currentTimeMillis();
                final long endTime = startTime + millis;

                do {
                    for (View child : TreeIterables.breadthFirstViewTraversal(view)) {
                        // found view with required ID
                        if (viewMatcher.matches(child)) {
                            return;
                        }
                    }

                    uiController.loopMainThreadForAtLeast(50);
                } while (System.currentTimeMillis() < endTime);

                // timeout happens
                throw new PerformException.Builder()
                        .withActionDescription(this.getDescription())
                        .withViewDescription(HumanReadables.describe(view))
                        .withCause(new TimeoutException())
                        .build();
            }
        };
    }

    /**
     * Wait until a certain view becomes visible, but at the longest until the timeout.
     * Unlike {@link #waitForView(Matcher, long)} it doesn't stick to the initial root view.
     *
     * @param viewMatcher The view to wait for.
     * @param timeoutMillis Maximum waiting period in milliseconds.
     */
    public static void waitForViewGlobally(@NonNull Matcher<View> viewMatcher, long timeoutMillis) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMillis;

        do {
            try {
                onView(viewMatcher).check(matches(isDisplayed()));
                // no Exception thrown -> check successful
                return;
            } catch (RuntimeException exception) {
                // check was not successful "not found" -> continue waiting
                if (System.currentTimeMillis() >= endTime) {
                    throw exception;
                }
            }
            try {
                //noinspection BusyWait
                Thread.sleep(50);
            } catch (InterruptedException e) {
                break;
            }
        } while (true);

        throw new RuntimeException("Timeout after " + timeoutMillis + " ms");
    }

    /**
     * Perform action of waiting for a specific view id.
     * https://stackoverflow.com/a/30338665/
     * @param id The id of the child to click.
     */
    public static ViewAction clickChildViewWithId(final @IdRes int id) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return null;
            }

            @Override
            public String getDescription() {
                return "Click on a child view with specified id.";
            }

            @Override
            public void perform(UiController uiController, View view) {
                View v = view.findViewById(id);
                v.performClick();
            }
        };
    }

    public static ViewAction clickViewDirectly() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public String getDescription() {
                return "Click the view itself even if a dialog window covers part of it.";
            }

            @Override
            public void perform(UiController uiController, View view) {
                view.performClick();
                uiController.loopMainThreadUntilIdle();
            }
        };
    }

    public static ViewAction replaceTextDirectly(final String text) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isAssignableFrom(EditText.class);
            }

            @Override
            public String getDescription() {
                return "Replace the text of the view itself even if a dialog window covers part of it.";
            }

            @Override
            public void perform(UiController uiController, View view) {
                ((EditText) view).setText(text);
                uiController.loopMainThreadUntilIdle();
            }
        };
    }

    public static void waitForViewToDisappear(Matcher<? super View> matcher, long maxWaitingTimeMs) {
        long endTime = System.currentTimeMillis() + maxWaitingTimeMs;
        while (System.currentTimeMillis() <= endTime) {
            try {
                onView(allOf(matcher, isDisplayed())).check(matches(not(doesNotExist())));
                Thread.sleep(100);
            } catch (NoMatchingViewException ex) {
                return; // view has disappeared
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("timeout exceeded"); // or whatever exception you want
    }
    
    /**
     * Clear all app databases.
     */
    public static void clearPreferences() {
        File root = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir().getParentFile();
        String[] sharedPreferencesFileNames = new File(root, "shared_prefs").list();
        for (String fileName : sharedPreferencesFileNames) {
            System.out.println("Cleared database: " + fileName);
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences(
                    fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().commit();
        }

        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.PREF_IS_FIRST_LAUNCH, false)
                .commit();

        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .edit()
                .putString(UserPreferences.PREF_UPDATE_INTERVAL_MINUTES, "0")
                .commit();
    }

    public static void setLaunchScreen(String tag) {
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences(NavDrawerFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(NavDrawerFragment.PREF_LAST_FRAGMENT_TAG, tag)
                .commit();
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .edit()
                .putString(UserPreferences.PREF_DEFAULT_PAGE, UserPreferences.DEFAULT_PAGE_REMEMBER)
                .commit();
    }

    public static void clearDatabase() {
        PodDBAdapter.init(InstrumentationRegistry.getInstrumentation().getTargetContext());
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
    }

    public static void clickPreference(@StringRes int title) {
        onView(withId(R.id.recycler_view)).perform(
                RecyclerViewActions.actionOnItem(
                        allOf(hasDescendant(withText(title)),
                                hasDescendant(withId(android.R.id.widget_frame))),
                        click()));
    }

    public static ViewAction scrollRecyclerUntilItemMatches(final Matcher<View> itemViewMatcher) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isAssignableFrom(RecyclerView.class);
            }

            @Override
            public String getDescription() {
                return "scroll recycler view until an item matches " + itemViewMatcher;
            }

            @Override
            public void perform(UiController uiController, View rootView) {
                RecyclerView recyclerView = (RecyclerView) rootView;
                LinearLayoutManager layoutManager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                int itemCount = recyclerView.getAdapter().getItemCount();
                jumpToPosition(uiController, layoutManager, 0);
                int pageSize = Math.max(1,
                        layoutManager.findLastVisibleItemPosition() - layoutManager.findFirstVisibleItemPosition());
                for (int anchor = 0; anchor < itemCount; anchor += pageSize) {
                    for (int offset = 0; offset < pageSize && anchor + offset < itemCount; offset++) {
                        RecyclerView.ViewHolder holder =
                                recyclerView.findViewHolderForAdapterPosition(anchor + offset);
                        if (holder != null && itemViewMatcher.matches(holder.itemView)) {
                            jumpToPosition(uiController, layoutManager, anchor + offset);
                            return;
                        }
                    }
                    jumpToPosition(uiController, layoutManager, Math.min(anchor + pageSize, itemCount - 1));
                }
                throw new PerformException.Builder()
                        .withActionDescription(getDescription())
                        .withViewDescription(HumanReadables.describe(rootView))
                        .withCause(new RuntimeException("No item matches " + itemViewMatcher))
                        .build();
            }
        };
    }

    private static void jumpToPosition(UiController uiController, LinearLayoutManager layoutManager,
            int position) {
        layoutManager.scrollToPositionWithOffset(position, 0);
        uiController.loopMainThreadForAtLeast(100);
        uiController.loopMainThreadUntilIdle();
    }

    public static void clickBottomNavItem(@StringRes int text) {
        onView(allOf(withText(text),
                isDescendantOfA(withId(R.id.bottomNavigationView)), isDisplayed())).perform(click());
    }

    public static void clickBottomNavOverflow(@StringRes int text) {
        onView(allOf(withText(R.string.overflow_more),
                isDescendantOfA(withId(R.id.bottomNavigationView)), isDisplayed())).perform(click());
        onView(allOf(withText(text), isDisplayed())).perform(click());
    }

    public static void tryKillPlaybackService() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.stopService(new Intent(context, PlaybackService.class));
        try {
            // Android has no reliable way to stop a service instantly.
            // Calling stopSelf marks allows the system to destroy the service but the actual call
            // to onDestroy takes until the next GC of the system, which we can not influence.
            // Try to wait for the service at least a bit.
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> !PlaybackService.isRunning);
        } catch (ConditionTimeoutException e) {
            e.printStackTrace();
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    public static Matcher<View> actionBarOverflow() {
        return allOf(isDisplayed(), withContentDescription("More options"));
    }
}
