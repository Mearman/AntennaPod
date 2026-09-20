package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ChapterTest {

    private static List<Chapter> chaptersStartingAt(long... starts) {
        Chapter[] chapters = new Chapter[starts.length];
        for (int i = 0; i < starts.length; i++) {
            chapters[i] = new Chapter(starts[i], "Chapter " + i, null, null);
        }
        return Arrays.asList(chapters);
    }

    @Test
    public void getAfterPosition_nullOrEmptyList_returnsMinusOne() {
        assertEquals(-1, Chapter.getAfterPosition(null, 100));
        assertEquals(-1, Chapter.getAfterPosition(Collections.emptyList(), 100));
    }

    @Test
    public void getAfterPosition_positionBeforeFirstChapter_returnsMinusOne() {
        assertEquals(-1, Chapter.getAfterPosition(chaptersStartingAt(1000, 2000), 500));
    }

    @Test
    public void getAfterPosition_positionInsideChapter_returnsThatChapterIndex() {
        List<Chapter> chapters = chaptersStartingAt(0, 1000, 2000);
        assertEquals(0, Chapter.getAfterPosition(chapters, 500));
        assertEquals(1, Chapter.getAfterPosition(chapters, 1500));
    }

    @Test
    public void getAfterPosition_positionExactlyAtChapterStart_returnsThatChapterIndex() {
        List<Chapter> chapters = chaptersStartingAt(0, 1000, 2000);
        assertEquals(1, Chapter.getAfterPosition(chapters, 1000));
        assertEquals(2, Chapter.getAfterPosition(chapters, 2000));
    }

    @Test
    public void getAfterPosition_positionAfterLastChapterStart_returnsLastIndex() {
        assertEquals(2, Chapter.getAfterPosition(chaptersStartingAt(0, 1000, 2000), 999999));
    }

    @Test
    public void constructor_keepsValues() {
        Chapter chapter = new Chapter(1500, "Intro", "http://example.com/link", "http://example.com/image.png");
        assertEquals(1500, chapter.getStart());
        assertEquals("Intro", chapter.getTitle());
        assertEquals("http://example.com/link", chapter.getLink());
        assertEquals("http://example.com/image.png", chapter.getImageUrl());
    }

    @Test
    public void setters_replaceValues() {
        Chapter chapter = new Chapter();
        chapter.setStart(10);
        chapter.setTitle("Title");
        chapter.setLink("link");
        chapter.setImageUrl("image");
        chapter.setChapterId("source-id");
        chapter.setId(5);
        assertEquals(10, chapter.getStart());
        assertEquals("Title", chapter.getTitle());
        assertEquals("link", chapter.getLink());
        assertEquals("image", chapter.getImageUrl());
        assertEquals("source-id", chapter.getChapterId());
        assertEquals(5, chapter.getId());
    }

    @Test
    public void equals_comparesDatabaseIdOnly() {
        Chapter first = new Chapter(0, "A", null, null);
        Chapter second = new Chapter(5000, "B", "link", "image");
        first.setId(3);
        second.setId(3);
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        second.setId(4);
        assertNotEquals(first, second);
        assertNotEquals(first, null);
        assertNotEquals(first, "chapter");
    }

    @Test
    public void toString_containsTitleStartAndLink() {
        Chapter chapter = new Chapter(1500, "Intro", "http://example.com/link", null);
        assertEquals("Chapter [title=Intro, start=1500, url=http://example.com/link]", chapter.toString());
    }
}
