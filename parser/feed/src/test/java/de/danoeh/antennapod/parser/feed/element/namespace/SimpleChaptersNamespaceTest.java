package de.danoeh.antennapod.parser.feed.element.namespace;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class SimpleChaptersNamespaceTest {

    @Test
    public void chaptersWithValidStartAreParsedInOrder() throws Exception {
        Feed feed = FeedParserTestHelper.runFeedParser(
                FeedParserTestHelper.getFeedFile("feed-rss-testSimpleChaptersPsc.xml"));
        List<Chapter> chapters = feed.getItems().get(0).getChapters();
        assertEquals(3, chapters.size());
        assertEquals("Intro", chapters.get(0).getTitle());
        assertEquals(0, chapters.get(0).getStart());
        assertEquals("Main", chapters.get(1).getTitle());
        assertEquals(70500, chapters.get(1).getStart());
        assertEquals("http://example.com/main", chapters.get(1).getLink());
        assertEquals("http://example.com/main.png", chapters.get(1).getImageUrl());
        assertEquals("Hour", chapters.get(2).getTitle());
        assertEquals(3723000, chapters.get(2).getStart());
    }

    @Test
    public void chapterWithoutLinkAndImageHasNoLinkOrImage() throws Exception {
        Feed feed = FeedParserTestHelper.runFeedParser(
                FeedParserTestHelper.getFeedFile("feed-rss-testSimpleChaptersPsc.xml"));
        Chapter intro = feed.getItems().get(0).getChapters().get(0);
        assertNull(intro.getLink());
        assertNull(intro.getImageUrl());
    }

    @Test
    public void shortNamespacePrefixIsRecognised() throws Exception {
        Feed feed = FeedParserTestHelper.runFeedParser(
                FeedParserTestHelper.getFeedFile("feed-rss-testSimpleChaptersSc.xml"));
        List<Chapter> chapters = feed.getItems().get(0).getChapters();
        assertEquals(1, chapters.size());
        assertEquals("Short prefix", chapters.get(0).getTitle());
        assertEquals(30000, chapters.get(0).getStart());
    }
}
