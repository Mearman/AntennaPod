package de.test.antennapod.sync;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.sync.GpodderTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickPreference;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class GpodderLoginTest {
    private GpodderTestServer server;

    @Rule
    public ActivityTestRule<PreferenceActivity> activityTestRule =
            new ActivityTestRule<>(PreferenceActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.cancelPendingSyncWork();
        EspressoTestUtils.clearDatabase();
        EspressoTestUtils.clearPreferences();
        server = new GpodderTestServer();
        server.start();
        activityTestRule.launchActivity(new Intent());
    }

    @After
    public void tearDown() throws Exception {
        activityTestRule.finishActivity();
        server.stop();
    }

    private void openSynchronizationScreen() {
        clickPreference(R.string.synchronization_pref);
    }

    private void openGpodderLoginDialog() {
        onView(withText(R.string.synchronization_choose_title)).perform(click());
        onView(withText(R.string.gpodnet_description)).perform(click());
        onView(withId(R.id.serverUrlText)).check(matches(isDisplayed()));
    }

    private void enterHostAndProceed() {
        onView(withId(R.id.serverUrlText)).perform(replaceText(server.getBaseUrl()));
        onView(withId(R.id.chooseHostButton)).perform(click());
        onView(withId(R.id.etxtUsername)).check(matches(isDisplayed()));
    }

    private void enterCredentialsAndLogIn(String username, String password) {
        onView(withId(R.id.etxtUsername)).perform(replaceText(username));
        onView(withId(R.id.etxtPassword)).perform(replaceText(password));
        closeSoftKeyboard();
        onView(withId(R.id.butLogin)).perform(click());
    }

    @Test
    public void testLoginSelectsExistingDeviceAndStartsSync() {
        openSynchronizationScreen();
        openGpodderLoginDialog();
        enterHostAndProceed();
        enterCredentialsAndLogIn(GpodderTestServer.USERNAME, GpodderTestServer.PASSWORD);

        waitForViewGlobally(withText("Existing device"), 15000);
        assertTrue(server.hasRequest("POST", "/api/2/auth/" + GpodderTestServer.USERNAME + "/login.json"));
        assertTrue(server.hasRequest("GET", "/api/2/devices/" + GpodderTestServer.USERNAME + ".json"));

        onView(withText("Existing device")).perform(click());
        onView(withId(R.id.butSyncNow)).check(matches(isDisplayed()));
        onView(withId(R.id.butSyncNow)).perform(click());

        await().atMost(15, TimeUnit.SECONDS)
                .until(SynchronizationSettings::isProviderConnected);
        assertEquals("device1", SynchronizationCredentials.getDeviceId());
        assertEquals(GpodderTestServer.USERNAME, SynchronizationCredentials.getUsername());
        assertEquals(GpodderTestServer.PASSWORD, SynchronizationCredentials.getPassword());
        assertEquals(server.getBaseUrl(), SynchronizationCredentials.getHosturl());
        await().atMost(90, TimeUnit.SECONDS)
                .until(() -> server.hasRequest("GET", "/api/2/subscriptions/"));
    }

    @Test
    public void testCreateNewDeviceDuringLogin() {
        server.setDevicesJson("[]");
        openSynchronizationScreen();
        openGpodderLoginDialog();
        enterHostAndProceed();
        enterCredentialsAndLogIn(GpodderTestServer.USERNAME, GpodderTestServer.PASSWORD);

        waitForViewGlobally(withId(R.id.createDeviceButton), 15000);
        onView(withId(R.id.deviceName)).check(matches(withText(containsString("AntennaPod on"))));
        onView(withId(R.id.createDeviceButton)).perform(click());

        waitForViewGlobally(withId(R.id.butSyncNow), 15000);
        await().atMost(5, TimeUnit.SECONDS).until(() -> !server.configuredDevices.isEmpty());
        assertEquals("mobile", server.configuredDevices.get(0).optString("type"));
        assertTrue(server.configuredDevices.get(0).optString("caption").contains("AntennaPod on"));
        assertTrue(SynchronizationSettings.isProviderConnected());
    }

    @Test
    public void testWrongPasswordShowsErrorAndStaysOnLogin() {
        openSynchronizationScreen();
        openGpodderLoginDialog();
        enterHostAndProceed();
        enterCredentialsAndLogIn(GpodderTestServer.USERNAME, "wrong-password");

        waitForViewGlobally(withText("Wrong username or password"), 15000);
        onView(withId(R.id.etxtUsername)).check(matches(isDisplayed()));
        assertFalse(SynchronizationSettings.isProviderConnected());
        assertTrue(server.hasRequest("POST", "/login.json"));
    }

    @Test
    public void testUsernameWithSpecialCharactersIsRejectedLocally() {
        openSynchronizationScreen();
        openGpodderLoginDialog();
        enterHostAndProceed();
        enterCredentialsAndLogIn("us!er", GpodderTestServer.PASSWORD);

        onView(withText(R.string.gpodnetsync_username_characters_error))
                .check(matches(isDisplayed()));
        assertTrue(server.requests.isEmpty());
    }

    @Test
    public void testDeviceCreationErrorIsShown() {
        server.setDevicesJson("[]");
        server.setDeviceUploadStatus(500);
        openSynchronizationScreen();
        openGpodderLoginDialog();
        enterHostAndProceed();
        enterCredentialsAndLogIn(GpodderTestServer.USERNAME, GpodderTestServer.PASSWORD);

        waitForViewGlobally(withId(R.id.createDeviceButton), 15000);
        onView(withId(R.id.createDeviceButton)).perform(click());

        waitForViewGlobally(withText(containsString("unavailable")), 15000);
        assertFalse(SynchronizationSettings.isProviderConnected());
    }

    @Test
    public void testUnavailableServerShowsServerError() {
        server.setLoginStatus(500);
        openSynchronizationScreen();
        openGpodderLoginDialog();
        enterHostAndProceed();
        enterCredentialsAndLogIn(GpodderTestServer.USERNAME, GpodderTestServer.PASSWORD);

        waitForViewGlobally(withText(containsString("unavailable")), 15000);
        onView(withId(R.id.etxtUsername)).check(matches(isDisplayed()));
        assertFalse(SynchronizationSettings.isProviderConnected());
    }

    @Test
    public void testBrokenDeviceListShowsError() {
        server.setDevicesJson("this is not json");
        openSynchronizationScreen();
        openGpodderLoginDialog();
        enterHostAndProceed();
        enterCredentialsAndLogIn(GpodderTestServer.USERNAME, GpodderTestServer.PASSWORD);

        waitForViewGlobally(withId(R.id.credentialsError), 15000);
        onView(withId(R.id.etxtUsername)).check(matches(isDisplayed()));
        assertFalse(SynchronizationSettings.isProviderConnected());
        assertTrue(server.hasRequest("GET", "/api/2/devices/"));
    }

    @Test
    public void testLogoutClearsProviderAndShowsChooserAgain() {
        openSynchronizationScreen();
        openGpodderLoginDialog();
        enterHostAndProceed();
        enterCredentialsAndLogIn(GpodderTestServer.USERNAME, GpodderTestServer.PASSWORD);
        waitForViewGlobally(withText("Existing device"), 15000);
        onView(withText("Existing device")).perform(click());
        onView(withId(R.id.butSyncNow)).perform(click());
        await().atMost(15, TimeUnit.SECONDS).until(SynchronizationSettings::isProviderConnected);
        waitForViewGlobally(withText(R.string.synchronization_logout), 10000);

        clickPreference(R.string.synchronization_logout);
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> !SynchronizationSettings.isProviderConnected());
        assertNull(SynchronizationCredentials.getUsername());
        assertNull(SynchronizationCredentials.getPassword());
        assertNull(SynchronizationCredentials.getDeviceId());
        onView(withText(R.string.synchronization_choose_title)).check(matches(isDisplayed()));
    }
}
