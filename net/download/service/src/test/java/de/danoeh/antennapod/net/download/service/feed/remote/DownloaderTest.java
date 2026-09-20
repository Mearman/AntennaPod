package de.danoeh.antennapod.net.download.service.feed.remote;

import android.os.Bundle;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DownloaderTest {
    private static class CountingDownloader extends Downloader {
        private int downloads = 0;

        CountingDownloader(DownloadRequest request) {
            super(request);
        }

        @Override
        protected void download() {
            downloads++;
        }
    }

    private static DownloadRequest request() {
        return new DownloadRequest("/downloads/file", "http://example.com/a.mp3", "Episode", 12,
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, null, null, false, new Bundle(), true);
    }

    @Test
    public void resultStartsUnsuccessfulAndDescribesTheRequest() {
        Downloader downloader = new CountingDownloader(request());
        assertFalse(downloader.getResult().isSuccessful());
        assertEquals("Episode", downloader.getResult().getTitle());
        assertEquals(12, downloader.getResult().getFeedfileId());
        assertEquals(FeedMedia.FEEDFILETYPE_FEEDMEDIA, downloader.getResult().getFeedfileType());
        assertFalse(downloader.isFinished());
        assertFalse(downloader.cancelled);
    }

    @Test
    public void callRunsTheDownloadOnceAndMarksItFinished() {
        CountingDownloader downloader = new CountingDownloader(request());

        Downloader returned = downloader.call();

        assertSame(downloader, returned);
        assertEquals(1, downloader.downloads);
        assertTrue(downloader.isFinished());
    }

    @Test
    public void cancelMarksTheDownloaderCancelled() {
        Downloader downloader = new CountingDownloader(request());
        downloader.cancel();
        assertTrue(downloader.cancelled);
    }
}
