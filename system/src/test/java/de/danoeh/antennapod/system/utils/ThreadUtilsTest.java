package de.danoeh.antennapod.system.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowBuild;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class ThreadUtilsTest {

    @Test
    public void mainThreadIsAllowedInsideTests() {
        ThreadUtils.assertNotMainThread();
    }

    @Test
    public void mainThreadIsAllowedWhenJUnitIsOnClasspathOnNonRobolectricDevice() {
        ShadowBuild.setFingerprint("release-keys/device");
        ThreadUtils.assertNotMainThread();
    }

    @Test
    public void backgroundThreadIsAlwaysAllowed() throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                ThreadUtils.assertNotMainThread();
            } catch (RuntimeException e) {
                failure.set(e);
            }
        });
        thread.start();
        thread.join();
        assertNull(failure.get());
    }
}
