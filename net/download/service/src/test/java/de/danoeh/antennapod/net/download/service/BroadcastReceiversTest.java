package de.danoeh.antennapod.net.download.service;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateReceiver;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class BroadcastReceiversTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final AutoDownloadManager autoDownload = Mockito.mock(AutoDownloadManager.class);
    private final DownloadServiceInterface downloads = Mockito.mock(DownloadServiceInterface.class);
    private final FeedUpdateManager feedUpdates = Mockito.mock(FeedUpdateManager.class);
    private MockedStatic<UserPreferences> preferences;
    private MockedStatic<NetworkUtils> network;

    @Before
    public void setUp() {
        AutoDownloadManager.setInstance(autoDownload);
        DownloadServiceInterface.setImpl(downloads);
        FeedUpdateManager.setInstance(feedUpdates);
        preferences = Mockito.mockStatic(UserPreferences.class);
        network = Mockito.mockStatic(NetworkUtils.class);
    }

    @After
    public void tearDown() {
        network.close();
        preferences.close();
        FeedUpdateManager.setInstance(null);
        DownloadServiceInterface.setImpl(null);
        AutoDownloadManager.setInstance(null);
    }

    @Test
    public void connectingPowerStartsAutoDownloadEvenIfBatteryDownloadsAreForbidden() {
        preferences.when(UserPreferences::isEnableAutodownloadOnBattery).thenReturn(false);

        new PowerConnectionReceiver().onReceive(context, new Intent(Intent.ACTION_POWER_CONNECTED));

        Mockito.verify(autoDownload).autodownloadUndownloadedItems(context);
        Mockito.verifyNoInteractions(downloads);
    }

    @Test
    public void disconnectingPowerCancelsDownloadsWhenBatteryDownloadsAreForbidden() {
        preferences.when(UserPreferences::isEnableAutodownloadOnBattery).thenReturn(false);

        new PowerConnectionReceiver().onReceive(context, new Intent(Intent.ACTION_POWER_DISCONNECTED));

        Mockito.verify(downloads).cancelAll(context);
        Mockito.verifyNoInteractions(autoDownload);
    }

    @Test
    public void disconnectingPowerKeepsDownloadsWhenBatteryDownloadsAreAllowed() {
        preferences.when(UserPreferences::isEnableAutodownloadOnBattery).thenReturn(true);

        new PowerConnectionReceiver().onReceive(context, new Intent(Intent.ACTION_POWER_DISCONNECTED));

        Mockito.verifyNoInteractions(downloads);
        Mockito.verifyNoInteractions(autoDownload);
    }

    @Test
    public void connectivityChangeToAllowedNetworkStartsAutoDownload() {
        network.when(NetworkUtils::isAutoDownloadAllowed).thenReturn(true);

        new ConnectivityActionReceiver().onReceive(context, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

        Mockito.verify(autoDownload).autodownloadUndownloadedItems(context);
        Mockito.verifyNoInteractions(downloads);
    }

    @Test
    public void connectivityChangeToRestrictedNetworkCancelsDownloads() {
        network.when(NetworkUtils::isAutoDownloadAllowed).thenReturn(false);
        network.when(NetworkUtils::isNetworkRestricted).thenReturn(true);

        new ConnectivityActionReceiver().onReceive(context, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

        Mockito.verify(downloads).cancelAll(context);
        Mockito.verifyNoInteractions(autoDownload);
    }

    @Test
    public void connectivityChangeToUnrestrictedButDisallowedNetworkDoesNothing() {
        network.when(NetworkUtils::isAutoDownloadAllowed).thenReturn(false);
        network.when(NetworkUtils::isNetworkRestricted).thenReturn(false);

        new ConnectivityActionReceiver().onReceive(context, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

        Mockito.verifyNoInteractions(downloads);
        Mockito.verifyNoInteractions(autoDownload);
    }

    @Test
    public void unrelatedBroadcastsAreIgnoredByConnectivityReceiver() {
        network.when(NetworkUtils::isAutoDownloadAllowed).thenReturn(true);

        new ConnectivityActionReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));

        Mockito.verifyNoInteractions(autoDownload);
        Mockito.verifyNoInteractions(downloads);
    }

    @Test
    public void feedUpdateReceiverRefreshesAllFeeds() {
        new FeedUpdateReceiver().onReceive(context, new Intent());

        Mockito.verify(feedUpdates).runOnce(context);
    }
}
