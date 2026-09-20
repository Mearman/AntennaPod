package de.danoeh.antennapod.net.sync.serviceinterface;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class SynchronizationQueueTest {
    @After
    public void resetInstance() {
        SynchronizationQueue.setInstance(null);
    }

    @Test
    public void instanceIsAbsentUntilOneIsSet() {
        assertNull(SynchronizationQueue.getInstance());
    }

    @Test
    public void getInstanceReturnsTheQueueThatWasSet() {
        SynchronizationQueue queue = new SynchronizationQueueStub();

        SynchronizationQueue.setInstance(queue);

        assertSame(queue, SynchronizationQueue.getInstance());
    }
}
