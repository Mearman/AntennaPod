package de.danoeh.antennapod.parser.feed.element.util;

import de.danoeh.antennapod.parser.feed.util.DurationParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class DurationParserFormatTest {

    @Test
    public void fractionalSecondsWithoutColonsAreConvertedToMillis() {
        assertEquals(1500, DurationParser.inMillis("1.5"));
    }

    @Test
    public void surroundingWhitespaceIsIgnored() {
        assertEquals(90000, DurationParser.inMillis("  1:30  "));
    }

    @Test
    public void tooManySegmentsThrows() {
        assertThrows(NumberFormatException.class, () -> DurationParser.inMillis("1:02:03:04"));
    }

    @Test
    public void nonNumericSegmentThrows() {
        assertThrows(NumberFormatException.class, () -> DurationParser.inMillis("ab:cd"));
    }

    @Test
    public void emptyStringThrows() {
        assertThrows(NumberFormatException.class, () -> DurationParser.inMillis(""));
    }
}
