package de.danoeh.antennapod.parser.feed;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import de.danoeh.antennapod.model.feed.Chapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PodcastIndexChapterParserTest {

    @Test
    public void chapterFieldsAreReadAndStartTimeIsConvertedToMillis() {
        String json = "{\"version\":\"1.2.0\",\"chapters\":["
                + "{\"startTime\":90,\"title\":\"Main\",\"url\":\"https://example.com/main\","
                + "\"img\":\"https://example.com/main.png\"}]}";
        List<Chapter> chapters = PodcastIndexChapterParser.parse(json);
        assertNotNull(chapters);
        assertEquals(1, chapters.size());
        Chapter chapter = chapters.get(0);
        assertEquals(90000, chapter.getStart());
        assertEquals("Main", chapter.getTitle());
        assertEquals("https://example.com/main", chapter.getLink());
        assertEquals("https://example.com/main.png", chapter.getImageUrl());
    }

    @Test
    public void missingFieldsDefaultToZeroStartAndEmptyStrings() {
        List<Chapter> chapters = PodcastIndexChapterParser.parse("{\"chapters\":[{}]}");
        assertNotNull(chapters);
        Chapter chapter = chapters.get(0);
        assertEquals(0, chapter.getStart());
        assertEquals("", chapter.getTitle());
        assertEquals("", chapter.getLink());
        assertEquals("", chapter.getImageUrl());
    }

    @Test
    public void chaptersKeepTheirOrder() {
        List<Chapter> chapters = PodcastIndexChapterParser.parse(
                "{\"chapters\":[{\"startTime\":0,\"title\":\"A\"},{\"startTime\":5,\"title\":\"B\"}]}");
        assertNotNull(chapters);
        assertEquals("A", chapters.get(0).getTitle());
        assertEquals("B", chapters.get(1).getTitle());
    }

    @Test
    public void emptyChapterArrayGivesEmptyList() {
        List<Chapter> chapters = PodcastIndexChapterParser.parse("{\"chapters\":[]}");
        assertNotNull(chapters);
        assertTrue(chapters.isEmpty());
    }

    @Test
    public void invalidJsonGivesNull() {
        assertNull(PodcastIndexChapterParser.parse("this is not json"));
    }

    @Test
    public void jsonWithoutChaptersArrayGivesNull() {
        assertNull(PodcastIndexChapterParser.parse("{\"version\":\"1.2.0\"}"));
    }
}
