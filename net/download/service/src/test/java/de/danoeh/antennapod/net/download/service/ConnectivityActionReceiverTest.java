package de.danoeh.antennapod.net.download.service;

import android.content.Intent;
import android.net.ConnectivityManager;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Category(IntegrationTest.class)
public class ConnectivityActionReceiverTest extends DownloadIntegrationTestBase {
    private final ConnectivityActionReceiver receiver = new ConnectivityActionReceiver();
    private DownloadServiceInterface downloadService;
    private AutoDownloadManager autoDownloadManager;

    @Before
    public void useRecordingServices() {
        downloadService = Mockito.mock(DownloadServiceInterface.class);
        DownloadServiceInterface.setImpl(downloadService);
        autoDownloadManager = AutoDownloadManager.getInstance();
    }

    private void receiveConnectivityChange() {
        receiver.onReceive(context, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Test
    public void unmeteredWifiStartsAutoDownload() {
        setNetwork(ConnectivityManager.TYPE_WIFI);

        receiveConnectivityChange();

        verify(autoDownloadManager).autodownloadUndownloadedItems(context);
        verify(downloadService, never()).cancelAll(context);
    }

    @Test
    public void mobileNetworkCancelsOngoingDownloads() {
        setNetwork(ConnectivityManager.TYPE_MOBILE);

        receiveConnectivityChange();

        verify(downloadService).cancelAll(context);
        verify(autoDownloadManager, never()).autodownloadUndownloadedItems(context);
    }

    @Test
    public void lostNetworkNeitherStartsNorCancelsDownloads() {
        setNoNetwork();

        receiveConnectivityChange();

        verify(downloadService, never()).cancelAll(context);
        verify(autoDownloadManager, never()).autodownloadUndownloadedItems(context);
    }

    @Test
    public void unrelatedBroadcastsAreIgnored() {
        setNetwork(ConnectivityManager.TYPE_WIFI);

        receiver.onReceive(context, new Intent(Intent.ACTION_BATTERY_LOW));

        verify(autoDownloadManager, never()).autodownloadUndownloadedItems(context);
        verify(downloadService, never()).cancelAll(context);
    }
}
