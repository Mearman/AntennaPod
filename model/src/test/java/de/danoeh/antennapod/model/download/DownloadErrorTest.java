package de.danoeh.antennapod.model.download;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class DownloadErrorTest {

    @Test
    public void fromCode_returnsErrorWithThatCode() {
        for (DownloadError error : DownloadError.values()) {
            assertEquals(error, DownloadError.fromCode(error.getCode()));
        }
    }

    @Test
    public void fromCode_unknownCode_throws() {
        assertThrows(IllegalArgumentException.class, () -> DownloadError.fromCode(-1));
        assertThrows(IllegalArgumentException.class, () -> DownloadError.fromCode(9999));
    }

    @Test
    public void codes_areUnique() {
        long distinct = Arrays.stream(DownloadError.values()).mapToInt(DownloadError::getCode).distinct().count();
        assertEquals(DownloadError.values().length, distinct);
    }

    @Test
    public void successHasCodeZero() {
        assertEquals(0, DownloadError.SUCCESS.getCode());
    }
}
