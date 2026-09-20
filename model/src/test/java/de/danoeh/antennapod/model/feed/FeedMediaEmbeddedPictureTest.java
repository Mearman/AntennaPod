package de.danoeh.antennapod.model.feed;

import de.danoeh.antennapod.model.MediaMetadataRetrieverCompat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class FeedMediaEmbeddedPictureTest {
    private FeedMedia media;

    @Before
    public void setUp() {
        media = new FeedMedia(null, "http://example.com/episode.mp3", 0, "audio/mpeg");
    }

    private void makeDownloaded() {
        media.setDownloaded(true, 1000);
        media.setLocalFileUrl("/storage/episode.mp3");
    }

    @Test
    public void hasEmbeddedPicture_withoutLocalFileIsFalseWithoutInspectingAnyFile() {
        try (MockedConstruction<MediaMetadataRetrieverCompat> retrievers =
                     mockConstruction(MediaMetadataRetrieverCompat.class)) {
            assertFalse(media.hasEmbeddedPicture());
            assertTrue(retrievers.constructed().isEmpty());
        }
    }

    @Test
    public void hasEmbeddedPicture_fileWithCoverArtIsTrue() {
        makeDownloaded();
        try (MockedConstruction<MediaMetadataRetrieverCompat> retrievers = mockConstruction(
                MediaMetadataRetrieverCompat.class,
                (retriever, context) -> when(retriever.getEmbeddedPicture()).thenReturn(new byte[] {1, 2, 3}))) {
            assertTrue(media.hasEmbeddedPicture());
            verify(retrievers.constructed().get(0)).setDataSource("/storage/episode.mp3");
        }
    }

    @Test
    public void hasEmbeddedPicture_fileWithoutCoverArtIsFalse() {
        makeDownloaded();
        try (MockedConstruction<MediaMetadataRetrieverCompat> retrievers = mockConstruction(
                MediaMetadataRetrieverCompat.class,
                (retriever, context) -> when(retriever.getEmbeddedPicture()).thenReturn(null))) {
            assertFalse(media.hasEmbeddedPicture());
            verify(retrievers.constructed().get(0)).close();
        }
    }

    @Test
    public void hasEmbeddedPicture_unreadableFileIsFalse() {
        makeDownloaded();
        try (MockedConstruction<MediaMetadataRetrieverCompat> retrievers = mockConstruction(
                MediaMetadataRetrieverCompat.class,
                (retriever, context) -> doThrow(new RuntimeException("cannot read")).when(retriever)
                        .setDataSource("/storage/episode.mp3"))) {
            assertFalse(media.hasEmbeddedPicture());
            verify(retrievers.constructed().get(0)).close();
        }
    }

    @Test
    public void hasEmbeddedPicture_resultIsCachedAfterFirstCheck() {
        makeDownloaded();
        try (MockedConstruction<MediaMetadataRetrieverCompat> retrievers = mockConstruction(
                MediaMetadataRetrieverCompat.class,
                (retriever, context) -> when(retriever.getEmbeddedPicture()).thenReturn(new byte[] {1}))) {
            assertTrue(media.hasEmbeddedPicture());
            assertTrue(media.hasEmbeddedPicture());
            assertEquals(1, retrievers.constructed().size());
        }
    }

    @Test
    public void checkEmbeddedPicture_withoutLocalFileResetsKnownPicture() {
        makeDownloaded();
        media.setHasEmbeddedPicture(true);
        media.setLocalFileUrl(null);
        media.checkEmbeddedPicture();
        assertFalse(media.hasEmbeddedPicture());
    }
}
