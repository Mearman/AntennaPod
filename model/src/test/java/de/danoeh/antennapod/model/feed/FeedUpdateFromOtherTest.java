package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FeedUpdateFromOtherTest {
    private Feed original;
    private Feed other;

    @Before
    public void setUp() {
        original = FeedMother.anyFeed();
        other = new Feed("http://example.com/other.xml", null);
    }

    @Test
    public void nullFieldsOnOther_keepOriginalValues() {
        original.updateFromOther(other);
        assertEquals("title", original.getFeedTitle());
        assertEquals("http://example.com/feed", original.getFeedIdentifier());
        assertEquals("http://example.com", original.getLink());
        assertEquals("This is the description", original.getDescription());
        assertEquals("en", original.getLanguage());
        assertEquals("Daniel", original.getAuthor());
        assertEquals(FeedMother.IMAGE_URL, original.getImageUrl());
    }

    @Test
    public void presentFieldsOnOther_replaceOriginalValues() {
        other.setTitle("New title");
        other.setFeedIdentifier("new-id");
        other.setLink("http://example.com/new");
        other.setDescription("New description");
        other.setLanguage("de");
        other.setAuthor("Someone else");

        original.updateFromOther(other);

        assertEquals("New title", original.getFeedTitle());
        assertEquals("new-id", original.getFeedIdentifier());
        assertEquals("http://example.com/new", original.getLink());
        assertEquals("New description", original.getDescription());
        assertEquals("de", original.getLanguage());
        assertEquals("Someone else", original.getAuthor());
    }

    @Test
    public void downloadUrlIsNeverUpdated() {
        original.updateFromOther(other);
        assertEquals("http://example.com/feed", original.getDownloadUrl());
    }

    @Test
    public void fundingList_replacedOnlyWhenOtherHasOne() {
        original.addPayment(new FeedFunding("http://example.com/pay", "Pay"));
        ArrayList<FeedFunding> originalFunding = original.getPaymentLinks();
        original.updateFromOther(other);
        assertSame(originalFunding, original.getPaymentLinks());

        other.addPayment(new FeedFunding("http://example.com/new-pay", "New"));
        ArrayList<FeedFunding> otherFunding = other.getPaymentLinks();
        original.updateFromOther(other);
        assertSame(otherFunding, original.getPaymentLinks());
    }

    @Test
    public void lastRefreshAttempt_onlyMovesForward() {
        original.setLastRefreshAttempt(5000);
        other.setLastRefreshAttempt(3000);
        original.updateFromOther(other);
        assertEquals(5000, original.getLastRefreshAttempt());

        other.setLastRefreshAttempt(8000);
        original.updateFromOther(other);
        assertEquals(8000, original.getLastRefreshAttempt());
    }

    @Test
    public void pagedState_adoptedOnlyWhenOriginalIsNotPaged() {
        other.setPaged(true);
        other.setNextPageLink("http://example.com/page2");
        original.updateFromOther(other);
        assertTrue(original.isPaged());
        assertEquals("http://example.com/page2", original.getNextPageLink());
    }

    @Test
    public void pagedState_originalNextPageIsNotOverwritten() {
        original.setPaged(true);
        original.setNextPageLink("http://example.com/page3");
        other.setPaged(true);
        other.setNextPageLink("http://example.com/page2");
        original.updateFromOther(other);
        assertEquals("http://example.com/page3", original.getNextPageLink());
    }

    @Test
    public void pagedState_notPagedOtherLeavesOriginalUnpaged() {
        original.updateFromOther(other);
        assertFalse(original.isPaged());
    }

    @Test
    public void addPayment_createsListOnDemandAndAppends() {
        Feed feed = new Feed("http://example.com/feed.xml", null);
        assertNull(feed.getPaymentLinks());
        feed.addPayment(new FeedFunding("a", "A"));
        feed.addPayment(new FeedFunding("b", "B"));
        assertEquals(Arrays.asList(new FeedFunding("a", "A"), new FeedFunding("b", "B")), feed.getPaymentLinks());
    }
}
