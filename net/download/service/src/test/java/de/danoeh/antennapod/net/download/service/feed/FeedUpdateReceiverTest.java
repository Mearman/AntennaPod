package de.danoeh.antennapod.net.download.service.feed;

import android.content.Intent;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import de.danoeh.antennapod.net.download.service.DownloadIntegrationTestBase;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Category(IntegrationTest.class)
public class FeedUpdateReceiverTest extends DownloadIntegrationTestBase {
    @Test
    public void receivedIntentRefreshesAllFeedsOnce() {
        FeedUpdateManager updateManager = mock(FeedUpdateManager.class);
        FeedUpdateManager.setInstance(updateManager);

        new FeedUpdateReceiver().onReceive(context, new Intent("refresh"));

        verify(updateManager).runOnce(context);
        verify(updateManager, never()).runOnce(any(), any());
    }

    @Test
    public void refreshWorkIsEnqueuedThroughTheRealManager() {
        FeedUpdateManager.setInstance(new FeedUpdateManagerImpl());

        new FeedUpdateReceiver().onReceive(context, new Intent("refresh"));

        ArgumentCaptor<OneTimeWorkRequest> captor = ArgumentCaptor.forClass(OneTimeWorkRequest.class);
        verify(workManager).enqueueUniqueWork(any(String.class), any(ExistingWorkPolicy.class),
                captor.capture());
        assertEquals(FeedUpdateWorker.class.getName(), captor.getValue().getWorkSpec().workerClassName);
    }
}
