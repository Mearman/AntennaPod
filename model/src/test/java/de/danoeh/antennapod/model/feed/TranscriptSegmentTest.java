package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TranscriptSegmentTest {

    @Test
    public void constructor_keepsValues() {
        TranscriptSegment segment = new TranscriptSegment(100, 200, "hello", "Alice");
        assertEquals(100, segment.getStartTime());
        assertEquals(200, segment.getEndTime());
        assertEquals("hello", segment.getWords());
        assertEquals("Alice", segment.getSpeaker());
    }

    @Test
    public void append_extendsEndTimeAndJoinsWordsWithSpace() {
        TranscriptSegment segment = new TranscriptSegment(100, 200, "hello", "Alice");
        segment.append(350, "world");
        assertEquals(100, segment.getStartTime());
        assertEquals(350, segment.getEndTime());
        assertEquals("hello world", segment.getWords());
        assertEquals("Alice", segment.getSpeaker());
    }

    @Test
    public void append_multipleTimes_accumulatesWords() {
        TranscriptSegment segment = new TranscriptSegment(0, 10, "a", null);
        segment.append(20, "b");
        segment.append(30, "c");
        assertEquals("a b c", segment.getWords());
        assertEquals(30, segment.getEndTime());
    }
}
