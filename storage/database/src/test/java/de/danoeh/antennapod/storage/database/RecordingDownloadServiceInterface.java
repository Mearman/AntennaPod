package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingDownloadServiceInterface extends DownloadServiceInterfaceStub {
    private final List<FeedMedia> cancelledMedia = new CopyOnWriteArrayList<>();

    @Override
    public void cancel(Context context, FeedMedia media) {
        cancelledMedia.add(media);
    }

    public List<FeedMedia> getCancelledMedia() {
        return cancelledMedia;
    }
}
