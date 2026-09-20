package de.test.antennapod.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import androidx.test.platform.app.InstrumentationRegistry;

public class PlatformNetwork {
    private static ConnectivityManager connectivityManager() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private static NetworkCapabilities activeCapabilities() {
        ConnectivityManager manager = connectivityManager();
        Network network = manager.getActiveNetwork();
        return network == null ? null : manager.getNetworkCapabilities(network);
    }

    public static boolean isConnected() {
        return activeCapabilities() != null;
    }

    public static boolean isMeteredOrCellular() {
        NetworkCapabilities capabilities = activeCapabilities();
        if (capabilities == null) {
            return true;
        }
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    public static boolean isVpnOverWifi() {
        NetworkCapabilities capabilities = activeCapabilities();
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
}
