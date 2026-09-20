package de.danoeh.antennapod.net.sync.nextcloud;

import android.app.Activity;
import android.content.Intent;
import de.danoeh.antennapod.net.sync.FakeHttpClient;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class NextcloudLoginFlowTest {
    private static final String START_RESPONSE = "{\"login\": \"https://cloud.example/login/flow/abc\","
            + "\"poll\": {\"token\": \"poll-token\", \"endpoint\": \"https://cloud.example/poll\"}}";
    private static final String POLL_RESPONSE = "{\"server\": \"https://cloud.example\","
            + "\"loginName\": \"alice\", \"appPassword\": \"app-secret\"}";

    private static class RecordingCallback implements NextcloudLoginFlow.AuthenticationCallback {
        final List<String> authenticated = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void onNextcloudAuthenticated(String server, String username, String password) {
            authenticated.add(server + "|" + username + "|" + password);
        }

        @Override
        public void onNextcloudAuthError(String errorMessage) {
            errors.add(errorMessage);
        }
    }

    private FakeHttpClient http;
    private RecordingCallback callback;
    private TestScheduler computation;
    private Activity activity;
    private NextcloudLoginFlow flow;

    @Before
    public void setUp() {
        RxJavaPlugins.setIoSchedulerHandler(scheduler -> Schedulers.trampoline());
        computation = new TestScheduler();
        RxJavaPlugins.setComputationSchedulerHandler(scheduler -> computation);
        http = new FakeHttpClient();
        callback = new RecordingCallback();
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        flow = new NextcloudLoginFlow(http.client(), "https://cloud.example", activity, callback);
    }

    @After
    public void tearDown() {
        RxJavaPlugins.reset();
    }

    private void deliverMainThreadWork() {
        shadowOf(activity.getMainLooper()).idle();
    }

    @Test
    public void startRequestsLoginFlowFromServerAndOpensLoginPageInBrowser() {
        http.enqueue(200, START_RESPONSE).enqueue(200, POLL_RESPONSE);

        flow.start();
        deliverMainThreadWork();

        assertEquals("POST", http.request(0).request.method());
        assertEquals("https://cloud.example/index.php/login/v2", http.request(0).request.url().toString());
        Intent browserIntent = shadowOf(activity).getNextStartedActivity();
        assertNotNull(browserIntent);
        assertEquals(Intent.ACTION_VIEW, browserIntent.getAction());
        assertEquals("https://cloud.example/login/flow/abc", browserIntent.getData().toString());
    }

    @Test
    public void startPollsWithTokenAndReportsCredentialsOnceAuthenticated() {
        http.enqueue(200, START_RESPONSE).enqueue(200, POLL_RESPONSE);

        flow.start();
        deliverMainThreadWork();

        assertEquals("https://cloud.example/poll", http.request(1).request.url().toString());
        assertEquals("token=poll-token", http.request(1).body);
        assertEquals(Arrays.asList("https://cloud.example|alice|app-secret"), callback.authenticated);
        assertTrue(callback.errors.isEmpty());
    }

    @Test
    public void pollingIsRetriedEverySecondUntilTheUserHasAuthenticated() {
        http.enqueue(200, START_RESPONSE).enqueue(404, "").enqueue(404, "").enqueue(200, POLL_RESPONSE);

        flow.start();
        deliverMainThreadWork();
        assertTrue(callback.authenticated.isEmpty());
        assertEquals(2, http.requests().size());

        computation.advanceTimeBy(1, TimeUnit.SECONDS);
        deliverMainThreadWork();
        assertTrue(callback.authenticated.isEmpty());
        assertEquals(3, http.requests().size());

        computation.advanceTimeBy(1, TimeUnit.SECONDS);
        deliverMainThreadWork();
        assertEquals(1, callback.authenticated.size());
        assertEquals(4, http.requests().size());
        assertTrue(callback.errors.isEmpty());
    }

    @Test
    public void pollingGivesUpAfterFiveMinutes() {
        http.enqueue(200, START_RESPONSE).replyToEverythingWith(404, "");

        flow.start();
        deliverMainThreadWork();
        computation.advanceTimeBy(5, TimeUnit.MINUTES);
        deliverMainThreadWork();

        assertTrue(callback.authenticated.isEmpty());
        assertEquals(1, callback.errors.size());
        assertNull(flow.saveInstanceState().get(1));
    }

    @Test
    public void startReportsServerErrorAndForgetsLoginState() {
        http.enqueue(500, "");

        flow.start();
        deliverMainThreadWork();

        assertEquals(Arrays.asList("Return code 500"), callback.errors);
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertEquals(Arrays.asList("https://cloud.example", null, null), flow.saveInstanceState());
    }

    @Test
    public void startReportsMalformedLoginFlowResponse() {
        http.enqueue(200, "{\"login\": \"https://cloud.example/login\"}");

        flow.start();
        deliverMainThreadWork();

        assertEquals(1, callback.errors.size());
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void savedStateContainsHostTokenAndPollEndpoint() {
        http.enqueue(200, START_RESPONSE).enqueue(404, "");

        flow.start();
        deliverMainThreadWork();

        assertEquals(Arrays.asList("https://cloud.example", "poll-token", "https://cloud.example/poll"),
                flow.saveInstanceState());
    }

    @Test
    public void restoredFlowResumesPollingWithoutRequestingANewLogin() {
        ArrayList<String> state = new ArrayList<>(
                Arrays.asList("https://cloud.example", "poll-token", "https://cloud.example/poll"));
        http.enqueue(200, POLL_RESPONSE);

        NextcloudLoginFlow restored = NextcloudLoginFlow.fromInstanceState(http.client(), activity, callback, state);
        restored.start();
        deliverMainThreadWork();

        assertEquals(1, http.requests().size());
        assertEquals("https://cloud.example/poll", http.request(0).request.url().toString());
        assertEquals("token=poll-token", http.request(0).body);
        assertEquals(1, callback.authenticated.size());
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void cancelDropsResultsThatHaveNotBeenDeliveredYet() {
        http.enqueue(200, START_RESPONSE);

        flow.start();
        flow.cancel();
        deliverMainThreadWork();

        assertNull(shadowOf(activity).getNextStartedActivity());
        assertTrue(callback.authenticated.isEmpty());
        assertTrue(callback.errors.isEmpty());
        assertEquals(1, http.requests().size());
    }

    @Test
    public void cancelStopsPolling() {
        http.enqueue(200, START_RESPONSE).replyToEverythingWith(404, "");
        flow.start();
        deliverMainThreadWork();
        int requestsBeforeCancel = http.requests().size();

        flow.cancel();
        computation.advanceTimeBy(30, TimeUnit.SECONDS);
        deliverMainThreadWork();

        assertEquals(requestsBeforeCancel, http.requests().size());
        assertTrue(callback.errors.isEmpty());
    }

    @Test
    public void cancelBeforeStartIsHarmless() {
        flow.cancel();

        assertEquals(0, http.requests().size());
    }
}
