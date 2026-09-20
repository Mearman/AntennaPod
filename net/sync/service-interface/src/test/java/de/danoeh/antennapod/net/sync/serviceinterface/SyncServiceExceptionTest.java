package de.danoeh.antennapod.net.sync.serviceinterface;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class SyncServiceExceptionTest {
    @Test
    public void messageConstructorKeepsMessage() {
        assertEquals("message", new SyncServiceException("message").getMessage());
    }

    @Test
    public void causeConstructorKeepsCause() {
        Exception cause = new IllegalStateException("cause");

        assertSame(cause, new SyncServiceException(cause).getCause());
    }
}
