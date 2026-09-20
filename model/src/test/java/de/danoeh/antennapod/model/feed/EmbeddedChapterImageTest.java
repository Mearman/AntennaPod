package de.danoeh.antennapod.model.feed;

import de.danoeh.antennapod.model.playback.Playable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class EmbeddedChapterImageTest {

    @Test
    public void makeUrl_encodesPositionAndLength() {
        assertEquals("embedded-image://12/345", EmbeddedChapterImage.makeUrl(12, 345));
    }

    @Test
    public void constructor_parsesPositionAndLengthFromUrl() {
        Playable media = mock(Playable.class);
        EmbeddedChapterImage image = new EmbeddedChapterImage(media, EmbeddedChapterImage.makeUrl(12, 345));
        assertEquals(12, image.getPosition());
        assertEquals(345, image.getLength());
        assertSame(media, image.getMedia());
    }

    @Test
    public void constructor_nonEmbeddedUrl_throws() {
        Playable media = mock(Playable.class);
        assertThrows(IllegalArgumentException.class,
                () -> new EmbeddedChapterImage(media, "http://example.com/image.png"));
    }

    @Test
    public void equals_comparesImageUrlOnly() {
        EmbeddedChapterImage a = new EmbeddedChapterImage(mock(Playable.class), EmbeddedChapterImage.makeUrl(1, 2));
        EmbeddedChapterImage sameUrl = new EmbeddedChapterImage(mock(Playable.class),
                EmbeddedChapterImage.makeUrl(1, 2));
        EmbeddedChapterImage otherUrl = new EmbeddedChapterImage(mock(Playable.class),
                EmbeddedChapterImage.makeUrl(1, 3));
        assertEquals(a, a);
        assertEquals(a, sameUrl);
        assertEquals(a.hashCode(), sameUrl.hashCode());
        assertNotEquals(a, otherUrl);
        assertNotEquals(a, null);
        assertNotEquals(a, "embedded-image://1/2");
    }

    @Test
    public void getModelFor_embeddedImageUrl_returnsEmbeddedChapterImage() {
        Playable media = mediaWithChapterImage(EmbeddedChapterImage.makeUrl(7, 99));
        Object model = EmbeddedChapterImage.getModelFor(media, 0);
        assertTrue(model instanceof EmbeddedChapterImage);
        assertEquals(7, ((EmbeddedChapterImage) model).getPosition());
        assertEquals(99, ((EmbeddedChapterImage) model).getLength());
    }

    @Test
    public void getModelFor_remoteImageUrl_returnsUrlString() {
        Playable media = mediaWithChapterImage("http://example.com/chapter.png");
        Object model = EmbeddedChapterImage.getModelFor(media, 0);
        assertFalse(model instanceof EmbeddedChapterImage);
        assertEquals("http://example.com/chapter.png", model);
    }

    private Playable mediaWithChapterImage(String imageUrl) {
        Playable media = mock(Playable.class);
        Chapter chapter = new Chapter(0, "Title", null, imageUrl);
        when(media.getChapters()).thenReturn(Collections.singletonList(chapter));
        return media;
    }
}
