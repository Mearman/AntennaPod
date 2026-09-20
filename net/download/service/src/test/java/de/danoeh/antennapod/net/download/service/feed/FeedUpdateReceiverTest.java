package de.danoeh.antennapod.net.download.service.feed;

import android.content.Intent;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import de.danoeh.antennapod.net.download.service.DownloadIntegrationTestBase;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@Category(IntegrationTest.class)
public class FeedUpdateReceiverTest extends DownloadIntegrationTestBase {
    @After
    public void removeUpdateManager() {
        FeedUpdateManager.setInstance(null);
    }

    @Test
    public void receivedIntentEnqueuesRefreshOfAllFeeds() {
        FeedUpdateManager.setInstance(new FeedUpdateManagerImpl());

        new FeedUpdateReceiver().onReceive(context, new Intent("refresh"));

        ArgumentCaptor<OneTimeWorkRequest> captor = ArgumentCaptor.forClass(OneTimeWorkRequest.class);
        verify(workManager).enqueueUniqueWork(any(String.class), any(ExistingWorkPolicy.class),
                captor.capture());
        assertEquals(FeedUpdateWorker.class.getName(), captor.getValue().getWorkSpec().workerClassName);
        assertEquals(-1, captor.getValue().getWorkSpec().input.getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
    }
}
