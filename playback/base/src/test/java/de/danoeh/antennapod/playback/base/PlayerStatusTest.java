package de.danoeh.antennapod.playback.base;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerStatusTest {

    @Test
    public void nullThresholdIsAlwaysReached() {
        for (PlayerStatus status : PlayerStatus.values()) {
            assertTrue(status.isAtLeast(null));
        }
    }

    @Test
    public void everyStatusIsAtLeastItself() {
        for (PlayerStatus status : PlayerStatus.values()) {
            assertTrue(status.isAtLeast(status));
        }
    }

    @Test
    public void playingIsAtLeastPreparedButPreparedIsNotAtLeastPlaying() {
        assertTrue(PlayerStatus.PLAYING.isAtLeast(PlayerStatus.PREPARED));
        assertFalse(PlayerStatus.PREPARED.isAtLeast(PlayerStatus.PLAYING));
    }

    @Test
    public void pausedIsBetweenPreparedAndPlaying() {
        assertTrue(PlayerStatus.PAUSED.isAtLeast(PlayerStatus.PREPARED));
        assertTrue(PlayerStatus.PAUSED.isAtLeast(PlayerStatus.SEEKING));
        assertFalse(PlayerStatus.PAUSED.isAtLeast(PlayerStatus.PLAYING));
    }

    @Test
    public void errorIsBelowEveryOtherStatus() {
        for (PlayerStatus status : PlayerStatus.values()) {
            if (status != PlayerStatus.ERROR) {
                assertFalse(PlayerStatus.ERROR.isAtLeast(status));
                assertTrue(status.isAtLeast(PlayerStatus.ERROR));
            }
        }
    }

    @Test
    public void loadingStatesAreOrderedBeforePrepared() {
        assertTrue(PlayerStatus.INITIALIZED.isAtLeast(PlayerStatus.INITIALIZING));
        assertTrue(PlayerStatus.PREPARING.isAtLeast(PlayerStatus.INITIALIZED));
        assertTrue(PlayerStatus.PREPARED.isAtLeast(PlayerStatus.PREPARING));
        assertFalse(PlayerStatus.INITIALIZING.isAtLeast(PlayerStatus.INITIALIZED));
    }

    @Test
    public void indeterminateIsBelowStopped() {
        assertTrue(PlayerStatus.STOPPED.isAtLeast(PlayerStatus.INDETERMINATE));
        assertFalse(PlayerStatus.INDETERMINATE.isAtLeast(PlayerStatus.STOPPED));
    }
}
