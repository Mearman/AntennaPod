package de.danoeh.antennapod.net.sync.gpoddernet.mapper;

import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeActionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.SubscriptionChanges;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ResponseMapperTest {
    @Test
    public void subscriptionChangesKeepOrderOfAddedAndRemovedFeeds() throws JSONException {
        JSONObject json = new JSONObject("{\"add\": [\"http://b.example\", \"http://a.example\"],"
                + "\"remove\": [\"http://d.example\", \"http://c.example\"], \"timestamp\": 42}");

        SubscriptionChanges changes = ResponseMapper.readSubscriptionChangesFromJsonObject(json);

        assertEquals(Arrays.asList("http://b.example", "http://a.example"), changes.getAdded());
        assertEquals(Arrays.asList("http://d.example", "http://c.example"), changes.getRemoved());
        assertEquals(42, changes.getTimestamp());
    }

    @Test
    public void subscriptionChangesUnescapeColonsInUrls() throws JSONException {
        JSONObject json = new JSONObject("{\"add\": [\"http%3A//a.example/feed\"],"
                + "\"remove\": [\"https%3A//b.example/feed\"], \"timestamp\": 1}");

        SubscriptionChanges changes = ResponseMapper.readSubscriptionChangesFromJsonObject(json);

        assertEquals(Arrays.asList("http://a.example/feed"), changes.getAdded());
        assertEquals(Arrays.asList("https://b.example/feed"), changes.getRemoved());
    }

    @Test
    public void subscriptionChangesWithoutTimestampAreRejected() throws JSONException {
        JSONObject json = new JSONObject("{\"add\": [], \"remove\": []}");

        assertThrows(JSONException.class, () -> ResponseMapper.readSubscriptionChangesFromJsonObject(json));
    }

    @Test
    public void subscriptionChangesWithoutRemovedListAreRejected() throws JSONException {
        JSONObject json = new JSONObject("{\"add\": [], \"timestamp\": 1}");

        assertThrows(JSONException.class, () -> ResponseMapper.readSubscriptionChangesFromJsonObject(json));
    }

    @Test
    public void episodeActionsSkipEntriesThatCannotBeConverted() throws JSONException {
        JSONObject json = new JSONObject("{\"timestamp\": 8, \"actions\": ["
                + "{\"podcast\": \"p\", \"episode\": \"e1\", \"action\": \"download\"},"
                + "{\"episode\": \"missing-podcast\", \"action\": \"download\"},"
                + "{\"podcast\": \"p\", \"episode\": \"e2\", \"action\": \"new\"}]}");

        EpisodeActionChanges changes = ResponseMapper.readEpisodeActionsFromJsonObject(json);

        assertEquals(8, changes.getTimestamp());
        assertEquals(2, changes.getEpisodeActions().size());
        assertEquals("e1", changes.getEpisodeActions().get(0).getEpisode());
        assertEquals(EpisodeAction.DOWNLOAD, changes.getEpisodeActions().get(0).getAction());
        assertEquals("e2", changes.getEpisodeActions().get(1).getEpisode());
        assertEquals(EpisodeAction.NEW, changes.getEpisodeActions().get(1).getAction());
    }

    @Test
    public void episodeActionsMayBeEmpty() throws JSONException {
        JSONObject json = new JSONObject("{\"timestamp\": 8, \"actions\": []}");

        assertTrue(ResponseMapper.readEpisodeActionsFromJsonObject(json).getEpisodeActions().isEmpty());
    }

    @Test
    public void episodeActionsWithoutActionListAreRejected() throws JSONException {
        JSONObject json = new JSONObject("{\"timestamp\": 8}");

        assertThrows(JSONException.class, () -> ResponseMapper.readEpisodeActionsFromJsonObject(json));
    }
}
