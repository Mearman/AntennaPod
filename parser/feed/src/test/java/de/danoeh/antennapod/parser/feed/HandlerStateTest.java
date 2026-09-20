package de.danoeh.antennapod.parser.feed;

import org.junit.Before;
import org.junit.Test;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.parser.feed.element.SyndElement;
import de.danoeh.antennapod.parser.feed.namespace.Rss20;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class HandlerStateTest {
    private HandlerState state;
    private SyndElement bottom;
    private SyndElement middle;
    private SyndElement top;

    @Before
    public void setUp() {
        state = new HandlerState(new Feed("http://example.com/feed", null));
        Rss20 namespace = new Rss20();
        bottom = new SyndElement("rss", namespace);
        middle = new SyndElement("channel", namespace);
        top = new SyndElement("item", namespace);
        state.getTagstack().push(bottom);
        state.getTagstack().push(middle);
        state.getTagstack().push(top);
    }

    @Test
    public void secondTagIsTheElementBelowTheTopAndStackIsUnchanged() {
        assertSame(middle, state.getSecondTag());
        assertEquals(3, state.getTagstack().size());
        assertSame(top, state.getTagstack().peek());
    }

    @Test
    public void thirdTagIsTheElementTwoBelowTheTopAndStackIsUnchanged() {
        assertSame(bottom, state.getThirdTag());
        assertEquals(3, state.getTagstack().size());
        assertSame(top, state.getTagstack().pop());
        assertSame(middle, state.getTagstack().pop());
    }

    @Test
    public void alternateFeedUrlsAreStoredByUrlWithTitleAsValue() {
        state.addAlternateFeedUrl("Title", "http://example.com/alt");
        state.addAlternateFeedUrl("Newer title", "http://example.com/alt");
        assertEquals(1, state.alternateUrls.size());
        assertEquals("Newer title", state.alternateUrls.get("http://example.com/alt"));
    }
}
