package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TranscriptTypeTest {

    @Test
    public void fromMime_canonicalMimeTypes_returnMatchingType() {
        assertEquals(TranscriptType.JSON, TranscriptType.fromMime("application/json"));
        assertEquals(TranscriptType.VTT, TranscriptType.fromMime("text/vtt"));
        assertEquals(TranscriptType.SRT, TranscriptType.fromMime("application/srt"));
    }

    @Test
    public void fromMime_srtAliases_returnSrt() {
        assertEquals(TranscriptType.SRT, TranscriptType.fromMime("application/srr"));
        assertEquals(TranscriptType.SRT, TranscriptType.fromMime("application/x-subrip"));
    }

    @Test
    public void fromMime_nullOrUnknown_returnNone() {
        assertEquals(TranscriptType.NONE, TranscriptType.fromMime(null));
        assertEquals(TranscriptType.NONE, TranscriptType.fromMime("text/html"));
        assertEquals(TranscriptType.NONE, TranscriptType.fromMime(""));
    }

    @Test
    public void priority_jsonPreferredOverVttOverSrtOverNone() {
        assertTrue(TranscriptType.JSON.priority > TranscriptType.VTT.priority);
        assertTrue(TranscriptType.VTT.priority > TranscriptType.SRT.priority);
        assertTrue(TranscriptType.SRT.priority > TranscriptType.NONE.priority);
    }

    @Test
    public void canonicalMime_roundTripsThroughFromMime() {
        for (TranscriptType type : TranscriptType.values()) {
            assertEquals(type, TranscriptType.fromMime(type.canonicalMime));
        }
    }
}
