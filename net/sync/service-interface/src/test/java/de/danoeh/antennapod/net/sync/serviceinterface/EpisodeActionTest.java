package de.danoeh.antennapod.net.sync.serviceinterface;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class EpisodeActionTest {
    private static final long TIMESTAMP_MILLIS = 1609488000000L;
    private static final String TIMESTAMP_STRING = "2021-01-01T08:00:00";

    private static JSONObject playJson() throws JSONException {
        return new JSONObject()
                .put("podcast", "http://example.com/feed")
                .put("episode", "http://example.com/episode.mp3")
                .put("action", "play")
                .put("timestamp", TIMESTAMP_STRING)
                .put("guid", "guid-1")
                .put("started", 10)
                .put("position", 20)
                .put("total", 30);
    }

    @Test
    public void readFromJsonObjectParsesAllFieldsOfPlayAction() throws JSONException {
        EpisodeAction action = EpisodeAction.readFromJsonObject(playJson());

        assertNotNull(action);
        assertEquals("http://example.com/feed", action.getPodcast());
        assertEquals("http://example.com/episode.mp3", action.getEpisode());
        assertEquals(EpisodeAction.PLAY, action.getAction());
        assertEquals("guid-1", action.getGuid());
        assertEquals(10, action.getStarted());
        assertEquals(20, action.getPosition());
        assertEquals(30, action.getTotal());
    }

    @Test
    public void readFromJsonObjectInterpretsTimestampAsUtc() throws JSONException {
        EpisodeAction action = EpisodeAction.readFromJsonObject(playJson());

        assertEquals(TIMESTAMP_MILLIS, action.getTimestamp().getTime());
    }

    @Test
    public void readFromJsonObjectAcceptsActionInAnyCase() throws JSONException {
        EpisodeAction action = EpisodeAction.readFromJsonObject(playJson().put("action", "Download"));

        assertEquals(EpisodeAction.DOWNLOAD, action.getAction());
    }

    @Test
    public void readFromJsonObjectRejectsMissingMandatoryFields() throws JSONException {
        assertNull(EpisodeAction.readFromJsonObject(playJson().put("podcast", "")));
        assertNull(EpisodeAction.readFromJsonObject(playJson().put("episode", "")));
        assertNull(EpisodeAction.readFromJsonObject(playJson().put("action", "")));
        JSONObject withoutPodcast = playJson();
        withoutPodcast.remove("podcast");
        assertNull(EpisodeAction.readFromJsonObject(withoutPodcast));
    }

    @Test
    public void readFromJsonObjectRejectsUnknownAction() throws JSONException {
        assertNull(EpisodeAction.readFromJsonObject(playJson().put("action", "rewind")));
    }

    @Test
    public void readFromJsonObjectKeepsActionWithUnparsableTimestampWithoutTimestamp() throws JSONException {
        EpisodeAction action = EpisodeAction.readFromJsonObject(playJson().put("timestamp", "yesterday"));

        assertNotNull(action);
        assertNull(action.getTimestamp());
    }

    @Test
    public void readFromJsonObjectLeavesGuidNullWhenEmpty() throws JSONException {
        EpisodeAction action = EpisodeAction.readFromJsonObject(playJson().put("guid", ""));

        assertNull(action.getGuid());
    }

    @Test
    public void readFromJsonObjectIgnoresIncompletePlaybackPositions() throws JSONException {
        assertPlaybackIgnored(playJson().put("started", -1));
        assertPlaybackIgnored(playJson().put("position", 0));
        assertPlaybackIgnored(playJson().put("total", 0));
    }

    private static void assertPlaybackIgnored(JSONObject json) {
        EpisodeAction action = EpisodeAction.readFromJsonObject(json);

        assertNotNull(action);
        assertEquals(-1, action.getStarted());
        assertEquals(-1, action.getPosition());
        assertEquals(-1, action.getTotal());
    }

    @Test
    public void readFromJsonObjectIgnoresPlaybackPositionsOfNonPlayActions() throws JSONException {
        EpisodeAction action = EpisodeAction.readFromJsonObject(playJson().put("action", "delete"));

        assertEquals(EpisodeAction.DELETE, action.getAction());
        assertEquals(-1, action.getStarted());
        assertEquals(-1, action.getPosition());
        assertEquals(-1, action.getTotal());
    }

    @Test
    public void writeToJsonObjectContainsPlaybackPositionsForPlayAction() throws JSONException {
        EpisodeAction action = new EpisodeAction.Builder("podcast", "episode", EpisodeAction.PLAY)
                .timestamp(new Date(TIMESTAMP_MILLIS))
                .guid("guid-1")
                .started(1)
                .position(2)
                .total(3)
                .build();

        JSONObject json = action.writeToJsonObject();

        assertEquals("podcast", json.getString("podcast"));
        assertEquals("episode", json.getString("episode"));
        assertEquals("guid-1", json.getString("guid"));
        assertEquals("play", json.getString("action"));
        assertEquals(TIMESTAMP_STRING, json.getString("timestamp"));
        assertEquals(1, json.getInt("started"));
        assertEquals(2, json.getInt("position"));
        assertEquals(3, json.getInt("total"));
    }

    @Test
    public void writeToJsonObjectOmitsPlaybackPositionsAndMissingGuidForOtherActions() {
        EpisodeAction action = new EpisodeAction.Builder("podcast", "episode", EpisodeAction.DOWNLOAD)
                .timestamp(new Date(TIMESTAMP_MILLIS))
                .build();

        JSONObject json = action.writeToJsonObject();

        assertEquals("download", json.optString("action"));
        assertFalse(json.has("guid"));
        assertFalse(json.has("started"));
        assertFalse(json.has("position"));
        assertFalse(json.has("total"));
    }

    @Test
    public void writtenJsonCanBeReadBack() {
        EpisodeAction original = new EpisodeAction.Builder("podcast", "episode", EpisodeAction.PLAY)
                .timestamp(new Date(TIMESTAMP_MILLIS))
                .guid("guid-1")
                .started(5)
                .position(50)
                .total(500)
                .build();

        EpisodeAction copy = EpisodeAction.readFromJsonObject(original.writeToJsonObject());

        assertNotNull(copy);
        assertEquals(original.getPodcast(), copy.getPodcast());
        assertEquals(original.getEpisode(), copy.getEpisode());
        assertEquals(original.getGuid(), copy.getGuid());
        assertEquals(original.getAction(), copy.getAction());
        assertEquals(original.getTimestamp(), copy.getTimestamp());
        assertEquals(original.getStarted(), copy.getStarted());
        assertEquals(original.getPosition(), copy.getPosition());
        assertEquals(original.getTotal(), copy.getTotal());
    }

    @Test
    public void builderIgnoresPlaybackPositionsForNonPlayActions() {
        EpisodeAction action = new EpisodeAction.Builder("podcast", "episode", EpisodeAction.NEW)
                .started(1)
                .position(2)
                .total(3)
                .build();

        assertEquals(-1, action.getStarted());
        assertEquals(-1, action.getPosition());
        assertEquals(-1, action.getTotal());
    }

    @Test
    public void builderDefaultsToNoTimestampAndNoGuid() {
        EpisodeAction action = new EpisodeAction.Builder("podcast", "episode", EpisodeAction.PLAY).build();

        assertNull(action.getTimestamp());
        assertNull(action.getGuid());
    }

    @Test
    public void builderCurrentTimestampIsTakenFromTheClock() {
        long before = System.currentTimeMillis();
        EpisodeAction action = new EpisodeAction.Builder("podcast", "episode", EpisodeAction.PLAY)
                .currentTimestamp()
                .build();
        long after = System.currentTimeMillis();

        assertTrue(action.getTimestamp().getTime() >= before);
        assertTrue(action.getTimestamp().getTime() <= after);
    }

    @Test
    public void builderFromFeedItemUsesFeedUrlMediaUrlAndItemIdentifier() {
        Feed feed = new Feed("http://example.com/feed", null, "Feed");
        FeedItem item = new FeedItem(1, "Episode", "guid-1", "http://example.com/link", new Date(), 0, feed);
        item.setMedia(new FeedMedia(item, "http://example.com/episode.mp3", 1000, "audio/mp3"));

        EpisodeAction action = new EpisodeAction.Builder(item, EpisodeAction.PLAY).build();

        assertEquals("http://example.com/feed", action.getPodcast());
        assertEquals("http://example.com/episode.mp3", action.getEpisode());
        assertEquals("guid-1", action.getGuid());
        assertEquals(EpisodeAction.PLAY, action.getAction());
    }
}
