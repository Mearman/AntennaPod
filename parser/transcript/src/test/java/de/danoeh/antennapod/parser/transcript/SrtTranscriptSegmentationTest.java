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
public class SrtTranscriptSegmentationTest {

    @Test
    public void timecodeIsConvertedToMilliseconds() {
        assertEquals(3723004L, SrtTranscriptParser.parseTimecode("01:02:03,004"));
    }

    @Test
    public void malformedTimecodesAreRejected() {
        assertEquals(-1L, SrtTranscriptParser.parseTimecode("1:02:03,004"));
        assertEquals(-1L, SrtTranscriptParser.parseTimecode("01:02:03.004"));
        assertEquals(-1L, SrtTranscriptParser.parseTimecode(""));
    }

    @Test
    public void windowsLineEndingsAreAccepted() {
        Transcript transcript = SrtTranscriptParser.parse(
                "1\r\n00:00:00,000 --> 00:00:02,000\r\nHello there\r\n\r\n");
        assertNotNull(transcript);
        assertEquals("Hello there", transcript.getSegmentAt(0).getWords());
        assertEquals(2000L, transcript.getSegmentAt(0).getEndTime());
    }

    @Test
    public void cueReachingMinimumLengthIsClosedImmediately() {
        Transcript transcript = SrtTranscriptParser.parse(
                "1\n00:00:00,000 --> 00:00:06,000\nFirst\n\n2\n00:00:06,000 --> 00:00:07,000\nSecond\n");
        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals("First", transcript.getSegmentAt(0).getWords());
        assertEquals(6000L, transcript.getSegmentAt(0).getEndTime());
        assertEquals("Second", transcript.getSegmentAt(1).getWords());
        assertEquals(6000L, transcript.getSegmentAt(1).getStartTime());
    }

    @Test
    public void shortCuesAreMergedIntoOneSegment() {
        Transcript transcript = SrtTranscriptParser.parse(
                "1\n00:00:00,000 --> 00:00:01,000\nOne\n\n2\n00:00:01,000 --> 00:00:02,000\nTwo\n");
        assertNotNull(transcript);
        assertEquals(1, transcript.getSegmentCount());
        assertEquals("One Two", transcript.getSegmentAt(0).getWords());
        assertEquals(2000L, transcript.getSegmentAt(0).getEndTime());
    }

    @Test
    public void multiLineCueBodyIsJoinedWithSpaces() {
        Transcript transcript = SrtTranscriptParser.parse(
                "1\n00:00:00,000 --> 00:00:02,000\nFirst line\nSecond line\n");
        assertNotNull(transcript);
        assertEquals("First line Second line", transcript.getSegmentAt(0).getWords());
    }

    @Test
    public void speakerChangeStartsNewSegmentAndSpeakersAreCollected() {
        Transcript transcript = SrtTranscriptParser.parse(
                "1\n00:00:00,000 --> 00:00:02,000\nAlice: Hi there\n\n"
                        + "2\n00:00:02,000 --> 00:00:04,000\nBob: Hello\n");
        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals("Alice", transcript.getSegmentAt(0).getSpeaker());
        assertEquals("Hi there", transcript.getSegmentAt(0).getWords());
        assertEquals("Bob", transcript.getSegmentAt(1).getSpeaker());
        assertEquals("Hello", transcript.getSegmentAt(1).getWords());
        assertEquals(Set.of("Alice", "Bob"), transcript.getSpeakers());
    }

    @Test
    public void sameSpeakerContinuesSegment() {
        Transcript transcript = SrtTranscriptParser.parse(
                "1\n00:00:00,000 --> 00:00:02,000\nAlice: Hi there\n\n"
                        + "2\n00:00:02,000 --> 00:00:04,000\nAlice: Still me\n");
        assertNotNull(transcript);
        assertEquals(1, transcript.getSegmentCount());
        assertEquals("Alice", transcript.getSegmentAt(0).getSpeaker());
        assertEquals(Set.of("Alice"), transcript.getSpeakers());
    }

    @Test
    public void cueWithSingleTimecodeIsIgnored() {
        Transcript transcript = SrtTranscriptParser.parse(
                "1\n00:00:01,000 -->\nIgnored\n\n2\n00:00:02,000 --> 00:00:03,000\nKept\n");
        assertNotNull(transcript);
        assertEquals(1, transcript.getSegmentCount());
        assertEquals("Kept", transcript.getSegmentAt(0).getWords());
    }

    @Test
    public void whitespaceOnlyInputGivesNoTranscript() {
        assertNull(SrtTranscriptParser.parse("   \n  "));
    }
}
