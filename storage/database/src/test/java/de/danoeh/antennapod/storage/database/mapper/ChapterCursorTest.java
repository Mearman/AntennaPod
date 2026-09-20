package de.danoeh.antennapod.storage.database.mapper;

import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RunWith(RobolectricTestRunner.class)
public class ChapterCursorTest {

    private CursorRow chapterRow() {
        return new CursorRow()
                .with(PodDBAdapter.KEY_ID, 4L)
                .with(PodDBAdapter.KEY_TITLE, "Introduction")
                .with(PodDBAdapter.KEY_START, 45_000L)
                .with(PodDBAdapter.KEY_LINK, "https://example.com/intro")
                .with(PodDBAdapter.KEY_IMAGE_URL, "https://example.com/intro.png");
    }

    @Test
    public void rowIsConvertedFieldByField() {
        Chapter chapter = new ChapterCursor(chapterRow().build()).getChapter();

        assertEquals(4L, chapter.getId());
        assertEquals("Introduction", chapter.getTitle());
        assertEquals(45_000L, chapter.getStart());
        assertEquals("https://example.com/intro", chapter.getLink());
        assertEquals("https://example.com/intro.png", chapter.getImageUrl());
    }

    @Test
    public void optionalTextColumnsMayBeNull() {
        Chapter chapter = new ChapterCursor(chapterRow()
                .with(PodDBAdapter.KEY_LINK, null)
                .with(PodDBAdapter.KEY_IMAGE_URL, null)
                .build()).getChapter();
        assertNull(chapter.getLink());
        assertNull(chapter.getImageUrl());
    }

    @Test
    public void missingColumnIsRejectedWhenCursorIsWrapped() {
        CursorRow incomplete = new CursorRow().with(PodDBAdapter.KEY_ID, 1L);
        assertThrows(IllegalArgumentException.class, () -> new ChapterCursor(incomplete.build()));
    }
}
