package de.danoeh.antennapod.net.sync.nextcloud;

import de.danoeh.antennapod.net.sync.FakeHttpClient;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeActionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.SubscriptionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException;
import de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse;
import okhttp3.Credentials;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
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
public class NextcloudSyncServiceTest {
    private FakeHttpClient http;
    private NextcloudSyncService service;

    @Before
    public void setUp() {
        http = new FakeHttpClient();
        service = new NextcloudSyncService(http.client(), "https://cloud.example", "alice", "app-password");
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
    public void getSubscriptionChangesSendsAuthenticatedGetWithSinceParameter() throws Exception {
        http.enqueue(200, "{\"add\": [\"http://a.example/feed\"], \"remove\": [], \"timestamp\": 50}");

        SubscriptionChanges changes = service.getSubscriptionChanges(12);

        assertEquals("GET", http.request(0).request.method());
        assertEquals("https://cloud.example/index.php/apps/gpoddersync/subscriptions?since=12",
                http.request(0).request.url().toString());
        assertEquals(Credentials.basic("alice", "app-password"), http.request(0).request.header("Authorization"));
        assertEquals("application/json", http.request(0).request.header("Accept"));
        assertEquals(Collections.singletonList("http://a.example/feed"), changes.getAdded());
        assertEquals(50, changes.getTimestamp());
    }

    @Test
    public void getSubscriptionChangesFailsOnNon200Status() {
        http.enqueue(403, "");

        SyncServiceException exception = assertThrows(SyncServiceException.class,
                () -> service.getSubscriptionChanges(0));

        assertTrue(exception.getCause().getMessage().contains("403"));
    }

    @Test
    public void getSubscriptionChangesFailsOnMalformedBody() {
        http.enqueue(200, "nope");

        assertThrows(SyncServiceException.class, () -> service.getSubscriptionChanges(0));
    }

    @Test
    public void getSubscriptionChangesWrapsNetworkFailures() {
        IOException failure = new IOException("offline");
        http.enqueueFailure(failure);

        SyncServiceException exception = assertThrows(SyncServiceException.class,
                () -> service.getSubscriptionChanges(0));

        assertEquals(failure, exception.getCause());
    }

    @Test
    public void uploadSubscriptionChangesPostsAddedAndRemovedFeeds() throws Exception {
        http.enqueue(200, "");
        long before = System.currentTimeMillis() / 1000;

        UploadChangesResponse response = service.uploadSubscriptionChanges(
                Arrays.asList("http://a.example", "http://b.example"), Collections.singletonList("http://c.example"));

        long after = System.currentTimeMillis() / 1000;
        assertEquals("POST", http.request(0).request.method());
        assertEquals("https://cloud.example/index.php/apps/gpoddersync/subscription_change/create",
                http.request(0).request.url().toString());
        JSONObject body = new JSONObject(http.request(0).body);
        assertEquals(new JSONArray(Arrays.asList("http://a.example", "http://b.example")).toString(),
                body.getJSONArray("add").toString());
        assertEquals("http://c.example", body.getJSONArray("remove").getString(0));
        assertTrue(response.timestamp >= before && response.timestamp <= after);
    }

    @Test
    public void uploadSubscriptionChangesFailsOnNon200Status() {
        http.enqueue(500, "");

        assertThrows(NextcloudSynchronizationServiceException.class,
                () -> service.uploadSubscriptionChanges(Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    public void getEpisodeActionChangesParsesActions() throws Exception {
        http.enqueue(200, "{\"timestamp\": 70, \"actions\": [{\"podcast\": \"p\", \"episode\": \"e\","
                + "\"action\": \"delete\"}]}");

        EpisodeActionChanges changes = service.getEpisodeActionChanges(33);

        assertEquals("https://cloud.example/index.php/apps/gpoddersync/episode_action?since=33",
                http.request(0).request.url().toString());
        assertEquals(70, changes.getTimestamp());
        assertEquals(1, changes.getEpisodeActions().size());
        assertEquals(EpisodeAction.DELETE, changes.getEpisodeActions().get(0).getAction());
    }

    @Test
    public void getEpisodeActionChangesFailsOnNon200Status() {
        http.enqueue(401, "");

        assertThrows(SyncServiceException.class, () -> service.getEpisodeActionChanges(0));
    }

    @Test
    public void getEpisodeActionChangesFailsOnMalformedBody() {
        http.enqueue(200, "{}");

        assertThrows(SyncServiceException.class, () -> service.getEpisodeActionChanges(0));
    }

    @Test
    public void uploadEpisodeActionsPostsActionsWithoutDeviceId() throws Exception {
        http.enqueue(200, "");

        service.uploadEpisodeActions(playActions(2));

        assertEquals("https://cloud.example/index.php/apps/gpoddersync/episode_action/create",
                http.request(0).request.url().toString());
        JSONArray body = new JSONArray(http.request(0).body);
        assertEquals(2, body.length());
        assertEquals("episode0", body.getJSONObject(0).getString("episode"));
        assertFalse(body.getJSONObject(0).has("device"));
    }

    @Test
    public void uploadEpisodeActionsSplitsLargeUploadsIntoBatches() throws Exception {
        http.replyToEverythingWith(200, "");

        service.uploadEpisodeActions(playActions(61));

        assertEquals(3, http.requests().size());
        assertEquals(30, new JSONArray(http.request(0).body).length());
        assertEquals(30, new JSONArray(http.request(1).body).length());
        assertEquals(1, new JSONArray(http.request(2).body).length());
    }

    @Test
    public void uploadEpisodeActionsWithoutActionsSendsNothing() throws Exception {
        UploadChangesResponse response = service.uploadEpisodeActions(Collections.emptyList());

        assertEquals(0, http.requests().size());
        assertTrue(response.timestamp > 0);
    }

    @Test
    public void uploadEpisodeActionsFailsOnNon200Status() {
        http.enqueue(500, "");

        assertThrows(NextcloudSynchronizationServiceException.class,
                () -> service.uploadEpisodeActions(playActions(1)));
    }

    @Test
    public void subfolderOfHostUrlPrefixesEveryEndpoint() throws Exception {
        service = new NextcloudSyncService(http.client(), "https://example.com:8443/nextcloud/", "alice", "pw");
        http.enqueue(200, "{\"add\": [], \"remove\": [], \"timestamp\": 1}");

        service.getSubscriptionChanges(0);

        assertEquals("https://example.com:8443/nextcloud/index.php/apps/gpoddersync/subscriptions?since=0",
                http.request(0).request.url().toString());
    }

    @Test
    public void loginAndLogoutDoNotContactTheServer() throws Exception {
        service.login();
        service.logout();

        assertEquals(0, http.requests().size());
    }
}
