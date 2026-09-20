package de.danoeh.antennapod.net.sync.gpoddernet;

import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice;
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetUploadChangesResponse;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeActionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.SubscriptionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException;
import de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class GpodnetServiceSyncTest {
    private static final String FEED_URL = "https://example.com/feed.xml";
    private static final String EPISODE_URL = "https://example.com/episode1.mp3";
    private MockWebServer server;
    private GpodnetService service;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        service = new GpodnetService(new OkHttpClient(),
                "http://" + server.getHostName() + ":" + server.getPort(), "device1", "alice", "secret");
        server.enqueue(new MockResponse().setResponseCode(200));
        service.login();
        takeRequest();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    private static EpisodeAction playAction(int number) {
        return new EpisodeAction.Builder(FEED_URL, "https://example.com/episode" + number + ".mp3",
                EpisodeAction.PLAY)
                .timestamp(new Date(1_700_000_000_000L))
                .started(0)
                .position(10)
                .total(100)
                .build();
    }

    @Test
    public void getDevicesParsesDeviceList() throws Exception {
        server.enqueue(new MockResponse().setBody("["
                + "{\"id\":\"phone\",\"caption\":\"My phone\",\"type\":\"mobile\",\"subscriptions\":4},"
                + "{\"id\":\"box\",\"caption\":\"Home server\",\"type\":\"unknown-type\",\"subscriptions\":0}]"));

        List<GpodnetDevice> devices = service.getDevices();

        assertEquals(2, devices.size());
        assertEquals("phone", devices.get(0).getId());
        assertEquals("My phone", devices.get(0).getCaption());
        assertEquals(GpodnetDevice.DeviceType.MOBILE, devices.get(0).getType());
        assertEquals(4, devices.get(0).getSubscriptions());
        assertEquals(GpodnetDevice.DeviceType.OTHER, devices.get(1).getType());
        RecordedRequest request = takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/2/devices/alice.json", request.getPath());
    }

    @Test
    public void getDevicesWithMalformedJsonFails() {
        server.enqueue(new MockResponse().setBody("<html>not json</html>"));

        assertThrows(GpodnetServiceException.class, () -> service.getDevices());
    }

    @Test
    public void getDevicesWithMissingFieldFails() {
        server.enqueue(new MockResponse().setBody("[{\"id\":\"phone\"}]"));

        assertThrows(GpodnetServiceException.class, () -> service.getDevices());
    }

    @Test
    public void getDevicesWithAuthenticationErrorFailsWithAuthenticationException() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertThrows(GpodnetServiceAuthenticationException.class, () -> service.getDevices());
    }

    @Test
    public void configureDevicePostsCaptionAndType() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));

        service.configureDevice("phone", "My phone", GpodnetDevice.DeviceType.MOBILE);

        RecordedRequest request = takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/2/devices/alice/phone.json", request.getPath());
        JSONObject body = new JSONObject(request.getBody().readUtf8());
        assertEquals("My phone", body.getString("caption"));
        assertEquals("mobile", body.getString("type"));
    }

    @Test
    public void configureDeviceWithoutCaptionAndTypeSendsEmptyBody() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));

        service.configureDevice("phone", null, null);

        RecordedRequest request = takeRequest();
        assertEquals(0, request.getBodySize());
    }

    @Test
    public void configureDeviceWithOnlyCaptionOmitsType() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));

        service.configureDevice("phone", "Renamed", null);

        JSONObject body = new JSONObject(takeRequest().getBody().readUtf8());
        assertEquals("Renamed", body.getString("caption"));
        assertFalse(body.has("type"));
    }

    @Test
    public void uploadSubscriptionChangesPostsAddAndRemoveListsAndReadsUpdatedUrls() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"timestamp\":1234,"
                + "\"update_urls\":[[\"http://a.example/feed\",\"https://a.example/feed\"]]}"));

        UploadChangesResponse response = service.uploadSubscriptionChanges(
                Arrays.asList("http://a.example/feed", "http://b.example/feed"),
                Collections.singletonList("http://c.example/feed"));

        assertEquals(1234, response.timestamp);
        assertEquals("https://a.example/feed",
                ((GpodnetUploadChangesResponse) response).updatedUrls.get("http://a.example/feed"));
        RecordedRequest request = takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/2/subscriptions/alice/device1.json", request.getPath());
        JSONObject body = new JSONObject(request.getBody().readUtf8());
        assertEquals(2, body.getJSONArray("add").length());
        assertEquals("http://a.example/feed", body.getJSONArray("add").getString(0));
        assertEquals("http://b.example/feed", body.getJSONArray("add").getString(1));
        assertEquals(1, body.getJSONArray("remove").length());
        assertEquals("http://c.example/feed", body.getJSONArray("remove").getString(0));
    }

    @Test
    public void uploadSubscriptionChangesWithoutTimestampInResponseFails() {
        server.enqueue(new MockResponse().setBody("{\"update_urls\":[]}"));

        assertThrows(GpodnetServiceException.class, () -> service.uploadSubscriptionChanges(
                Collections.singletonList("http://a.example/feed"), Collections.emptyList()));
    }

    @Test
    public void getSubscriptionChangesSendsTimestampAndUnescapesColons() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"timestamp\":99,"
                + "\"add\":[\"http%3A//a.example/feed\",\"http://b.example/feed\"],"
                + "\"remove\":[\"https%3A//c.example/feed\"]}"));

        SubscriptionChanges changes = service.getSubscriptionChanges(42);

        assertEquals(99, changes.getTimestamp());
        assertEquals(Arrays.asList("http://a.example/feed", "http://b.example/feed"), changes.getAdded());
        assertEquals(Collections.singletonList("https://c.example/feed"), changes.getRemoved());
        RecordedRequest request = takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/2/subscriptions/alice/device1.json?since=42", request.getPath());
    }

    @Test
    public void getSubscriptionChangesWithMissingListsFails() {
        server.enqueue(new MockResponse().setBody("{\"timestamp\":99}"));

        assertThrows(GpodnetServiceException.class, () -> service.getSubscriptionChanges(0));
    }

    @Test
    public void getSubscriptionChangesWithServerErrorFailsWithStatusCode() {
        server.enqueue(new MockResponse().setResponseCode(500));

        GpodnetServiceBadStatusCodeException exception = assertThrows(GpodnetServiceBadStatusCodeException.class,
                () -> service.getSubscriptionChanges(0));

        assertTrue(exception.getMessage().contains("500"));
    }

    @Test
    public void uploadEpisodeActionsPostsActionsWithDeviceId() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"timestamp\":555,\"update_urls\":[]}"));

        UploadChangesResponse response = service.uploadEpisodeActions(Collections.singletonList(playAction(1)));

        assertEquals(555, response.timestamp);
        RecordedRequest request = takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/2/episodes/alice.json", request.getPath());
        JSONArray body = new JSONArray(request.getBody().readUtf8());
        assertEquals(1, body.length());
        JSONObject action = body.getJSONObject(0);
        assertEquals("device1", action.getString("device"));
        assertEquals("play", action.getString("action"));
        assertEquals(FEED_URL, action.getString("podcast"));
        assertEquals(EPISODE_URL, action.getString("episode"));
        assertEquals("2023-11-14T22:13:20", action.getString("timestamp"));
        assertEquals(0, action.getInt("started"));
        assertEquals(10, action.getInt("position"));
        assertEquals(100, action.getInt("total"));
    }

    @Test
    public void uploadEpisodeActionsSplitsLargeListsIntoBatchesAndReturnsLastResponse() throws Exception {
        int actionCount = 65;
        server.enqueue(new MockResponse().setBody("{\"timestamp\":1,\"update_urls\":[]}"));
        server.enqueue(new MockResponse().setBody("{\"timestamp\":2,\"update_urls\":[]}"));
        server.enqueue(new MockResponse().setBody("{\"timestamp\":3,\"update_urls\":[]}"));
        List<EpisodeAction> actions = new ArrayList<>();
        for (int i = 0; i < actionCount; i++) {
            actions.add(playAction(i));
        }

        UploadChangesResponse response = service.uploadEpisodeActions(actions);

        assertEquals(3, response.timestamp);
        assertEquals(3, server.getRequestCount() - 1);
        int total = 0;
        List<Integer> batchSizes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int size = new JSONArray(takeRequest().getBody().readUtf8()).length();
            batchSizes.add(size);
            total += size;
        }
        assertEquals(actionCount, total);
        assertEquals(Arrays.asList(30, 30, 5), batchSizes);
    }

    @Test
    public void uploadEpisodeActionsStopsAtFirstFailingBatch() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"timestamp\":1,\"update_urls\":[]}"));
        server.enqueue(new MockResponse().setResponseCode(500));
        List<EpisodeAction> actions = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            actions.add(playAction(i));
        }

        assertThrows(GpodnetServiceBadStatusCodeException.class, () -> service.uploadEpisodeActions(actions));

        assertEquals(2, server.getRequestCount() - 1);
    }

    @Test
    public void uploadEpisodeActionsWithEmptyListContactsNoServer() throws Exception {
        service.uploadEpisodeActions(Collections.emptyList());

        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void getEpisodeActionChangesParsesValidActionsAndSkipsInvalidOnes() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"timestamp\":777,\"actions\":["
                + "{\"podcast\":\"" + FEED_URL + "\",\"episode\":\"" + EPISODE_URL + "\",\"guid\":\"guid-1\","
                + "\"action\":\"play\",\"timestamp\":\"2023-11-14T22:13:20\","
                + "\"started\":5,\"position\":50,\"total\":100},"
                + "{\"podcast\":\"" + FEED_URL + "\",\"action\":\"play\"},"
                + "{\"podcast\":\"" + FEED_URL + "\",\"episode\":\"" + EPISODE_URL + "\",\"action\":\"explode\"},"
                + "{\"podcast\":\"" + FEED_URL + "\",\"episode\":\"" + EPISODE_URL + "\",\"action\":\"delete\"}"
                + "]}"));

        EpisodeActionChanges changes = service.getEpisodeActionChanges(123);

        assertEquals(777, changes.getTimestamp());
        assertEquals(2, changes.getEpisodeActions().size());
        EpisodeAction play = changes.getEpisodeActions().get(0);
        assertEquals(EpisodeAction.PLAY, play.getAction());
        assertEquals("guid-1", play.getGuid());
        assertEquals(FEED_URL, play.getPodcast());
        assertEquals(EPISODE_URL, play.getEpisode());
        assertEquals(5, play.getStarted());
        assertEquals(50, play.getPosition());
        assertEquals(100, play.getTotal());
        assertEquals(1_700_000_000_000L, play.getTimestamp().getTime());
        assertEquals(EpisodeAction.DELETE, changes.getEpisodeActions().get(1).getAction());
        RecordedRequest request = takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/2/episodes/alice.json?since=123", request.getPath());
    }

    @Test
    public void getEpisodeActionChangesWithMalformedBodyFails() {
        server.enqueue(new MockResponse().setBody("nonsense"));

        assertThrows(SyncServiceException.class, () -> service.getEpisodeActionChanges(0));
    }
}
