package de.danoeh.antennapod.net.sync.gpoddernet.model;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class GpodnetUploadChangesResponseTest {
    @Test
    public void parsesTimestampAndUrlRewrites() throws JSONException {
        GpodnetUploadChangesResponse response = GpodnetUploadChangesResponse.fromJSONObject(
                "{\"timestamp\": 12, \"update_urls\": [[\"http://a.example\", \"http://a.example/fixed\"],"
                        + "[\"http://b.example\", \"http://b.example/fixed\"]]}");

        assertEquals(12, response.timestamp);
        assertEquals(2, response.updatedUrls.size());
        assertEquals("http://a.example/fixed", response.updatedUrls.get("http://a.example"));
        assertEquals("http://b.example/fixed", response.updatedUrls.get("http://b.example"));
    }

    @Test
    public void noUrlRewritesGivesEmptyMap() throws JSONException {
        GpodnetUploadChangesResponse response = GpodnetUploadChangesResponse.fromJSONObject(
                "{\"timestamp\": 12, \"update_urls\": []}");

        assertTrue(response.updatedUrls.isEmpty());
    }

    @Test
    public void missingUrlRewriteListIsRejected() {
        assertThrows(JSONException.class, () -> GpodnetUploadChangesResponse.fromJSONObject("{\"timestamp\": 12}"));
    }

    @Test
    public void urlRewriteWithoutTargetIsRejected() {
        assertThrows(JSONException.class, () -> GpodnetUploadChangesResponse.fromJSONObject(
                "{\"timestamp\": 12, \"update_urls\": [[\"http://a.example\"]]}"));
    }

    @Test
    public void descriptionContainsTimestampAndRewrites() {
        GpodnetUploadChangesResponse response = new GpodnetUploadChangesResponse(
                77, Collections.singletonMap("http://a.example", "http://b.example"));

        assertTrue(response.toString().contains("77"));
        assertTrue(response.toString().contains("http://b.example"));
    }
}
