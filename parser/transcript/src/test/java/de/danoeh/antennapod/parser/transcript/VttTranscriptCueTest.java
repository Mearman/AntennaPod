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
public class VttTranscriptCueTest {

    @Test
    public void headerWithoutCuesGivesNoTranscript() {
        assertNull(VttTranscriptParser.parse("WEBVTT\n\nNOTE only a note\n"));
    }

    @Test
    public void whitespaceOnlyInputGivesNoTranscript() {
        assertNull(VttTranscriptParser.parse(" \n\t\n"));
    }

    @Test
    public void cueWithUnparsableEndTimestampRejectsWholeInput() {
        assertNull(VttTranscriptParser.parse(
                "WEBVTT\n\n00:00.000 --> 00:01.000\nValid\n\n00:02.000 --> later\nBroken\n"));
    }

    @Test
    public void cueWithUnparsableStartTimestampRejectsWholeInput() {
        assertNull(VttTranscriptParser.parse("WEBVTT\n\nsoon --> 00:01.000\nBroken\n"));
    }

    @Test
    public void cueArrowWithoutEndTimestampRejectsWholeInput() {
        assertNull(VttTranscriptParser.parse("WEBVTT\n\n00:01.000 -->\nBroken\n"));
    }

    @Test
    public void bareArrowLineRejectsWholeInput() {
        assertNull(VttTranscriptParser.parse("WEBVTT\n\n-->\nBroken\n"));
    }

    @Test
    public void carriageReturnLineEndingsAreAccepted() {
        Transcript transcript = VttTranscriptParser.parse("WEBVTT\r\r00:00.000 --> 00:01.000\rHello\r");
        assertNotNull(transcript);
        assertEquals("Hello", transcript.getSegmentAt(0).getWords());
    }

    @Test
    public void multiLinePayloadIsJoinedAndMarkupIsRemoved() {
        Transcript transcript = VttTranscriptParser.parse(
                "WEBVTT\n\n00:00.000 --> 00:02.000\n<b>Bold</b> start\nand <i>italic</i> end\n");
        assertNotNull(transcript);
        assertEquals("Bold start and italic end", transcript.getSegmentAt(0).getWords());
    }

    @Test
    public void voiceWithClassesProvidesSpeakerName() {
        Transcript transcript = VttTranscriptParser.parse(
                "WEBVTT\n\n00:00.000 --> 00:02.000\n<v.loud.shout Grace Hopper>Hello</v>\n");
        assertNotNull(transcript);
        assertEquals("Grace Hopper", transcript.getSegmentAt(0).getSpeaker());
        assertEquals(Set.of("Grace Hopper"), transcript.getSpeakers());
    }

    @Test
    public void cuesWithoutVoiceHaveEmptySpeakerAndNoSpeakers() {
        Transcript transcript = VttTranscriptParser.parse("WEBVTT\n\n00:00.000 --> 00:02.000\nHello\n");
        assertNotNull(transcript);
        assertEquals("", transcript.getSegmentAt(0).getSpeaker());
        assertEquals(Set.of(), transcript.getSpeakers());
    }

    @Test
    public void singleDigitHourTimestampIsAccepted() {
        Transcript transcript = VttTranscriptParser.parse("WEBVTT\n\n1:00:00.000 --> 1:00:01.500\nLate\n");
        assertNotNull(transcript);
        assertEquals(3600000L, transcript.getSegmentAt(0).getStartTime());
        assertEquals(3601500L, transcript.getSegmentAt(0).getEndTime());
    }

    @Test
    public void speakerChangeStartsNewSegment() {
        Transcript transcript = VttTranscriptParser.parse(
                "WEBVTT\n\n00:00.000 --> 00:01.000\n<v A>One\n\n00:01.000 --> 00:02.000\n<v B>Two\n");
        assertNotNull(transcript);
        assertEquals(2, transcript.getSegmentCount());
        assertEquals("A", transcript.getSegmentAt(0).getSpeaker());
        assertEquals("B", transcript.getSegmentAt(1).getSpeaker());
    }
}
