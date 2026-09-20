package de.danoeh.antennapod.net.sync.nextcloud;

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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class NextcloudSyncServiceTest {
    private static final String FEED_URL = "https://example.com/feed.xml";
    private static final String EPISODE_URL = "https://example.com/episode1.mp3";
    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private NextcloudSyncService newService(String subfolder) {
        String hostUrl = "http://" + server.getHostName() + ":" + server.getPort() + subfolder;
        return new NextcloudSyncService(new OkHttpClient(), hostUrl, "alice", "app-password");
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
    public void getSubscriptionChangesSendsBasicAuthAndSinceParameter() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"timestamp\":50,"
                + "\"add\":[\"http://a.example/feed\"],\"remove\":[\"http://b.example/feed\"]}"));

        SubscriptionChanges changes = newService("").getSubscriptionChanges(7);

        assertEquals(50, changes.getTimestamp());
        assertEquals(Collections.singletonList("http://a.example/feed"), changes.getAdded());
        assertEquals(Collections.singletonList("http://b.example/feed"), changes.getRemoved());
        RecordedRequest request = takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/index.php/apps/gpoddersync/subscriptions?since=7", request.getPath());
        assertEquals("Basic YWxpY2U6YXBwLXBhc3N3b3Jk", request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Accept"));
    }

    @Test
    public void requestsAreSentBelowConfiguredSubfolder() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"timestamp\":1,\"add\":[],\"remove\":[]}"));

        newService("/nextcloud/").getSubscriptionChanges(0);

        assertEquals("/nextcloud/index.php/apps/gpoddersync/subscriptions?since=0", takeRequest().getPath());
    }

    @Test
    public void getSubscriptionChangesWithUnexpectedStatusFailsWithSyncException() {
        server.enqueue(new MockResponse().setResponseCode(403));

        SyncServiceException exception = assertThrows(SyncServiceException.class,
                () -> newService("").getSubscriptionChanges(0));

        assertTrue(exception.getCause().getMessage().contains("403"));
    }

    @Test
    public void getSubscriptionChangesWithMalformedBodyFailsWithSyncException() {
        server.enqueue(new MockResponse().setBody("not json"));

        assertThrows(SyncServiceException.class, () -> newService("").getSubscriptionChanges(0));
    }

    @Test
    public void uploadSubscriptionChangesPostsAddAndRemoveLists() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));

        UploadChangesResponse response = newService("").uploadSubscriptionChanges(
                Arrays.asList("http://a.example/feed", "http://b.example/feed"),
                Collections.singletonList("http://c.example/feed"));

        assertTrue(response.timestamp > 0);
        RecordedRequest request = takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/index.php/apps/gpoddersync/subscription_change/create", request.getPath());
        assertTrue(request.getHeader("Content-Type").startsWith("application/json"));
        JSONObject body = new JSONObject(request.getBody().readUtf8());
        assertEquals(2, body.getJSONArray("add").length());
        assertEquals("http://a.example/feed", body.getJSONArray("add").getString(0));
        assertEquals("http://c.example/feed", body.getJSONArray("remove").getString(0));
    }

    @Test
    public void uploadSubscriptionChangesRejectedByServerFails() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThrows(NextcloudSynchronizationServiceException.class, () -> newService("")
                .uploadSubscriptionChanges(Collections.singletonList("http://a.example/feed"),
                        Collections.emptyList()));
    }

    @Test
    public void getEpisodeActionChangesParsesActions() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"timestamp\":88,\"actions\":["
                + "{\"podcast\":\"" + FEED_URL + "\",\"episode\":\"" + EPISODE_URL + "\",\"action\":\"play\","
                + "\"timestamp\":\"2023-11-14T22:13:20\",\"started\":0,\"position\":30,\"total\":60}]}"));

        EpisodeActionChanges changes = newService("").getEpisodeActionChanges(11);

        assertEquals(88, changes.getTimestamp());
        assertEquals(1, changes.getEpisodeActions().size());
        assertEquals(30, changes.getEpisodeActions().get(0).getPosition());
        assertEquals("/index.php/apps/gpoddersync/episode_action?since=11", takeRequest().getPath());
    }

    @Test
    public void getEpisodeActionChangesWithServerErrorFailsWithSyncException() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThrows(SyncServiceException.class, () -> newService("").getEpisodeActionChanges(0));
    }

    @Test
    public void uploadEpisodeActionsSplitsLargeListsIntoBatches() throws Exception {
        List<EpisodeAction> actions = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            actions.add(playAction(i));
        }
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setBody("{}"));
        }

        UploadChangesResponse response = newService("").uploadEpisodeActions(actions);

        assertTrue(response.timestamp > 0);
        List<Integer> batchSizes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            RecordedRequest request = takeRequest();
            assertEquals("/index.php/apps/gpoddersync/episode_action/create", request.getPath());
            batchSizes.add(new JSONArray(request.getBody().readUtf8()).length());
        }
        assertEquals(Arrays.asList(30, 30, 1), batchSizes);
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void uploadEpisodeActionsRejectedByServerFails() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertThrows(NextcloudSynchronizationServiceException.class,
                () -> newService("").uploadEpisodeActions(Collections.singletonList(playAction(1))));
    }

    @Test
    public void loginAndLogoutDoNotContactServer() {
        NextcloudSyncService service = newService("");

        service.login();
        service.logout();

        assertEquals(0, server.getRequestCount());
    }
}
