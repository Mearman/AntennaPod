package de.danoeh.antennapod.parser.feed.element.namespace;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.parser.feed.FeedHandlerResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class ItunesNamespaceTest {
    private FeedHandlerResult result;
    private Feed feed;

    @Before
    public void parseFeed() throws Exception {
        result = FeedParserTestHelper.runFeedHandler(
                FeedParserTestHelper.getFeedFile("feed-rss-testItunesTextFields.xml"));
        feed = result.feed;
    }

    @Test
    public void feedAuthorIsUnescapedFromHtml() {
        assertEquals("Feed & Author", feed.getAuthor());
    }

    @Test
    public void newFeedUrlIsTrimmedAndStoredAsRedirect() {
        assertEquals("http://example.com/moved.xml", result.redirectUrl);
    }

    @Test
    public void channelSubtitleIsFeedDescriptionAndSummaryOutsideChannelDoesNotOverrideIt() {
        assertEquals("Channel subtitle", feed.getDescription());
    }

    @Test
    public void itemSubtitleBecomesItemDescription() {
        assertEquals("Item subtitle", feed.getItems().get(0).getDescription());
    }

    @Test
    public void itemAuthorDoesNotChangeFeedAuthor() {
        assertEquals("Feed & Author", feed.getAuthor());
    }

    @Test
    public void longerSummaryReplacesSubtitle() {
        assertEquals("A considerably longer summary", feed.getItems().get(1).getDescription());
    }

    @Test
    public void shorterSummaryDoesNotReplaceDescription() {
        assertEquals("Existing description that is long", feed.getItems().get(2).getDescription());
    }

    @Test
    public void subtitleDoesNotReplaceExistingItemDescription() {
        assertEquals("Existing", feed.getItems().get(3).getDescription());
    }

    @Test
    public void channelSummaryReplacesChannelDescription() throws Exception {
        Feed summaryFeed = FeedParserTestHelper.runFeedParser(
                FeedParserTestHelper.getFeedFile("feed-rss-testItunesChannelSummary.xml"));
        assertEquals("Summary replaces description", summaryFeed.getDescription());
    }

    @Test
    public void feedWithoutNewFeedUrlHasNoRedirect() throws Exception {
        FeedHandlerResult plain = FeedParserTestHelper.runFeedHandler(
                FeedParserTestHelper.getFeedFile("feed-rss-testItunesChannelSummary.xml"));
        assertNull(plain.redirectUrl);
    }
}
