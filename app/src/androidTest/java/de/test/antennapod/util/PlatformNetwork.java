package de.test.antennapod.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import org.junit.Assume;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class PlatformNetwork {
    private static final long TIMEOUT_SECONDS = 60;

    private static Boolean networkStateSwitchable;

    public static void assumeNetworkStateCanBeSwitched() throws IOException {
        Assume.assumeTrue("Emulator image cannot switch between wifi and cellular networks",
                isNetworkStateSwitchable());
    }

    public static synchronized boolean isNetworkStateSwitchable() throws IOException {
        if (networkStateSwitchable == null) {
            networkStateSwitchable = probeNetworkStateSwitchable();
        }
        return networkStateSwitchable;
    }

    private static boolean probeNetworkStateSwitchable() throws IOException {
        runShellCommand("svc wifi disable");
        boolean cellularReachable = cellularNetworkBecameActive();
        runShellCommand("svc wifi enable");
        boolean wifiRestored = unmeteredWifiBecameActive();
        return cellularReachable && wifiRestored;
    }

    private static boolean cellularNetworkBecameActive() {
        try {
            Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .until(() -> activeNetworkHasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
            return true;
        } catch (ConditionTimeoutException e) {
            return false;
        }
    }

    private static boolean unmeteredWifiBecameActive() {
        try {
            Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(PlatformNetwork::isUnmeteredWifiActive);
            return true;
        } catch (ConditionTimeoutException e) {
            return false;
        }
    }

    public static void activateMeteredCellularNetwork() throws IOException {
        runShellCommand("svc wifi disable");
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> activeNetworkHasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    public static void activateUnmeteredWifiNetwork() throws IOException {
        if (isUnmeteredWifiActive()) {
            return;
        }
        runShellCommand("svc wifi enable");
        if (!isNetworkStateSwitchable()) {
            return;
        }
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(PlatformNetwork::isUnmeteredWifiActive);
    }

    private static boolean isUnmeteredWifiActive() {
        NetworkCapabilities capabilities = activeNetworkCapabilities();
        return capabilities != null
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    private static boolean activeNetworkHasTransport(int transport) {
        NetworkCapabilities capabilities = activeNetworkCapabilities();
        return capabilities != null && capabilities.hasTransport(transport);
    }

    private static NetworkCapabilities activeNetworkCapabilities() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager.getActiveNetwork();
        return network == null ? null : manager.getNetworkCapabilities(network);
    }

    private static void runShellCommand(String command) throws IOException {
        ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(command);
        try (FileInputStream in = new FileInputStream(output.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) {
                continue;
            }
        }
    }
}
