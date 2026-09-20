package de.danoeh.antennapod.model.playback;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class MediaTypeTest {

    @Test
    public void fromMimeType_nullOrEmpty_returnsUnknown() {
        assertEquals(MediaType.UNKNOWN, MediaType.fromMimeType(null));
        assertEquals(MediaType.UNKNOWN, MediaType.fromMimeType(""));
    }

    @Test
    public void fromMimeType_audioPrefix_returnsAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("audio/mpeg"));
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("audio/x-m4a"));
    }

    @Test
    public void fromMimeType_videoPrefix_returnsVideo() {
        assertEquals(MediaType.VIDEO, MediaType.fromMimeType("video/mp4"));
    }

    @Test
    public void fromMimeType_audioApplicationTypes_returnAudio() {
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("application/ogg"));
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("application/opus"));
        assertEquals(MediaType.AUDIO, MediaType.fromMimeType("application/x-flac"));
    }

    @Test
    public void fromMimeType_otherApplicationTypes_returnUnknown() {
        assertEquals(MediaType.UNKNOWN, MediaType.fromMimeType("application/pdf"));
        assertEquals(MediaType.UNKNOWN, MediaType.fromMimeType("text/html"));
    }
}
