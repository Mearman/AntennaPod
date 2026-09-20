package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class TranscriptTest {
    private Transcript transcript;
    private TranscriptSegment first;
    private TranscriptSegment second;
    private TranscriptSegment third;

    @Before
    public void setUp() {
        transcript = new Transcript();
        first = new TranscriptSegment(0, 1000, "first", "A");
        second = new TranscriptSegment(1000, 2000, "second", "B");
        third = new TranscriptSegment(5000, 6000, "third", "A");
        transcript.addSegment(first);
        transcript.addSegment(second);
        transcript.addSegment(third);
    }

    @Test
    public void addSegment_countsSegments() {
        assertEquals(3, transcript.getSegmentCount());
        assertSame(second, transcript.getSegmentAt(1));
    }

    @Test
    public void addSegment_sameStartTimeAsPrevious_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> transcript.addSegment(new TranscriptSegment(5000, 7000, "dup", "A")));
        assertEquals(3, transcript.getSegmentCount());
    }

    @Test
    public void addSegment_earlierStartTimeThanPrevious_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> transcript.addSegment(new TranscriptSegment(4000, 4500, "early", "A")));
        assertEquals(3, transcript.getSegmentCount());
    }

    @Test
    public void findSegmentIndexBefore_timeInsideSegment_returnsThatSegment() {
        assertEquals(0, transcript.findSegmentIndexBefore(500));
        assertEquals(1, transcript.findSegmentIndexBefore(1500));
    }

    @Test
    public void findSegmentIndexBefore_timeExactlyAtStart_returnsThatSegment() {
        assertEquals(1, transcript.findSegmentIndexBefore(1000));
        assertEquals(2, transcript.findSegmentIndexBefore(5000));
    }

    @Test
    public void findSegmentIndexBefore_timeInGap_returnsPrecedingSegment() {
        assertEquals(1, transcript.findSegmentIndexBefore(3000));
    }

    @Test
    public void findSegmentIndexBefore_timeBeforeFirstSegment_returnsFirst() {
        assertEquals(0, transcript.findSegmentIndexBefore(-10));
    }

    @Test
    public void findSegmentIndexBefore_timeAfterLastSegment_returnsLast() {
        assertEquals(2, transcript.findSegmentIndexBefore(999999));
    }

    @Test
    public void getSegmentAtTime_returnsSegmentContainingTime() {
        assertSame(third, transcript.getSegmentAtTime(5500));
        assertSame(first, transcript.getSegmentAtTime(0));
    }

    @Test
    public void speakers_areStoredAsGiven() {
        assertNull(transcript.getSpeakers());
        Set<String> speakers = Set.of("A", "B");
        transcript.setSpeakers(speakers);
        assertSame(speakers, transcript.getSpeakers());
    }
}
