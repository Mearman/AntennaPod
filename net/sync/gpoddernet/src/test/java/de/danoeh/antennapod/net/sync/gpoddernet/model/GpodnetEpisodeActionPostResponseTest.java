package de.danoeh.antennapod.net.sync.gpoddernet.model;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class GpodnetEpisodeActionPostResponseTest {
    @Test
    public void parsesTimestamp() throws JSONException {
        GpodnetEpisodeActionPostResponse response = GpodnetEpisodeActionPostResponse.fromJSONObject(
                "{\"timestamp\": 31, \"update_urls\": []}");

        assertEquals(31, response.timestamp);
    }

    @Test
    public void descriptionContainsRewrittenUrls() throws JSONException {
        GpodnetEpisodeActionPostResponse response = GpodnetEpisodeActionPostResponse.fromJSONObject(
                "{\"timestamp\": 31, \"update_urls\": [[\"http://a.example\", \"http://sanitised.example\"]]}");

        assertTrue(response.toString().contains("http://sanitised.example"));
    }

    @Test
    public void missingTimestampIsRejected() {
        assertThrows(JSONException.class,
                () -> GpodnetEpisodeActionPostResponse.fromJSONObject("{\"update_urls\": []}"));
    }

    @Test
    public void malformedBodyIsRejected() {
        assertThrows(JSONException.class, () -> GpodnetEpisodeActionPostResponse.fromJSONObject("<html>"));
    }
}
