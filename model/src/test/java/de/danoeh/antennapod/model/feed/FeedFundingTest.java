package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FeedFundingTest {
    private static final String ENTRY = FeedFunding.FUNDING_ENTRIES_SEPARATOR;
    private static final String TITLE = FeedFunding.FUNDING_TITLE_SEPARATOR;

    @Test
    public void extractPaymentLinks_blankInput_returnsNull() {
        assertNull(FeedFunding.extractPaymentLinks(null));
        assertNull(FeedFunding.extractPaymentLinks(""));
        assertNull(FeedFunding.extractPaymentLinks("   "));
    }

    @Test
    public void extractPaymentLinks_legacyPlainUrl_returnsSingleFundingWithEmptyContent() {
        ArrayList<FeedFunding> funding = FeedFunding.extractPaymentLinks("http://example.com/donate");
        assertEquals(1, funding.size());
        assertEquals("http://example.com/donate", funding.get(0).url);
        assertEquals("", funding.get(0).content);
    }

    @Test
    public void extractPaymentLinks_multipleEntries_returnsUrlAndTitleOfEach() {
        String serialised = "http://a.example" + TITLE + "Support A" + ENTRY + "http://b.example" + TITLE + "Support B";
        ArrayList<FeedFunding> funding = FeedFunding.extractPaymentLinks(serialised);
        assertEquals(Arrays.asList(new FeedFunding("http://a.example", "Support A"),
                new FeedFunding("http://b.example", "Support B")), funding);
    }

    @Test
    public void extractPaymentLinks_entryWithoutTitle_usesEmptyContent() {
        ArrayList<FeedFunding> funding = FeedFunding.extractPaymentLinks("http://a.example" + TITLE + ENTRY
                + "http://b.example" + TITLE + "  ");
        assertEquals(2, funding.size());
        assertEquals("", funding.get(0).content);
        assertEquals("", funding.get(1).content);
    }

    @Test
    public void extractPaymentLinks_entryWithBlankUrl_isSkipped() {
        ArrayList<FeedFunding> funding = FeedFunding.extractPaymentLinks(" " + TITLE + "Ignored" + ENTRY
                + "http://b.example" + TITLE + "Kept");
        assertEquals(1, funding.size());
        assertEquals("http://b.example", funding.get(0).url);
    }

    @Test
    public void extractPaymentLinks_onlySeparator_returnsNull() {
        assertNull(FeedFunding.extractPaymentLinks(ENTRY));
    }

    @Test
    public void getPaymentLinksAsString_null_returnsNull() {
        assertNull(FeedFunding.getPaymentLinksAsString(null));
    }

    @Test
    public void getPaymentLinksAsString_emptyList_returnsEmptyString() {
        assertEquals("", FeedFunding.getPaymentLinksAsString(new ArrayList<>()));
    }

    @Test
    public void getPaymentLinksAsString_joinsEntriesWithoutTrailingSeparator() {
        ArrayList<FeedFunding> funding = new ArrayList<>(Arrays.asList(
                new FeedFunding("http://a.example", "Support A"), new FeedFunding("http://b.example", "Support B")));
        assertEquals("http://a.example" + TITLE + "Support A" + ENTRY + "http://b.example" + TITLE + "Support B",
                FeedFunding.getPaymentLinksAsString(funding));
    }

    @Test
    public void serialisation_roundTrips() {
        ArrayList<FeedFunding> funding = new ArrayList<>(Arrays.asList(
                new FeedFunding("http://a.example", "Support A"), new FeedFunding("http://b.example", "Support B")));
        String serialised = FeedFunding.getPaymentLinksAsString(funding);
        assertEquals(funding, FeedFunding.extractPaymentLinks(serialised));
    }

    @Test
    public void equals_requiresSameUrlAndContent() {
        FeedFunding funding = new FeedFunding("http://a.example", "Support");
        assertEquals(funding, new FeedFunding("http://a.example", "Support"));
        assertEquals(funding.hashCode(), new FeedFunding("http://a.example", "Support").hashCode());
        assertNotEquals(funding, new FeedFunding("http://a.example", "Other"));
        assertNotEquals(funding, new FeedFunding("http://b.example", "Support"));
        assertNotEquals(funding, null);
        assertNotEquals(funding, "http://a.example");
    }

    @Test
    public void equals_bothFieldsNull_areEqual() {
        assertTrue(new FeedFunding(null, null).equals(new FeedFunding(null, null)));
        assertNotEquals(new FeedFunding(null, null), new FeedFunding("http://a.example", null));
    }

    @Test
    public void setters_replaceValues() {
        FeedFunding funding = new FeedFunding("http://a.example", "Support");
        funding.setUrl("http://b.example");
        funding.setContent("New");
        assertEquals("http://b.example", funding.url);
        assertEquals("New", funding.content);
    }
}
