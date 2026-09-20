package de.danoeh.antennapod.net.download.service;

import android.content.Intent;
import androidx.preference.PreferenceManager;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Category(IntegrationTest.class)
public class PowerConnectionReceiverTest extends DownloadIntegrationTestBase {
    private final PowerConnectionReceiver receiver = new PowerConnectionReceiver();
    private DownloadServiceInterface downloadService;

    @Before
    public void useRecordingDownloadService() {
        downloadService = Mockito.mock(DownloadServiceInterface.class);
        DownloadServiceInterface.setImpl(downloadService);
    }

    private void allowAutoDownloadOnBattery(boolean allowed) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(UserPreferences.PREF_ENABLE_AUTODL_ON_BATTERY, allowed).commit();
    }

    @Test
    public void connectingPowerStartsAutoDownload() {
        receiver.onReceive(context, new Intent(Intent.ACTION_POWER_CONNECTED));

        verify(AutoDownloadManager.getInstance()).autodownloadUndownloadedItems(context);
        verify(downloadService, never()).cancelAll(context);
    }

    @Test
    public void disconnectingPowerCancelsDownloadsWhenBatteryDownloadsAreDisallowed() {
        allowAutoDownloadOnBattery(false);

        receiver.onReceive(context, new Intent(Intent.ACTION_POWER_DISCONNECTED));

        verify(downloadService).cancelAll(context);
    }

    @Test
    public void disconnectingPowerKeepsDownloadsWhenBatteryDownloadsAreAllowed() {
        allowAutoDownloadOnBattery(true);

        receiver.onReceive(context, new Intent(Intent.ACTION_POWER_DISCONNECTED));

        verify(downloadService, never()).cancelAll(context);
        verify(AutoDownloadManager.getInstance(), never()).autodownloadUndownloadedItems(context);
    }
}
