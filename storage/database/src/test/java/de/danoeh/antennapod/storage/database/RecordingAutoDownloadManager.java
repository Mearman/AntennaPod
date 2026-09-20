package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import com.google.common.util.concurrent.Futures;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class RecordingAutoDownloadManager extends AutoDownloadManager {
    private final AtomicInteger autodownloadRequests = new AtomicInteger();
    private final AtomicInteger cleanupRequests = new AtomicInteger();

    @Override
    public Future<?> autodownloadUndownloadedItems(Context context) {
        autodownloadRequests.incrementAndGet();
        return Futures.immediateFuture(null);
    }

    @Override
    public void performAutoCleanup(Context context) {
        cleanupRequests.incrementAndGet();
    }

    public int getAutodownloadRequests() {
        return autodownloadRequests.get();
    }

    public int getCleanupRequests() {
        return cleanupRequests.get();
    }
}
