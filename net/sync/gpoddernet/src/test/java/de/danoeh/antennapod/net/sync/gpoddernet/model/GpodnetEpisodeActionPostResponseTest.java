package de.danoeh.antennapod.net.sync.gpoddernet.model;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
public class GpodnetEpisodeActionPostResponseTest {
    @Test
    public void parsesTimestamp() throws JSONException {
        GpodnetEpisodeActionPostResponse response = GpodnetEpisodeActionPostResponse.fromJSONObject(
                "{\"timestamp\": 31, \"update_urls\": []}");

        assertEquals(31, response.timestamp);
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
