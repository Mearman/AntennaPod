package de.danoeh.antennapod.model.download;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DownloadStatusTest {

    @Test
    public void constructor_keepsStateAndProgress() {
        DownloadStatus status = new DownloadStatus(DownloadStatus.STATE_RUNNING, 42);
        assertEquals(DownloadStatus.STATE_RUNNING, status.getState());
        assertEquals(42, status.getProgress());
    }
}
