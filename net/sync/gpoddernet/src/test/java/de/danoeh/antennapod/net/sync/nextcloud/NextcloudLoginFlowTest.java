package de.danoeh.antennapod.net.sync.nextcloud;

import android.app.Activity;
import android.content.Intent;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class NextcloudLoginFlowTest {
    private MockWebServer server;
    private Activity context;
    private RecordingCallback callback;

    @Before
    public void setUp() throws IOException {
        RxJavaPlugins.setIoSchedulerHandler(scheduler -> Schedulers.trampoline());
        RxAndroidPlugins.setMainThreadSchedulerHandler(scheduler -> Schedulers.trampoline());
        server = new MockWebServer();
        server.start();
        context = Robolectric.buildActivity(Activity.class).create().get();
        callback = new RecordingCallback();
    }

    @After
    public void tearDown() throws IOException {
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
        server.shutdown();
    }

    private String hostUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort();
    }

    private String loginStartResponse() {
        return "{\"login\":\"" + server.url("/login/flow") + "\",\"poll\":{\"token\":\"poll-token\","
                + "\"endpoint\":\"" + server.url("/poll") + "\"}}";
    }

    private static String pollResponse(String loginName, String appPassword) {
        return "{\"server\":\"https://cloud.example\",\"loginName\":\"" + loginName
                + "\",\"appPassword\":\"" + appPassword + "\"}";
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    @Test
    public void startOpensBrowserAtLoginUrlAndReportsCredentialsOnceApproved() throws Exception {
        server.enqueue(new MockResponse().setBody(loginStartResponse()));
        server.enqueue(new MockResponse().setBody(pollResponse("alice", "app-pw")));
        NextcloudLoginFlow flow = new NextcloudLoginFlow(new OkHttpClient(), hostUrl(), context, callback);

        flow.start();

        RecordedRequest loginRequest = takeRequest();
        assertEquals("POST", loginRequest.getMethod());
        assertEquals("/index.php/login/v2", loginRequest.getPath());
        RecordedRequest pollRequest = takeRequest();
        assertEquals("/poll", pollRequest.getPath());
        assertEquals("token=poll-token", pollRequest.getBody().readUtf8());
        Intent browserIntent = Shadows.shadowOf(context).getNextStartedActivity();
        assertNotNull(browserIntent);
        assertEquals(Intent.ACTION_VIEW, browserIntent.getAction());
        assertEquals(server.url("/login/flow").toString(), browserIntent.getData().toString());
        assertEquals(Collections.singletonList("https://cloud.example alice app-pw"), callback.authenticated);
        assertEquals(0, callback.errors.size());
    }

    @Test
    public void startWithRejectedLoginRequestReportsErrorAndClearsState() {
        server.enqueue(new MockResponse().setResponseCode(500));
        NextcloudLoginFlow flow = new NextcloudLoginFlow(new OkHttpClient(), hostUrl(), context, callback);

        flow.start();

        assertEquals(Collections.singletonList("Return code 500"), callback.errors);
        assertEquals(0, callback.authenticated.size());
        ArrayList<String> state = flow.saveInstanceState();
        assertNull(state.get(1));
        assertNull(state.get(2));
        assertNull(Shadows.shadowOf(context).getNextStartedActivity());
    }

    @Test
    public void startWithIncompleteLoginResponseReportsErrorAndDoesNotOpenBrowser() {
        server.enqueue(new MockResponse().setBody("{\"login\":\"http://example.com/login\"}"));
        NextcloudLoginFlow flow = new NextcloudLoginFlow(new OkHttpClient(), hostUrl(), context, callback);

        flow.start();

        assertEquals(1, callback.errors.size());
        assertEquals(0, callback.authenticated.size());
        assertNull(Shadows.shadowOf(context).getNextStartedActivity());
    }

    @Test
    public void saveInstanceStateContainsHostTokenAndEndpoint() {
        server.enqueue(new MockResponse().setBody(loginStartResponse()));
        server.enqueue(new MockResponse().setBody(pollResponse("alice", "app-pw")));
        NextcloudLoginFlow flow = new NextcloudLoginFlow(new OkHttpClient(), hostUrl(), context, callback);
        flow.start();

        ArrayList<String> state = flow.saveInstanceState();

        assertEquals(Arrays.asList(hostUrl(), "poll-token", server.url("/poll").toString()), state);
    }

    @Test
    public void restoredFlowResumesPollingWithoutRequestingANewLoginSession() throws Exception {
        server.enqueue(new MockResponse().setBody(pollResponse("bob", "other-pw")));
        ArrayList<String> savedState = new ArrayList<>(Arrays.asList(
                hostUrl(), "saved-token", server.url("/poll").toString()));
        NextcloudLoginFlow flow = NextcloudLoginFlow.fromInstanceState(
                new OkHttpClient(), context, callback, savedState);

        flow.start();

        assertEquals(1, server.getRequestCount());
        RecordedRequest pollRequest = takeRequest();
        assertEquals("/poll", pollRequest.getPath());
        assertEquals("token=saved-token", pollRequest.getBody().readUtf8());
        assertEquals(Collections.singletonList("https://cloud.example bob other-pw"), callback.authenticated);
        assertNull(Shadows.shadowOf(context).getNextStartedActivity());
    }

    @Test
    public void cancelBeforeStartLeavesFlowUsable() {
        server.enqueue(new MockResponse().setBody(loginStartResponse()));
        server.enqueue(new MockResponse().setBody(pollResponse("alice", "app-pw")));
        NextcloudLoginFlow flow = new NextcloudLoginFlow(new OkHttpClient(), hostUrl(), context, callback);

        flow.cancel();
        flow.start();

        assertEquals(1, callback.authenticated.size());
    }

    private static class RecordingCallback implements NextcloudLoginFlow.AuthenticationCallback {
        private final ArrayList<String> authenticated = new ArrayList<>();
        private final ArrayList<String> errors = new ArrayList<>();

        @Override
        public void onNextcloudAuthenticated(String server, String username, String password) {
            authenticated.add(server + " " + username + " " + password);
        }

        @Override
        public void onNextcloudAuthError(String errorMessage) {
            errors.add(errorMessage);
        }
    }
}
