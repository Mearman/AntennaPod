package de.danoeh.antennapod.parser.feed.element.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.parser.feed.UnsupportedFeedtypeException;
import de.danoeh.antennapod.parser.feed.util.TypeGetter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
public class TypeGetterTest {

    private Feed feedFor(String resourceName) {
        File file = new File(getClass().getClassLoader().getResource(resourceName).getFile());
        Feed feed = new Feed("http://example.com/feed", null);
        feed.setLocalFileUrl(file.getAbsolutePath());
        return feed;
    }

    @Test
    public void rss2VersionIsDetectedAndStoredOnFeed() throws Exception {
        Feed feed = feedFor("feed-rss-testRss2Basic.xml");
        assertEquals(TypeGetter.Type.RSS20, new TypeGetter().getType(feed));
        assertEquals(Feed.TYPE_RSS2, feed.getType());
    }

    @Test
    public void rssWithoutVersionIsAssumedToBeRss2() throws Exception {
        Feed feed = feedFor("feed-rss-testRssWithoutVersion.xml");
        assertEquals(TypeGetter.Type.RSS20, new TypeGetter().getType(feed));
        assertEquals(Feed.TYPE_RSS2, feed.getType());
    }

    @Test
    public void rss091IsDetectedWithoutChangingFeedType() throws Exception {
        Feed feed = feedFor("feed-rss-testRss091.xml");
        assertEquals(TypeGetter.Type.RSS091, new TypeGetter().getType(feed));
        assertNull(feed.getType());
    }

    @Test
    public void unsupportedRssVersionIsRejected() {
        Feed feed = feedFor("feed-rss-testRssVersion1.xml");
        UnsupportedFeedtypeException exception =
                assertThrows(UnsupportedFeedtypeException.class, () -> new TypeGetter().getType(feed));
        assertEquals("Unsupported rss version", exception.getMessage());
    }

    @Test
    public void atomIsDetectedAndStoredOnFeed() throws Exception {
        Feed feed = feedFor("feed-atom-testAtomBasic.xml");
        assertEquals(TypeGetter.Type.ATOM, new TypeGetter().getType(feed));
        assertEquals(Feed.TYPE_ATOM1, feed.getType());
    }

    @Test
    public void atomXmlLangAttributeSetsFeedLanguage() throws Exception {
        Feed feed = feedFor("feed-atom-testXmlLang.xml");
        new TypeGetter().getType(feed);
        assertEquals("fr-FR", feed.getLanguage());
    }

    @Test
    public void atomWithoutXmlLangLeavesLanguageUnset() throws Exception {
        Feed feed = feedFor("feed-atom-testAtomBasic.xml");
        new TypeGetter().getType(feed);
        assertNull(feed.getLanguage());
    }

    @Test
    public void websiteWithTitleReportsTitleInException() {
        Feed feed = feedFor("feed-html-testWebsiteWithTitle.html");
        UnsupportedFeedtypeException exception =
                assertThrows(UnsupportedFeedtypeException.class, () -> new TypeGetter().getType(feed));
        assertEquals("html", exception.getRootElement());
        assertEquals("Website title: \"Example Site\"", exception.getMessage());
    }

    @Test
    public void websiteWithoutTitleReportsRootElement() {
        Feed feed = feedFor("feed-html-testWebsiteWithoutTitle.html");
        UnsupportedFeedtypeException exception =
                assertThrows(UnsupportedFeedtypeException.class, () -> new TypeGetter().getType(feed));
        assertEquals("html", exception.getRootElement());
        assertEquals("Server returned html", exception.getMessage());
    }

    @Test
    public void unknownXmlRootIsRejected() {
        Feed feed = feedFor("feed-xml-testUnknownRoot.xml");
        assertThrows(UnsupportedFeedtypeException.class, () -> new TypeGetter().getType(feed));
    }

    @Test
    public void plainTextIsRejected() {
        Feed feed = feedFor("feed-txt-testNotXml.txt");
        assertThrows(UnsupportedFeedtypeException.class, () -> new TypeGetter().getType(feed));
    }

    @Test
    public void emptyFileIsRejectedAsUnknownProblem() {
        Feed feed = feedFor("feed-xml-testEmpty.xml");
        UnsupportedFeedtypeException exception =
                assertThrows(UnsupportedFeedtypeException.class, () -> new TypeGetter().getType(feed));
        assertEquals("Unknown problem when trying to determine feed type", exception.getMessage());
    }

    @Test
    public void feedWithoutLocalFileIsRejectedAsUnknownProblem() {
        Feed feed = new Feed("http://example.com/feed", null);
        UnsupportedFeedtypeException exception =
                assertThrows(UnsupportedFeedtypeException.class, () -> new TypeGetter().getType(feed));
        assertEquals("Unknown problem when trying to determine feed type", exception.getMessage());
    }
}
