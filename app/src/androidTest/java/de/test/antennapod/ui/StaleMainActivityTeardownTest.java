package de.test.antennapod.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
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
        application.unregisterActivityLifecycleCallbacks(lifecycleLogger);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(this::finishEverythingLeftOpen);
        uiTestUtils.tearDown();
    }

    @Test
    public void relaunchWhilePreviousMainActivityIsFinishingResumesFreshInstance() throws Exception {
        firstInstance = activityRule.launchActivity(new Intent());
        log("first MainActivity instance " + identity(firstInstance));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(firstInstance::finish);
        log("previous MainActivity finished, destroy still pending, relaunching now");
        issueRelaunch();
        Activity resumed = waitForResumedMainActivity(25000);
        log("distinct MainActivity instances started: " + mainActivityInstances.size());
        log("resumed after relaunch: " + identity(resumed));
        assertNotNull("No MainActivity was resumed after the relaunch", resumed);
        assertNotSame("Relaunch resolved against the previous, still finishing MainActivity", firstInstance, resumed);
        assertTrue("The MainActivity resumed after the relaunch is still finishing", !resumed.isFinishing());
    }

    @Test
    public void markAsPlayedListWithPreviousMainActivityStillFinishing() throws Exception {
        uiTestUtils.addLocalFeedData(false);
        final Feed feed = uiTestUtils.hostedFeeds.get(0);
        firstInstance = activityRule.launchActivity(new Intent());
        log("first MainActivity instance " + identity(firstInstance));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(firstInstance::finish);
        log("previous MainActivity finished, destroy still pending, relaunching now");
        EspressoTestUtils.setLaunchScreen("" + feed.getId());
        issueRelaunch();
        onView(withText(feed.getItemAtIndex(0).getTitle())).perform(click());
        onView(isRoot()).perform(waitForView(withText(R.string.mark_read_no_media_label), 3000));
        onView(allOf(withText(R.string.mark_read_no_media_label), isDisplayed())).perform(click());
        EspressoTestUtils.waitForViewToDisappear(withText(R.string.mark_read_no_media_label), 3000);
    }

    private void issueRelaunch() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(intent);
        log("relaunch issued while previous finishing=" + firstInstance.isFinishing());
    }

    private Activity waitForResumedMainActivity(long timeoutMillis) throws InterruptedException {
        long endTime = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < endTime) {
            Activity resumed = resumedMainActivity;
            if (resumed != null) {
                return resumed;
            }
            Thread.sleep(50);
        }
        return null;
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

    private static String identity(Object object) {
        return Integer.toHexString(System.identityHashCode(object));
    }

    private static void log(String message) {
        Log.i(TAG, message);
    }
}
