package de.test.antennapod.sync;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.ui.UITestUtils;
import de.test.antennapod.util.sync.GpodderTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.awaitility.Awaitility.await;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SubscribeSyncTest {

    private GpodderTestServer server;
    private UITestUtils uiTestUtils;

    @Rule
    public ActivityTestRule<MainActivity> activityTestRule =
            new ActivityTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.cancelPendingSyncWork();
        EspressoTestUtils.clearDatabase();
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.enableSyncOverAnyConnection();
        server = new GpodderTestServer();
        server.start();
        uiTestUtils = new UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext());
        uiTestUtils.setup();
        uiTestUtils.addHostedFeedData();
    }

    @After
    public void tearDown() throws Exception {
        EspressoTestUtils.cancelPendingSyncWork();
        activityTestRule.finishActivity();
        SynchronizationSettings.setSelectedSyncProvider(null);
        SynchronizationCredentials.clear();
        uiTestUtils.tearDown();
        server.stop();
    }

    @Test
    public void testSubscribingThroughUiQueuesAndUploadsNewSubscription() throws Exception {
        SynchronizationSettings.resetTimestamps();
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(
                server.latestTimestamp.get());
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(
                server.latestTimestamp.get());
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProvider.GPODDER_NET.getIdentifier());
        SynchronizationCredentials.setHosturl(server.getBaseUrl());
        SynchronizationCredentials.setUsername(GpodderTestServer.USERNAME);
        SynchronizationCredentials.setPassword(GpodderTestServer.PASSWORD);
        SynchronizationCredentials.setDeviceId("device1");

        EspressoTestUtils.setLaunchScreen(AddFeedFragment.TAG);
        activityTestRule.launchActivity(new Intent());
        final String feedUrl = uiTestUtils.hostedFeeds.get(0).getDownloadUrl();
        onView(withId(R.id.addViaUrlButton)).perform(scrollTo(), click());
        onView(withId(R.id.textInput)).perform(replaceText(feedUrl));
        onView(withText(R.string.confirm_label)).perform(scrollTo(), click());

        closeSoftKeyboard();
        waitForViewGlobally(withText(R.string.subscribe_label), 20000);
        onView(withText(R.string.subscribe_label)).perform(click());

        await().atMost(150, TimeUnit.SECONDS)
                .until(() -> server.uploadedAddedFeeds.contains(feedUrl));
        await().atMost(60, TimeUnit.SECONDS)
                .until(SynchronizationSettings::isLastSyncSuccessful);
    }
}
