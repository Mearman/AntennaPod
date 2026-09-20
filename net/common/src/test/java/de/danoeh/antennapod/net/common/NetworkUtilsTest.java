package de.danoeh.antennapod.net.common;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class NetworkUtilsTest {
    private ConnectivityManager connectivityManager;
    private ShadowConnectivityManager shadowConnectivityManager;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        NetworkUtils.init(context);
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowConnectivityManager = Shadow.extract(connectivityManager);
    }

    private void connect(int networkType, int... transports) {
        NetworkInfo info = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, networkType, 0,
                true, NetworkInfo.State.CONNECTED);
        shadowConnectivityManager.setActiveNetworkInfo(info);
        Network network = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = ShadowNetworkCapabilities.newInstance();
        ShadowNetworkCapabilities shadowCapabilities = Shadow.extract(capabilities);
        for (int transport : transports) {
            shadowCapabilities.addTransportType(transport);
        }
        shadowConnectivityManager.setNetworkCapabilities(network, capabilities);
    }

    private void disconnect() {
        shadowConnectivityManager.setActiveNetworkInfo(null);
    }

    @Test
    public void networkIsUnavailableWithoutActiveNetwork() {
        disconnect();

        assertFalse(NetworkUtils.networkAvailable());
    }

    @Test
    public void networkIsAvailableWhenActiveNetworkIsConnected() {
        connect(ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI);

        assertTrue(NetworkUtils.networkAvailable());
    }

    @Test
    public void unmeteredWifiIsNotRestricted() {
        connect(ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI);

        assertFalse(NetworkUtils.isNetworkRestricted());
        assertTrue(NetworkUtils.isEpisodeDownloadAllowed());
        assertTrue(NetworkUtils.isFeedRefreshAllowed());
        assertTrue(NetworkUtils.isStreamingAllowed());
        assertTrue(NetworkUtils.isImageAllowed());
    }

    @Test
    public void cellularNetworkIsRestricted() {
        connect(ConnectivityManager.TYPE_MOBILE, NetworkCapabilities.TRANSPORT_CELLULAR);

        assertTrue(NetworkUtils.isNetworkRestricted());
    }

    @Test
    public void networkWithoutCapabilitiesIsTreatedAsRestrictedToBeSafe() {
        NetworkInfo info = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, NetworkInfo.State.CONNECTED);
        shadowConnectivityManager.setActiveNetworkInfo(info);
        shadowConnectivityManager.setNetworkCapabilities(connectivityManager.getActiveNetwork(), null);

        assertTrue(NetworkUtils.isNetworkRestricted());
    }

    @Test
    public void cellularOnlyAllowsImagesByDefault() {
        connect(ConnectivityManager.TYPE_MOBILE, NetworkCapabilities.TRANSPORT_CELLULAR);

        assertTrue(NetworkUtils.isImageAllowed());
        assertTrue(NetworkUtils.isEpisodeHeadDownloadAllowed());
        assertFalse(NetworkUtils.isEpisodeDownloadAllowed());
        assertFalse(NetworkUtils.isStreamingAllowed());
        assertFalse(NetworkUtils.isFeedRefreshAllowed());
    }

    @Test
    public void cellularUsageFollowsStoredMobileDataPreferences() {
        connect(ConnectivityManager.TYPE_MOBILE, NetworkCapabilities.TRANSPORT_CELLULAR);

        UserPreferences.setAllowMobileEpisodeDownload(true);
        UserPreferences.setAllowMobileStreaming(true);
        UserPreferences.setAllowMobileFeedRefresh(true);
        UserPreferences.setAllowMobileImages(false);

        assertTrue(NetworkUtils.isEpisodeDownloadAllowed());
        assertTrue(NetworkUtils.isStreamingAllowed());
        assertTrue(NetworkUtils.isFeedRefreshAllowed());
        assertFalse(NetworkUtils.isImageAllowed());
        assertFalse(NetworkUtils.isEpisodeHeadDownloadAllowed());
    }

    @Test
    public void autoDownloadIsNotAllowedWithoutNetwork() {
        disconnect();

        assertFalse(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void autoDownloadIsAllowedOnUnmeteredWifi() {
        connect(ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI);

        assertTrue(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void autoDownloadIsAllowedOnEthernet() {
        connect(ConnectivityManager.TYPE_ETHERNET, NetworkCapabilities.TRANSPORT_ETHERNET);

        assertTrue(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void autoDownloadOnCellularRequiresMobileAutoDownloadPreference() {
        connect(ConnectivityManager.TYPE_MOBILE, NetworkCapabilities.TRANSPORT_CELLULAR);
        assertFalse(NetworkUtils.isAutoDownloadAllowed());

        UserPreferences.setAllowMobileAutoDownload(true);

        assertTrue(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void vpnOverWifiRequiresBothTransports() {
        connect(ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_VPN);
        assertTrue(NetworkUtils.isVpnOverWifi());

        connect(ConnectivityManager.TYPE_WIFI, NetworkCapabilities.TRANSPORT_WIFI);
        assertFalse(NetworkUtils.isVpnOverWifi());

        connect(ConnectivityManager.TYPE_MOBILE, NetworkCapabilities.TRANSPORT_CELLULAR,
                NetworkCapabilities.TRANSPORT_VPN);
        assertFalse(NetworkUtils.isVpnOverWifi());
    }

    @Test
    public void downloadBlockedWhenFailureMentionsLoopbackAddress() {
        assertTrue(NetworkUtils.wasDownloadBlocked(new IOException("Failed to connect to /127.0.0.1:443")));
        assertTrue(NetworkUtils.wasDownloadBlocked(new IOException("Failed to connect to /0.0.0.0:80")));
    }

    @Test
    public void downloadNotBlockedForOtherAddressesOrMessages() {
        assertFalse(NetworkUtils.wasDownloadBlocked(new IOException("Failed to connect to /192.168.1.5:443")));
        assertFalse(NetworkUtils.wasDownloadBlocked(new IOException("Connection reset")));
        assertFalse(NetworkUtils.wasDownloadBlocked(new IOException()));
    }

    @Test
    public void downloadBlockedIsDetectedInCauseChain() {
        IOException inner = new IOException("connect to 127.0.0.1 refused");
        IOException outer = new IOException("Download failed", inner);

        assertTrue(NetworkUtils.wasDownloadBlocked(outer));
    }
}
