package de.danoeh.antennapod.parser.feed.element.util;

import de.danoeh.antennapod.parser.feed.util.DateUtils;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class DateUtilsFallbackTest {

    @Test
    public void parsingNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> DateUtils.parse(null));
    }

    @Test
    public void parsingUnrecognisedTextGivesNull() {
        assertNull(DateUtils.parse("definitely not a date"));
    }

    @Test
    public void weekdayPrefixIsDroppedWhenNoPatternMatchesWithIt() {
        assertEquals(new Date(1427500800000L), DateUtils.parse("Sat, 2015-03-28"));
    }

    @Test
    public void rfc822DateIsParsedInGmt() {
        assertEquals(new Date(60000), DateUtils.parse("Thu, 01 Jan 1970 00:01:00 +0000"));
    }

    @Test
    public void pastDateIsReturnedByParseOrNullIfFuture() {
        assertEquals(new Date(60000), DateUtils.parseOrNullIfFuture("Thu, 01 Jan 1970 00:01:00 +0000"));
    }

    @Test
    public void futureDateIsDiscardedByParseOrNullIfFuture() {
        assertNull(DateUtils.parseOrNullIfFuture("2999-01-01T00:00:00Z"));
    }

    @Test
    public void unparsableDateIsDiscardedByParseOrNullIfFuture() {
        assertNull(DateUtils.parseOrNullIfFuture("definitely not a date"));
    }

    @Test
    public void timeStringWithHoursMinutesSecondsAndFraction() {
        assertEquals(3723500, DateUtils.parseTimeString("01:02:03.5"));
    }

    @Test
    public void timeStringWithMinutesAndSeconds() {
        assertEquals(123000, DateUtils.parseTimeString("02:03"));
    }

    @Test
    public void timeStringWithNonNumericSegmentThrows() {
        assertThrows(NumberFormatException.class, () -> DateUtils.parseTimeString("aa:bb"));
    }
}
