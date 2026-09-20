package de.danoeh.antennapod.parser.transcript;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Set;

import de.danoeh.antennapod.model.feed.Transcript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class JsonTranscriptSegmentationTest {

    private static String segments(String... entries) {
        return "{\"version\":\"1.0.0\",\"segments\":[" + String.join(",", entries) + "]}";
    }

    private static String entry(String speaker, double start, double end, String body) {
        String speakerPart = speaker == null ? "" : "\"speaker\":\"" + speaker + "\",";
        return "{" + speakerPart + "\"startTime\":" + start + ",\"endTime\":" + end + ",\"body\":\"" + body + "\"}";
    }

    @Test
    public void spanReachingMinimumLengthIsClosedBeforeAlphanumericContinuation() {
        Transcript transcript = JsonTranscriptParser.parse(segments(
                entry("A", 0, 3, "Hello"),
                entry(null, 3, 6, "world."),
                entry(null, 6, 8, "Next"),
                entry(null, 8, 10, "again")));
        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals("Hello world.", transcript.getSegmentAt(0).getWords());
        assertEquals(0L, transcript.getSegmentAt(0).getStartTime());
        assertEquals(6000L, transcript.getSegmentAt(0).getEndTime());
        assertEquals("A", transcript.getSegmentAt(0).getSpeaker());
    }

    @Test
    public void spanIsExtendedWhenNextSegmentStartsWithPunctuation() {
        Transcript transcript = JsonTranscriptParser.parse(segments(
                entry("A", 0, 3, "Hello"),
                entry(null, 3, 6, "world"),
                entry(null, 6, 7, ", and more")));
        assertNotNull(transcript);
        assertEquals(1, transcript.getSegmentCount());
        assertEquals(0L, transcript.getSegmentAt(0).getStartTime());
        assertEquals(7000L, transcript.getSegmentAt(0).getEndTime());
    }

    @Test
    public void spanIsNotExtendedBeyondMaximumLengthEvenForPunctuation() {
        Transcript transcript = JsonTranscriptParser.parse(segments(
                entry("A", 0, 9, "Hello"),
                entry(null, 9, 10, ", trailing")));
        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals("Hello", transcript.getSegmentAt(0).getWords());
        assertEquals(9000L, transcript.getSegmentAt(0).getEndTime());
        assertEquals(", trailing", transcript.getSegmentAt(1).getWords());
        assertEquals(9000L, transcript.getSegmentAt(1).getStartTime());
    }

    @Test
    public void speakerChangeStartsNewSegmentAndAllSpeakersAreCollected() {
        Transcript transcript = JsonTranscriptParser.parse(segments(
                entry("Alice", 0, 1, "Hi"),
                entry("Bob", 1, 2, "Hello")));
        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals("Alice", transcript.getSegmentAt(0).getSpeaker());
        assertEquals("Hi", transcript.getSegmentAt(0).getWords());
        assertEquals("Bob", transcript.getSegmentAt(1).getSpeaker());
        Set<String> speakers = transcript.getSpeakers();
        assertEquals(Set.of("Alice", "Bob"), speakers);
    }

    @Test
    public void segmentsWithoutTimingsAreSkipped() {
        Transcript transcript = JsonTranscriptParser.parse(segments(
                entry("A", 0, 1, "kept"),
                "{\"speaker\":\"A\",\"body\":\"no timing\"}",
                "{\"startTime\":1.5,\"body\":\"no end\"}"));
        assertNotNull(transcript);
        assertEquals(1, transcript.getSegmentCount());
        assertEquals("kept", transcript.getSegmentAt(0).getWords());
    }

    @Test
    public void segmentsWithOnlyBlankBodiesGiveNoTranscript() {
        assertNull(JsonTranscriptParser.parse(segments(
                entry("A", 0, 1, ""),
                entry("A", 1, 2, " "))));
    }

    @Test
    public void nonObjectSegmentEntryGivesNoTranscript() {
        assertNull(JsonTranscriptParser.parse("{\"segments\":[\"just a string\"]}"));
    }

    @Test
    public void emptySegmentArrayGivesNoTranscript() {
        assertNull(JsonTranscriptParser.parse("{\"segments\":[]}"));
    }

    @Test
    public void fractionalTimesAreConvertedToMilliseconds() {
        Transcript transcript = JsonTranscriptParser.parse(segments(entry("A", 1.25, 2.5, "Word")));
        assertNotNull(transcript);
        assertEquals(1250L, transcript.getSegmentAt(0).getStartTime());
        assertEquals(2500L, transcript.getSegmentAt(0).getEndTime());
    }
}
