package de.danoeh.antennapod.net.common;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;

import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class NetworkUtilsTest {
    private MockedStatic<UserPreferences> userPreferences;
    private ConnectivityManager connectivityManager;
    private Network network;
    private NetworkInfo activeNetworkInfo;
    private NetworkCapabilities capabilities;

    @Before
    public void setUp() {
        userPreferences = mockStatic(UserPreferences.class);
        Context context = mock(Context.class);
        connectivityManager = mock(ConnectivityManager.class);
        network = mock(Network.class);
        activeNetworkInfo = mock(NetworkInfo.class);
        capabilities = mock(NetworkCapabilities.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
        NetworkUtils.init(context);
    }

    @After
    public void tearDown() {
        userPreferences.close();
    }

    private void givenActiveNetworkType(int type) {
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(activeNetworkInfo);
        when(activeNetworkInfo.getType()).thenReturn(type);
        when(connectivityManager.getActiveNetwork()).thenReturn(network);
        when(connectivityManager.getNetworkInfo(network)).thenReturn(activeNetworkInfo);
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(capabilities);
    }

    private void givenMeteredWifi() {
        givenActiveNetworkType(ConnectivityManager.TYPE_WIFI);
        when(connectivityManager.isActiveNetworkMetered()).thenReturn(true);
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);
    }

    private void givenUnmeteredWifi() {
        givenActiveNetworkType(ConnectivityManager.TYPE_WIFI);
        when(connectivityManager.isActiveNetworkMetered()).thenReturn(false);
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);
    }

    private void givenUnmeteredCellular() {
        givenActiveNetworkType(ConnectivityManager.TYPE_MOBILE);
        when(connectivityManager.isActiveNetworkMetered()).thenReturn(false);
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true);
    }

    private void assertFollowsMobilePreference(BooleanSupplier policy, MockedStatic.Verification preference) {
        givenMeteredWifi();
        userPreferences.when(preference).thenReturn(false);
        assertFalse(policy.getAsBoolean());
        userPreferences.when(preference).thenReturn(true);
        assertTrue(policy.getAsBoolean());
        givenUnmeteredWifi();
        userPreferences.when(preference).thenReturn(false);
        assertTrue(policy.getAsBoolean());
    }

    @Test
    public void testNetworkAvailableIsFalseWithoutActiveNetwork() {
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(null);
        assertFalse(NetworkUtils.networkAvailable());
    }

    @Test
    public void testNetworkAvailableFollowsConnectedStateOfActiveNetwork() {
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(activeNetworkInfo);
        when(activeNetworkInfo.isConnected()).thenReturn(true);
        assertTrue(NetworkUtils.networkAvailable());
        when(activeNetworkInfo.isConnected()).thenReturn(false);
        assertFalse(NetworkUtils.networkAvailable());
    }

    @Test
    public void testAutoDownloadIsNotAllowedWithoutActiveNetwork() {
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(null);
        userPreferences.when(UserPreferences::isAllowMobileAutoDownload).thenReturn(true);
        assertFalse(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void testAutoDownloadOverUnmeteredWifiIsAllowed() {
        givenUnmeteredWifi();
        assertTrue(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void testAutoDownloadOverMeteredWifiIsNotAllowedEvenIfMobileAutoDownloadIsEnabled() {
        givenMeteredWifi();
        userPreferences.when(UserPreferences::isAllowMobileAutoDownload).thenReturn(true);
        assertFalse(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void testAutoDownloadOverEthernetIsAllowed() {
        givenActiveNetworkType(ConnectivityManager.TYPE_ETHERNET);
        when(connectivityManager.isActiveNetworkMetered()).thenReturn(true);
        assertTrue(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void testAutoDownloadOverMobileRequiresMobileAutoDownloadPreference() {
        givenUnmeteredCellular();
        userPreferences.when(UserPreferences::isAllowMobileAutoDownload).thenReturn(false);
        assertFalse(NetworkUtils.isAutoDownloadAllowed());
        userPreferences.when(UserPreferences::isAllowMobileAutoDownload).thenReturn(true);
        assertTrue(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void testAutoDownloadOverUnrestrictedOtherNetworkTypeIsAllowedWithoutPreference() {
        givenActiveNetworkType(ConnectivityManager.TYPE_BLUETOOTH);
        when(connectivityManager.isActiveNetworkMetered()).thenReturn(false);
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(false);
        userPreferences.when(UserPreferences::isAllowMobileAutoDownload).thenReturn(false);
        assertTrue(NetworkUtils.isAutoDownloadAllowed());
    }

    @Test
    public void testEpisodeDownloadFollowsMobilePreferenceOnRestrictedNetworks() {
        assertFollowsMobilePreference(NetworkUtils::isEpisodeDownloadAllowed,
                UserPreferences::isAllowMobileEpisodeDownload);
    }

    @Test
    public void testImageDownloadFollowsMobilePreferenceOnRestrictedNetworks() {
        assertFollowsMobilePreference(NetworkUtils::isImageAllowed, UserPreferences::isAllowMobileImages);
    }

    @Test
    public void testEpisodeHeadDownloadFollowsImagePreference() {
        assertFollowsMobilePreference(NetworkUtils::isEpisodeHeadDownloadAllowed,
                UserPreferences::isAllowMobileImages);
    }

    @Test
    public void testStreamingFollowsMobilePreferenceOnRestrictedNetworks() {
        assertFollowsMobilePreference(NetworkUtils::isStreamingAllowed, UserPreferences::isAllowMobileStreaming);
    }

    @Test
    public void testFeedRefreshFollowsMobilePreferenceOnRestrictedNetworks() {
        assertFollowsMobilePreference(NetworkUtils::isFeedRefreshAllowed,
                UserPreferences::isAllowMobileFeedRefresh);
    }

    @Test
    public void testNetworkIsRestrictedWhenMetered() {
        givenMeteredWifi();
        assertTrue(NetworkUtils.isNetworkRestricted());
    }

    @Test
    public void testNetworkIsRestrictedWhenCellular() {
        givenUnmeteredCellular();
        assertTrue(NetworkUtils.isNetworkRestricted());
    }

    @Test
    public void testUnmeteredWifiIsNotRestricted() {
        givenUnmeteredWifi();
        assertFalse(NetworkUtils.isNetworkRestricted());
    }

    @Test
    public void testNetworkIsNotRestrictedWhenNothingIsConnected() {
        when(connectivityManager.isActiveNetworkMetered()).thenReturn(false);
        when(connectivityManager.getActiveNetwork()).thenReturn(null);
        assertFalse(NetworkUtils.isNetworkRestricted());
    }

    @Test
    public void testNetworkIsRestrictedWhenNetworkInfoIsUnknown() {
        givenUnmeteredWifi();
        when(connectivityManager.getNetworkInfo(network)).thenReturn(null);
        assertTrue(NetworkUtils.isNetworkRestricted());
    }

    @Test
    public void testNetworkIsRestrictedWhenCapabilitiesAreUnknown() {
        givenUnmeteredWifi();
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(null);
        assertTrue(NetworkUtils.isNetworkRestricted());
    }

    @Test
    public void testVpnOverWifiRequiresBothTransports() {
        givenUnmeteredWifi();
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(false);
        assertFalse(NetworkUtils.isVpnOverWifi());
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true);
        assertTrue(NetworkUtils.isVpnOverWifi());
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(false);
        assertFalse(NetworkUtils.isVpnOverWifi());
    }

    @Test
    public void testVpnOverWifiIsFalseWithoutCapabilities() {
        when(connectivityManager.getActiveNetwork()).thenReturn(network);
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(null);
        assertFalse(NetworkUtils.isVpnOverWifi());
    }

    @Test
    public void testDownloadBlockedWhenMessageContainsLoopbackAddress() {
        assertTrue(NetworkUtils.wasDownloadBlocked(new Exception("Unable to connect to 127.0.0.1:443")));
    }

    @Test
    public void testDownloadBlockedWhenMessageContainsUnspecifiedAddress() {
        assertTrue(NetworkUtils.wasDownloadBlocked(new Exception("failed to connect to /0.0.0.0 (port 80)")));
    }

    @Test
    public void testDownloadNotBlockedWhenMessageContainsPublicAddress() {
        assertFalse(NetworkUtils.wasDownloadBlocked(new Exception("failed to connect to /192.168.1.20 (port 80)")));
    }

    @Test
    public void testDownloadBlockedOnlyLooksAtFirstAddressInMessage() {
        assertFalse(NetworkUtils.wasDownloadBlocked(new Exception("10.0.0.1 redirected to 127.0.0.1")));
    }

    @Test
    public void testDownloadBlockedDetectedInCauseWhenMessageHasNoAddress() {
        Throwable wrapped = new RuntimeException("Download failed", new Exception("connect to 127.0.0.1 refused"));
        assertTrue(NetworkUtils.wasDownloadBlocked(wrapped));
    }

    @Test
    public void testDownloadBlockedDetectedInCauseWhenMessageIsNull() {
        Throwable withoutMessage = new Exception((String) null, new Exception("connect to 0.0.0.0 refused"));
        assertTrue(NetworkUtils.wasDownloadBlocked(withoutMessage));
    }

    @Test
    public void testDownloadNotBlockedWithoutMessageOrCause() {
        assertFalse(NetworkUtils.wasDownloadBlocked(new Exception((String) null)));
        assertFalse(NetworkUtils.wasDownloadBlocked(new Exception("Connection reset")));
    }
}
