package de.danoeh.antennapod.net.download.serviceinterface;

import de.danoeh.antennapod.model.download.DownloadStatus;
import org.junit.After;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DownloadServiceInterfaceTest {
    private static final String URL = "http://example.com/episode.mp3";

    private final DownloadServiceInterface downloads = new DownloadServiceInterfaceStub();

    @After
    public void tearDown() {
        DownloadServiceInterface.setImpl(null);
    }

    private void setDownload(String url, int state, int progress) {
        Map<String, DownloadStatus> current = new HashMap<>();
        current.put(url, new DownloadStatus(state, progress));
        downloads.setCurrentDownloads(current);
    }

    @Test
    public void unknownUrlIsNeitherDownloadingNorQueued() {
        assertFalse(downloads.isDownloadingEpisode(URL));
        assertFalse(downloads.isEpisodeQueued(URL));
        assertEquals(-1, downloads.getProgress(URL));
    }

    @Test
    public void queuedEpisodeIsDownloadingAndQueuedWithItsProgress() {
        setDownload(URL, DownloadStatus.STATE_QUEUED, 0);
        assertTrue(downloads.isDownloadingEpisode(URL));
        assertTrue(downloads.isEpisodeQueued(URL));
        assertEquals(0, downloads.getProgress(URL));
    }

    @Test
    public void runningEpisodeIsDownloadingButNotQueued() {
        setDownload(URL, DownloadStatus.STATE_RUNNING, 42);
        assertTrue(downloads.isDownloadingEpisode(URL));
        assertFalse(downloads.isEpisodeQueued(URL));
        assertEquals(42, downloads.getProgress(URL));
    }

    @Test
    public void completedEpisodeIsNoLongerConsideredDownloading() {
        setDownload(URL, DownloadStatus.STATE_COMPLETED, 100);
        assertFalse(downloads.isDownloadingEpisode(URL));
        assertFalse(downloads.isEpisodeQueued(URL));
        assertEquals(-1, downloads.getProgress(URL));
    }

    @Test
    public void statusOfOtherUrlDoesNotLeak() {
        setDownload(URL, DownloadStatus.STATE_RUNNING, 10);
        assertFalse(downloads.isDownloadingEpisode("http://example.com/other.mp3"));
        assertEquals(-1, downloads.getProgress("http://example.com/other.mp3"));
    }

    @Test
    public void replacingCurrentDownloadsDropsPreviousEntries() {
        setDownload(URL, DownloadStatus.STATE_RUNNING, 10);
        downloads.setCurrentDownloads(new HashMap<>());
        assertFalse(downloads.isDownloadingEpisode(URL));
    }

    @Test
    public void registeredImplementationIsReturned() {
        DownloadServiceInterface.setImpl(downloads);
        assertSame(downloads, DownloadServiceInterface.get());
    }

    @Test
    public void stubReportsNoActiveDownloads() {
        assertEquals(0, downloads.getNumberOfActiveDownloads(null));
    }
}
