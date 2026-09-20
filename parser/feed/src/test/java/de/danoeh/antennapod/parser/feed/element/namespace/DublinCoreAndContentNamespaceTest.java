package de.danoeh.antennapod.parser.feed.element.namespace;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import de.danoeh.antennapod.model.feed.Feed;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class DublinCoreAndContentNamespaceTest {
    private Feed feed;

    @Before
    public void parseFeed() throws Exception {
        feed = FeedParserTestHelper.runFeedParser(
                FeedParserTestHelper.getFeedFile("feed-rss-testDublinCoreAndContent.xml"));
    }

    @Test
    public void dublinCoreDateOverridesEarlierPubDate() {
        assertEquals(new Date(5 * 60000), feed.getItems().get(0).getPubDate());
    }

    @Test
    public void longerEncodedContentReplacesDescription() {
        assertEquals("Much longer encoded content", feed.getItems().get(0).getDescription());
    }

    @Test
    public void shorterEncodedContentKeepsDescription() {
        assertEquals("A description that is quite long indeed", feed.getItems().get(1).getDescription());
    }
}
