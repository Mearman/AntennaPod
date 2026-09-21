package de.test.antennapod.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;

import de.test.antennapod.EspressoTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.waitForView;
import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class StaleMainActivityTeardownTest {

    private static final String TAG = "StaleTeardownRepro";

    private final Set<Activity> mainActivityInstances = Collections.newSetFromMap(new IdentityHashMap<>());

    private Application application;
    private MainActivity firstInstance;
    private UITestUtils uiTestUtils;
    private volatile Activity resumedMainActivity;
    private CountDownLatch teardownHeld;
    private CountDownLatch teardownRelease;

    private final Application.ActivityLifecycleCallbacks lifecycleLogger = new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
            if (activity instanceof MainActivity) {
                mainActivityInstances.add(activity);
                log("started " + identity(activity) + " finishing=" + activity.isFinishing());
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (activity instanceof MainActivity) {
                resumedMainActivity = activity;
                log("resumed " + identity(activity) + " finishing=" + activity.isFinishing());
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (activity instanceof MainActivity) {
                if (activity == resumedMainActivity) {
                    resumedMainActivity = null;
                }
                log("paused " + identity(activity) + " finishing=" + activity.isFinishing());
            }
        }

        @Override
        public void onActivityStopped(Activity activity) {
            if (activity instanceof MainActivity) {
                log("stopped " + identity(activity) + " finishing=" + activity.isFinishing());
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (activity instanceof MainActivity) {
                log("destroyed " + identity(activity));
            }
        }
    };

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        application = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getApplicationContext();
        application.registerActivityLifecycleCallbacks(lifecycleLogger);
        uiTestUtils = new UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext());
        uiTestUtils.setHostTextOnlyFeeds(true);
        uiTestUtils.setup();
    }

    @After
    public void tearDown() throws Exception {
        if (teardownRelease != null) {
            teardownRelease.countDown();
        }
        application.unregisterActivityLifecycleCallbacks(lifecycleLogger);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(this::finishEverythingLeftOpen);
        uiTestUtils.tearDown();
    }

    @Test
    public void relaunchWhilePreviousMainActivityIsFinishingCreatesFreshInstance() throws Exception {
        firstInstance = activityRule.launchActivity(new Intent());
        log("first MainActivity instance " + identity(firstInstance));
        holdTeardown();
        log("ready to relaunch");
        Thread.sleep(2000);
        log("distinct MainActivity instances started: " + mainActivityInstances.size());
        log("resumed MainActivity: " + identity(resumedMainActivity));
        assertTrue("A relaunch issued while the previous MainActivity was still finishing created no fresh"
                + " instance; the previous, still finishing activity remained the resumed one",
                mainActivityInstances.size() > 1);
        Activity resumed = resumedMainActivity;
        assertNotNull("No MainActivity was resumed after the relaunch", resumed);
        assertNotSame("The resumed MainActivity is the previous, still finishing instance", firstInstance, resumed);
    }

    @Test
    public void markAsPlayedListWithPreviousMainActivityStillFinishing() throws Exception {
        uiTestUtils.addLocalFeedData(false);
        final Feed feed = uiTestUtils.hostedFeeds.get(0);
        firstInstance = activityRule.launchActivity(new Intent());
        log("first MainActivity instance " + identity(firstInstance));
        EspressoTestUtils.setLaunchScreen("" + feed.getId());
        holdTeardown();
        log("ready to relaunch");
        Thread.sleep(2000);
        teardownRelease.countDown();
        onView(withText(feed.getItemAtIndex(0).getTitle())).perform(click());
        onView(isRoot()).perform(waitForView(withText(R.string.mark_read_no_media_label), 3000));
        onView(allOf(withText(R.string.mark_read_no_media_label), isDisplayed())).perform(click());
        EspressoTestUtils.waitForViewToDisappear(withText(R.string.mark_read_no_media_label), 3000);
    }

    private void holdTeardown() throws InterruptedException {
        teardownHeld = new CountDownLatch(1);
        teardownRelease = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            firstInstance.finish();
            teardownHeld.countDown();
            try {
                teardownRelease.await(destroyHoldMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue("Previous MainActivity did not start finishing", teardownHeld.await(15, TimeUnit.SECONDS));
        log("previous MainActivity finishing with teardown held, still resumed="
                + (resumedMainActivity == firstInstance));
    }

    private void finishEverythingLeftOpen() {
        ArrayList<Activity> openActivities = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            openActivities.addAll(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage));
        }
        for (Activity activity : openActivities) {
            activity.finish();
        }
    }

    private static long destroyHoldMillis() {
        return Long.parseLong(InstrumentationRegistry.getArguments().getString("destroyHoldMillis", "3000"));
    }

    private static String identity(Object object) {
        return Integer.toHexString(System.identityHashCode(object));
    }

    private static void log(String message) {
        Log.i(TAG, message);
    }
}
