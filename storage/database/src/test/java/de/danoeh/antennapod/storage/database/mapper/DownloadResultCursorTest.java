package de.danoeh.antennapod.storage.database.mapper;

import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
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
public class DownloadResultCursorTest {

    private CursorRow resultRow() {
        return new CursorRow()
                .with(PodDBAdapter.KEY_ID, 8L)
                .with(PodDBAdapter.KEY_DOWNLOADSTATUS_TITLE, "Episode 1")
                .with(PodDBAdapter.KEY_FEEDFILE, 33L)
                .with(PodDBAdapter.KEY_FEEDFILETYPE, 2)
                .with(PodDBAdapter.KEY_SUCCESSFUL, 0)
                .with(PodDBAdapter.KEY_REASON, DownloadError.ERROR_NOT_FOUND.getCode())
                .with(PodDBAdapter.KEY_COMPLETION_DATE, 1_700_000_000_000L)
                .with(PodDBAdapter.KEY_REASON_DETAILED, "404 from server");
    }

    @Test
    public void failedDownloadRowIsConvertedFieldByField() {
        DownloadResult result = new DownloadResultCursor(resultRow().build()).getDownloadResult();

        assertEquals(8L, result.getId());
        assertEquals("Episode 1", result.getTitle());
        assertEquals(33L, result.getFeedfileId());
        assertEquals(2, result.getFeedfileType());
        assertFalse(result.isSuccessful());
        assertEquals(DownloadError.ERROR_NOT_FOUND, result.getReason());
        assertEquals(1_700_000_000_000L, result.getCompletionDate().getTime());
        assertEquals("404 from server", result.getReasonDetailed());
    }

    @Test
    public void positiveSuccessfulColumnMeansSuccess() {
        DownloadResult result = new DownloadResultCursor(resultRow()
                .with(PodDBAdapter.KEY_SUCCESSFUL, 1)
                .with(PodDBAdapter.KEY_REASON, DownloadError.SUCCESS.getCode())
                .with(PodDBAdapter.KEY_REASON_DETAILED, null)
                .build()).getDownloadResult();

        assertTrue(result.isSuccessful());
        assertEquals(DownloadError.SUCCESS, result.getReason());
        assertNull(result.getReasonDetailed());
    }

    @Test
    public void unknownReasonCodeIsRejected() {
        DownloadResultCursor cursor = new DownloadResultCursor(resultRow().with(PodDBAdapter.KEY_REASON, 9999).build());
        assertThrows(IllegalArgumentException.class, cursor::getDownloadResult);
    }

    @Test
    public void missingColumnIsRejectedWhenCursorIsWrapped() {
        CursorRow incomplete = new CursorRow().with(PodDBAdapter.KEY_ID, 1L);
        assertThrows(IllegalArgumentException.class, () -> new DownloadResultCursor(incomplete.build()));
    }
}
