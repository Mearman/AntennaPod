package de.danoeh.antennapod.system.utils;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowBuild;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class ThreadUtilsTest {

    @After
    public void resetBuild() {
        ShadowBuild.reset();
    }

    private static RuntimeException failureOfAssertion() {
        try {
            ThreadUtils.assertNotMainThread();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    @Test
    public void mainThreadIsNotRejectedInsideTests() {
        assertNull(failureOfAssertion());
    }

    @Test
    public void mainThreadIsNotRejectedWhenJUnitIsOnClasspathOnNonRobolectricDevice() {
        ShadowBuild.setFingerprint("release-keys/device");
        assertNull(failureOfAssertion());
    }

    @Test
    public void backgroundThreadIsNotRejected() throws InterruptedException {
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> failure.set(failureOfAssertion()));
        thread.start();
        thread.join();
        assertNull(failure.get());
    }
}
