package de.danoeh.antennapod.model.download;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DownloadResultTest {

    private static DownloadResult failedResult() {
        return new DownloadResult("Episode", 7, 2, false, DownloadError.ERROR_IO_ERROR, "disk failure");
    }

    @Test
    public void shortConstructor_setsCompletionDateAndZeroId() {
        DownloadResult result = failedResult();
        assertEquals(0, result.getId());
        assertNotNull(result.getCompletionDate());
    }

    @Test
    public void constructor_keepsValues() {
        Date completion = new Date(123456);
        DownloadResult result = new DownloadResult(9, "Title", 11, 2, true, DownloadError.SUCCESS, completion, null);
        assertEquals(9, result.getId());
        assertEquals("Title", result.getTitle());
        assertEquals(11, result.getFeedfileId());
        assertEquals(2, result.getFeedfileType());
        assertTrue(result.isSuccessful());
        assertEquals(DownloadError.SUCCESS, result.getReason());
        assertNull(result.getReasonDetailed());
        assertEquals(completion, result.getCompletionDate());
    }

    @Test
    public void completionDate_isDefensivelyCopiedOnWriteAndRead() {
        Date completion = new Date(1000);
        DownloadResult result = new DownloadResult(1, "t", 1, 1, true, DownloadError.SUCCESS, completion, null);
        completion.setTime(5000);
        assertEquals(1000, result.getCompletionDate().getTime());
        Date returned = result.getCompletionDate();
        returned.setTime(9000);
        assertNotSame(returned, result.getCompletionDate());
        assertEquals(1000, result.getCompletionDate().getTime());
    }

    @Test
    public void setSuccessful_marksSuccessAndSuccessReason() {
        DownloadResult result = failedResult();
        result.setSuccessful();
        assertTrue(result.isSuccessful());
        assertEquals(DownloadError.SUCCESS, result.getReason());
    }

    @Test
    public void setFailed_marksFailureWithReasonAndDetails() {
        DownloadResult result = new DownloadResult("t", 1, 1, true, DownloadError.SUCCESS, null);
        result.setFailed(DownloadError.ERROR_NOT_FOUND, "404");
        assertFalse(result.isSuccessful());
        assertEquals(DownloadError.ERROR_NOT_FOUND, result.getReason());
        assertEquals("404", result.getReasonDetailed());
    }

    @Test
    public void setCancelled_marksFailureWithCancelledReasonAndKeepsDetails() {
        DownloadResult result = failedResult();
        result.setCancelled();
        assertFalse(result.isSuccessful());
        assertEquals(DownloadError.ERROR_DOWNLOAD_CANCELLED, result.getReason());
        assertEquals("disk failure", result.getReasonDetailed());
    }

    @Test
    public void setId_replacesId() {
        DownloadResult result = failedResult();
        result.setId(77);
        assertEquals(77, result.getId());
    }

    @Test
    public void toString_describesResult() {
        DownloadResult result = new DownloadResult(3, "Episode", 7, 2, false, DownloadError.ERROR_UNKNOWN_HOST,
                new Date(0), "no dns");
        String text = result.toString();
        assertTrue(text.contains("id=3"));
        assertTrue(text.contains("title=Episode"));
        assertTrue(text.contains("reason=ERROR_UNKNOWN_HOST"));
        assertTrue(text.contains("reasonDetailed=no dns"));
        assertTrue(text.contains("successful=false"));
        assertTrue(text.contains("feedfileId=7"));
        assertTrue(text.contains("feedfileType=2"));
    }
}
