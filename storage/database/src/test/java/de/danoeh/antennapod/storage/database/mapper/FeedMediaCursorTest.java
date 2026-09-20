package de.danoeh.antennapod.storage.database.mapper;

import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedMediaCursorTest {

    private CursorRow mediaRow() {
        return new CursorRow()
                .with(PodDBAdapter.SELECT_KEY_MEDIA_ID, 12L)
                .with(PodDBAdapter.KEY_LAST_PLAYED_TIME_HISTORY, 1_700_000_000_000L)
                .with(PodDBAdapter.KEY_DURATION, 3_600_000)
                .with(PodDBAdapter.KEY_POSITION, 1_500)
                .with(PodDBAdapter.KEY_SIZE, 123_456_789L)
                .with(PodDBAdapter.KEY_MIME_TYPE, "audio/mpeg")
                .with(PodDBAdapter.KEY_FILE_URL, "/media/episode.mp3")
                .with(PodDBAdapter.KEY_DOWNLOAD_URL, "https://example.com/episode.mp3")
                .with(PodDBAdapter.KEY_DOWNLOAD_DATE, 1_650_000_000_000L)
                .with(PodDBAdapter.KEY_PLAYED_DURATION, 2_000)
                .with(PodDBAdapter.KEY_LAST_PLAYED_TIME_STATISTICS, 1_600_000_000_000L)
                .with(PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE, 1);
    }

    @Test
    public void rowIsConvertedFieldByField() {
        FeedMedia media = new FeedMediaCursor(mediaRow().build()).getFeedMedia();

        assertEquals(12L, media.getId());
        assertEquals(3_600_000, media.getDuration());
        assertEquals(1_500, media.getPosition());
        assertEquals(123_456_789L, media.getSize());
        assertEquals("audio/mpeg", media.getMimeType());
        assertEquals("/media/episode.mp3", media.getLocalFileUrl());
        assertEquals("https://example.com/episode.mp3", media.getDownloadUrl());
        assertEquals(2_000, media.getPlayedDuration());
        assertEquals(1_600_000_000_000L, media.getLastPlayedTimeStatistics());
        assertEquals(1_700_000_000_000L, media.getLastPlayedTimeHistory().getTime());
        assertTrue(media.isDownloaded());
    }

    @Test
    public void convertedMediaIsNotAttachedToAnItem() {
        assertNull(new FeedMediaCursor(mediaRow().build()).getFeedMedia().getItem());
    }

    @Test
    public void nonPositiveHistoryTimeMeansNeverPlayedToCompletion() {
        FeedMedia media = new FeedMediaCursor(
                mediaRow().with(PodDBAdapter.KEY_LAST_PLAYED_TIME_HISTORY, 0L).build()).getFeedMedia();
        assertNull(media.getLastPlayedTimeHistory());
    }

    @Test
    public void missingDownloadDateMeansNotDownloaded() {
        FeedMedia media = new FeedMediaCursor(mediaRow().with(PodDBAdapter.KEY_DOWNLOAD_DATE, 0L).build())
                .getFeedMedia();
        assertFalse(media.isDownloaded());
    }

    @Test
    public void embeddedPictureFlagIsReadFromIntegerColumn() {
        FeedMedia withPicture = new FeedMediaCursor(
                mediaRow().with(PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE, 1).build()).getFeedMedia();
        FeedMedia withoutPicture = new FeedMediaCursor(
                mediaRow().with(PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE, 0).build()).getFeedMedia();
        assertTrue(withPicture.hasEmbeddedPicture());
        assertFalse(withoutPicture.hasEmbeddedPicture());
    }

    @Test
    public void unknownEmbeddedPictureStateIsResolvedLazilyAndFindsNoPictureWithoutLocalFile() {
        FeedMedia media = new FeedMediaCursor(mediaRow()
                .with(PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE, -1)
                .with(PodDBAdapter.KEY_FILE_URL, null)
                .build()).getFeedMedia();
        assertFalse(media.hasEmbeddedPicture());
    }

    @Test
    public void nullTextColumnsStayNull() {
        FeedMedia media = new FeedMediaCursor(mediaRow()
                .with(PodDBAdapter.KEY_MIME_TYPE, null)
                .with(PodDBAdapter.KEY_FILE_URL, null)
                .build()).getFeedMedia();
        assertNull(media.getMimeType());
        assertNull(media.getLocalFileUrl());
    }

    @Test
    public void missingColumnIsRejectedWhenCursorIsWrapped() {
        CursorRow incomplete = new CursorRow().with(PodDBAdapter.SELECT_KEY_MEDIA_ID, 1L);
        assertThrows(IllegalArgumentException.class, () -> new FeedMediaCursor(incomplete.build()));
    }
}
