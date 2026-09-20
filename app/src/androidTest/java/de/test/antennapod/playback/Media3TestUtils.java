package de.test.antennapod.playback;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.media3.common.Player;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.util.concurrent.ListenableFuture;
import de.danoeh.antennapod.playback.service.Media3PlaybackService;
import de.danoeh.antennapod.playback.service.PlaybackService;
import org.awaitility.Awaitility;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility methods for tests that drive the Media3 playback service through a media session.
 */
public final class Media3TestUtils {
    private static final long CONNECT_TIMEOUT_SECONDS = 30;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 20;

    private Media3TestUtils() {
    }

    public static MediaController connectController(Context context) {
        AtomicReference<ListenableFuture<MediaController>> future = new AtomicReference<>();
        runOnMain(() -> future.set(new MediaController.Builder(context, sessionToken(context)).buildAsync()));
        return awaitFuture(future.get());
    }

    public static MediaBrowser connectBrowser(Context context) {
        AtomicReference<ListenableFuture<MediaBrowser>> future = new AtomicReference<>();
        runOnMain(() -> future.set(new MediaBrowser.Builder(context, sessionToken(context)).buildAsync()));
        return awaitFuture(future.get());
    }

    private static SessionToken sessionToken(Context context) {
        return new SessionToken(context, new ComponentName(context, Media3PlaybackService.class));
    }

    public static <T> T awaitFuture(ListenableFuture<T> future) {
        try {
            return future.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("Waiting for a media session future failed", e);
        }
    }

    public static void runOnMain(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }

    public static <T> T getOnMain(Callable<T> callable) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        runOnMain(() -> {
            try {
                result.set(callable.call());
            } catch (Exception e) {
                error.set(e);
            }
        });
        if (error.get() != null) {
            throw new AssertionError("Running on the main thread failed", error.get());
        }
        return result.get();
    }

    /**
     * Stops playback, releases the controller and waits until the service is destroyed. Tests must not continue while the service is alive because it keeps writing to the database.
     */
    public static void stopPlaybackService(Context context, Player controller) {
        if (controller != null) {
            runOnMain(() -> {
                controller.stop();
                controller.clearMediaItems();
                controller.release();
            });
        }
        awaitServiceStopped(context);
    }

    public static void awaitServiceStopped(Context context) {
        context.stopService(new Intent(context, Media3PlaybackService.class));
        Awaitility.await("playback service stopped")
                .atMost(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> !PlaybackService.isRunning && !isServiceAlive(context));
    }

    private static boolean isServiceAlive(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service
                : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (Media3PlaybackService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
