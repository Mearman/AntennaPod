package de.test.antennapod.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import org.awaitility.Awaitility;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class PlatformNetwork {
    private static final long TIMEOUT_SECONDS = 60;

    public static void activateMeteredCellularNetwork() throws IOException {
        runShellCommand("svc wifi disable");
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> activeNetworkHasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    public static void activateUnmeteredWifiNetwork() throws IOException {
        runShellCommand("svc wifi enable");
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> {
            NetworkCapabilities capabilities = activeNetworkCapabilities();
            return capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        });
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
