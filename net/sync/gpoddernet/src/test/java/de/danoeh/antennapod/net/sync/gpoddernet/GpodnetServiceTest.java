package de.danoeh.antennapod.net.sync.gpoddernet;

import de.danoeh.antennapod.net.sync.FakeHttpClient;
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice;
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetUploadChangesResponse;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeActionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.SubscriptionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException;
import de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse;
import okhttp3.Credentials;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class GpodnetServiceTest {
    private static final String LOGIN_URL = "https://gpodder.example/api/2/auth/user/login.json";
    private static final String UPLOAD_RESPONSE = "{\"timestamp\": 1234, \"update_urls\": []}";
    private static final String EMPTY_CHANGES = "{\"add\": [], \"remove\": [], \"timestamp\": 5}";

    private FakeHttpClient http;
    private GpodnetService service;

    @Before
    public void setUp() {
        http = new FakeHttpClient();
        service = new GpodnetService(http.client(), "gpodder.example", "device", "user", "secret");
    }

    private void login() throws GpodnetServiceException {
        http.enqueue(200, "");
        service.login();
    }

    private static List<EpisodeAction> playActions(int count) {
        List<EpisodeAction> actions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            actions.add(new EpisodeAction.Builder("podcast", "episode" + i, EpisodeAction.PLAY)
                    .timestamp(new Date(1609488000000L))
                    .started(1)
                    .position(2)
                    .total(3)
                    .build());
        }
        return actions;
    }

    @Test
    public void loginPostsWithBasicAuthenticationToTheUsersLoginEndpoint() throws Exception {
        login();

        assertEquals(1, http.requests().size());
        assertEquals("POST", http.request(0).request.method());
        assertEquals(LOGIN_URL, http.request(0).request.url().toString());
        assertEquals(Credentials.basic("user", "secret"), http.request(0).request.header("Authorization"));
    }

    @Test
    public void loginRejectsWrongCredentials() {
        http.enqueue(401, "");

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class, () -> service.login());

        assertTrue(exception.getMessage().contains("Wrong username or password"));
    }

    @Test
    public void loginReportsServerErrorsAsUnavailable() {
        http.enqueue(503, "");

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class, () -> service.login());

        assertTrue(exception.getMessage().contains("gpodder.example is currently unavailable"));
        assertTrue(exception.getMessage().contains("503"));
    }

    @Test
    public void loginReportsClientErrorsWithStatusCodeAndMessage() {
        http.enqueue(404, "Not Found Here", "");

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class, () -> service.login());

        assertTrue(exception.getMessage().contains("Unable to connect to gpodder.example"));
        assertTrue(exception.getMessage().contains("404: Not Found Here"));
    }

    @Test
    public void loginWrapsNetworkFailures() {
        IOException failure = new IOException("offline");
        http.enqueueFailure(failure);

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class, () -> service.login());

        assertEquals(failure, exception.getCause());
    }

    @Test
    public void failedLoginDoesNotAuthenticateFurtherRequests() {
        http.enqueue(401, "");
        assertThrows(GpodnetServiceException.class, () -> service.login());

        assertThrows(IllegalStateException.class, () -> service.getDevices());
        assertEquals(1, http.requests().size());
    }

    @Test
    public void requestsRequireLogin() {
        assertThrows(IllegalStateException.class, () -> service.getDevices());
        assertThrows(IllegalStateException.class, () -> service.configureDevice("device", "caption", null));
        assertThrows(IllegalStateException.class,
                () -> service.uploadSubscriptionChanges(Collections.emptyList(), Collections.emptyList()));
        assertThrows(IllegalStateException.class, () -> service.getSubscriptionChanges(0));
        assertThrows(IllegalStateException.class, () -> service.uploadEpisodeActions(playActions(1)));
        assertThrows(IllegalStateException.class, () -> service.getEpisodeActionChanges(0));
        assertEquals(0, http.requests().size());
    }

    @Test
    public void getDevicesParsesTheDeviceList() throws Exception {
        login();
        http.enqueue(200, "[{\"id\": \"d1\", \"caption\": \"Phone\", \"type\": \"mobile\", \"subscriptions\": 12},"
                + "{\"id\": \"d2\", \"caption\": \"Box\", \"type\": \"unknown\", \"subscriptions\": 0}]");

        List<GpodnetDevice> devices = service.getDevices();

        assertEquals("https://gpodder.example/api/2/devices/user.json", http.request(1).request.url().toString());
        assertEquals(2, devices.size());
        assertEquals("d1", devices.get(0).getId());
        assertEquals("Phone", devices.get(0).getCaption());
        assertEquals(GpodnetDevice.DeviceType.MOBILE, devices.get(0).getType());
        assertEquals(12, devices.get(0).getSubscriptions());
        assertEquals(GpodnetDevice.DeviceType.OTHER, devices.get(1).getType());
    }

    @Test
    public void getDevicesRejectsMalformedResponse() throws Exception {
        login();
        http.enqueue(200, "not json");

        assertThrows(GpodnetServiceException.class, () -> service.getDevices());
    }

    @Test
    public void getDevicesRejectsDevicesWithoutMandatoryFields() throws Exception {
        login();
        http.enqueue(200, "[{\"id\": \"d1\"}]");

        assertThrows(GpodnetServiceException.class, () -> service.getDevices());
    }

    @Test
    public void configureDevicePostsCaptionAndLowercaseType() throws Exception {
        login();
        http.enqueue(200, "");

        service.configureDevice("device-7", "My phone", GpodnetDevice.DeviceType.MOBILE);

        assertEquals("https://gpodder.example/api/2/devices/user/device-7.json",
                http.request(1).request.url().toString());
        assertEquals("POST", http.request(1).request.method());
        JSONObject body = new JSONObject(http.request(1).body);
        assertEquals("My phone", body.getString("caption"));
        assertEquals("mobile", body.getString("type"));
    }

    @Test
    public void configureDeviceSendsEmptyBodyWithoutCaptionAndType() throws Exception {
        login();
        http.enqueue(200, "");

        service.configureDevice("device-7", null, null);

        assertEquals("", http.request(1).body);
    }

    @Test
    public void configureDeviceOmitsMissingCaption() throws Exception {
        login();
        http.enqueue(200, "");

        service.configureDevice("device-7", null, GpodnetDevice.DeviceType.SERVER);

        JSONObject body = new JSONObject(http.request(1).body);
        assertFalse(body.has("caption"));
        assertEquals("server", body.getString("type"));
    }

    @Test
    public void uploadSubscriptionChangesPostsAddedAndRemovedFeeds() throws Exception {
        login();
        http.enqueue(200, "{\"timestamp\": 1234, \"update_urls\": [[\"http://old.example\", \"http://new.example\"]]}");

        UploadChangesResponse response = service.uploadSubscriptionChanges(
                Arrays.asList("http://a.example", "http://b.example"), Collections.singletonList("http://c.example"));

        assertEquals("https://gpodder.example/api/2/subscriptions/user/device.json",
                http.request(1).request.url().toString());
        JSONObject body = new JSONObject(http.request(1).body);
        assertEquals("http://a.example", body.getJSONArray("add").getString(0));
        assertEquals("http://b.example", body.getJSONArray("add").getString(1));
        assertEquals("http://c.example", body.getJSONArray("remove").getString(0));
        assertEquals(1234, response.timestamp);
        assertEquals("http://new.example", ((GpodnetUploadChangesResponse) response).updatedUrls
                .get("http://old.example"));
    }

    @Test
    public void uploadSubscriptionChangesRejectsResponseWithoutTimestamp() throws Exception {
        login();
        http.enqueue(200, "{}");

        assertThrows(GpodnetServiceException.class,
                () -> service.uploadSubscriptionChanges(Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    public void getSubscriptionChangesRequestsChangesSinceTimestamp() throws Exception {
        login();
        http.enqueue(200, "{\"add\": [\"http://a.example/feed\"], \"remove\": [\"http://b.example/feed\"],"
                + "\"timestamp\": 777}");

        SubscriptionChanges changes = service.getSubscriptionChanges(555);

        assertEquals("https://gpodder.example/api/2/subscriptions/user/device.json?since=555",
                http.request(1).request.url().toString());
        assertEquals("GET", http.request(1).request.method());
        assertEquals(Collections.singletonList("http://a.example/feed"), changes.getAdded());
        assertEquals(Collections.singletonList("http://b.example/feed"), changes.getRemoved());
        assertEquals(777, changes.getTimestamp());
    }

    @Test
    public void getSubscriptionChangesRejectsMalformedResponse() throws Exception {
        login();
        http.enqueue(200, "[]");

        assertThrows(GpodnetServiceException.class, () -> service.getSubscriptionChanges(0));
    }

    @Test
    public void getSubscriptionChangesReportsServerFailure() throws Exception {
        login();
        http.enqueue(500, "");

        assertThrows(GpodnetServiceBadStatusCodeException.class, () -> service.getSubscriptionChanges(0));
    }

    @Test
    public void uploadEpisodeActionsPostsActionsWithDeviceId() throws Exception {
        login();
        http.enqueue(200, UPLOAD_RESPONSE);

        UploadChangesResponse response = service.uploadEpisodeActions(playActions(2));

        assertEquals("https://gpodder.example/api/2/episodes/user.json", http.request(1).request.url().toString());
        JSONArray body = new JSONArray(http.request(1).body);
        assertEquals(2, body.length());
        assertEquals("device", body.getJSONObject(0).getString("device"));
        assertEquals("episode0", body.getJSONObject(0).getString("episode"));
        assertEquals("play", body.getJSONObject(0).getString("action"));
        assertEquals("episode1", body.getJSONObject(1).getString("episode"));
        assertEquals(1234, response.timestamp);
    }

    @Test
    public void uploadEpisodeActionsSplitsLargeUploadsIntoBatches() throws Exception {
        login();
        http.enqueue(200, "{\"timestamp\": 1, \"update_urls\": []}");
        http.enqueue(200, "{\"timestamp\": 2, \"update_urls\": []}");
        http.enqueue(200, "{\"timestamp\": 3, \"update_urls\": []}");

        UploadChangesResponse response = service.uploadEpisodeActions(playActions(65));

        assertEquals(4, http.requests().size());
        assertEquals(30, new JSONArray(http.request(1).body).length());
        assertEquals(30, new JSONArray(http.request(2).body).length());
        assertEquals(5, new JSONArray(http.request(3).body).length());
        assertEquals("episode30", new JSONArray(http.request(2).body).getJSONObject(0).getString("episode"));
        assertEquals("episode60", new JSONArray(http.request(3).body).getJSONObject(0).getString("episode"));
        assertEquals(3, response.timestamp);
    }

    @Test
    public void uploadEpisodeActionsFitsExactlyOneBatchInOneRequest() throws Exception {
        login();
        http.enqueue(200, UPLOAD_RESPONSE);

        service.uploadEpisodeActions(playActions(30));

        assertEquals(2, http.requests().size());
        assertEquals(30, new JSONArray(http.request(1).body).length());
    }

    @Test
    public void uploadEpisodeActionsStopsAtFirstFailingBatch() throws Exception {
        login();
        http.enqueue(200, UPLOAD_RESPONSE);
        http.enqueue(500, "");

        assertThrows(GpodnetServiceBadStatusCodeException.class, () -> service.uploadEpisodeActions(playActions(65)));

        assertEquals(3, http.requests().size());
    }

    @Test
    public void getEpisodeActionChangesParsesActionsAndSkipsInvalidOnes() throws Exception {
        login();
        http.enqueue(200, "{\"timestamp\": 999, \"actions\": ["
                + "{\"podcast\": \"p1\", \"episode\": \"e1\", \"action\": \"play\","
                + "\"timestamp\": \"2021-01-01T08:00:00\", \"started\": 1, \"position\": 2, \"total\": 3},"
                + "{\"podcast\": \"p2\", \"episode\": \"e2\", \"action\": \"unknown\"},"
                + "{\"podcast\": \"p3\", \"episode\": \"e3\", \"action\": \"delete\"}]}");

        EpisodeActionChanges changes = service.getEpisodeActionChanges(321);

        assertEquals("https://gpodder.example/api/2/episodes/user.json?since=321",
                http.request(1).request.url().toString());
        assertEquals(999, changes.getTimestamp());
        assertEquals(2, changes.getEpisodeActions().size());
        assertEquals("e1", changes.getEpisodeActions().get(0).getEpisode());
        assertEquals(2, changes.getEpisodeActions().get(0).getPosition());
        assertEquals("e3", changes.getEpisodeActions().get(1).getEpisode());
    }

    @Test
    public void getEpisodeActionChangesRejectsMalformedResponse() throws Exception {
        login();
        http.enqueue(200, "{\"timestamp\": 1}");

        assertThrows(SyncServiceException.class, () -> service.getEpisodeActionChanges(0));
    }

    @Test
    public void hostUrlWithSchemePortAndSubfolderIsUsedForEveryRequest() throws Exception {
        service = new GpodnetService(http.client(), "http://server.example:8080/gpodder/", "device", "user", "secret");
        login();
        http.enqueue(200, EMPTY_CHANGES);

        service.getSubscriptionChanges(0);

        assertEquals("http://server.example:8080/gpodder/api/2/auth/user/login.json",
                http.request(0).request.url().toString());
        assertEquals("http://server.example:8080/gpodder/api/2/subscriptions/user/device.json?since=0",
                http.request(1).request.url().toString());
    }

    @Test
    public void missingHostFallsBackToGpodderNet() throws Exception {
        service = new GpodnetService(http.client(), null, "device", "user", "secret");

        login();

        assertEquals("https://gpodder.net/api/2/auth/user/login.json", http.request(0).request.url().toString());
    }

    @Test
    public void setCredentialsChangesTheAuthenticationOfLaterLogins() throws Exception {
        service.setCredentials("other", "different");

        login();

        assertEquals("https://gpodder.example/api/2/auth/other/login.json",
                http.request(0).request.url().toString());
        assertEquals(Credentials.basic("other", "different"), http.request(0).request.header("Authorization"));
    }

    private void assertFailsWithNetworkError(ThrowingCall call) throws Exception {
        IOException failure = new IOException("offline");
        login();
        http.enqueueFailure(failure);

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class, call::run);

        assertEquals(failure, exception.getCause());
    }

    private interface ThrowingCall {
        void run() throws Exception;
    }

    @Test
    public void getDevicesWrapsNetworkFailures() throws Exception {
        assertFailsWithNetworkError(() -> service.getDevices());
    }

    @Test
    public void configureDeviceWrapsNetworkFailures() throws Exception {
        assertFailsWithNetworkError(() -> service.configureDevice("device", "caption", null));
    }

    @Test
    public void uploadSubscriptionChangesWrapsNetworkFailures() throws Exception {
        assertFailsWithNetworkError(
                () -> service.uploadSubscriptionChanges(Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    public void getSubscriptionChangesWrapsNetworkFailures() throws Exception {
        assertFailsWithNetworkError(() -> service.getSubscriptionChanges(0));
    }

    @Test
    public void uploadEpisodeActionsWrapsNetworkFailures() throws Exception {
        assertFailsWithNetworkError(() -> service.uploadEpisodeActions(playActions(1)));
    }

    @Test
    public void getEpisodeActionChangesWrapsNetworkFailures() throws Exception {
        assertFailsWithNetworkError(() -> service.getEpisodeActionChanges(0));
    }

    @Test
    public void responseBodyThatCannotBeReadIsReportedAsServiceFailure() throws Exception {
        login();
        http.enqueueUnreadableBody(200);

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class, () -> service.getDevices());

        assertEquals("unreadable body", exception.getCause().getMessage());
    }

    @Test
    public void hostThatCannotFormAnUrlFailsLogin() {
        service = new GpodnetService(http.client(), "bad host", "device", "user", "secret");

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class, () -> service.login());

        assertTrue(exception.getCause() instanceof URISyntaxException);
        assertEquals(0, http.requests().size());
    }
}
