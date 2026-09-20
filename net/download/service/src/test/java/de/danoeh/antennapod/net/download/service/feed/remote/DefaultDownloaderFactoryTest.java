package de.danoeh.antennapod.net.download.service.feed.remote;

import android.os.Bundle;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.feed.Feed;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DefaultDownloaderFactoryTest {
    private static Downloader create(String source) {
        DownloadRequest request = new DownloadRequest("/downloads/file", source, "title", 1,
                Feed.FEEDFILETYPE_FEED, null, null, null, false, new Bundle(), true);
        return new DefaultDownloaderFactory().create(request);
    }

    @Test
    public void httpSourceGetsHttpDownloader() {
        assertTrue(create("http://example.com/feed.xml") instanceof HttpDownloader);
    }

    @Test
    public void httpsSourceGetsHttpDownloader() {
        assertTrue(create("https://example.com/feed.xml") instanceof HttpDownloader);
    }

    @Test
    public void unsupportedSchemesGetNoDownloader() {
        assertNull(create("ftp://example.com/feed.xml"));
        assertNull(create("content://com.example/document"));
        assertNull(create("file:///sdcard/feed.xml"));
    }
}
