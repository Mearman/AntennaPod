package de.danoeh.antennapod.playback.service.internal;

import android.app.Notification;
import androidx.core.app.ServiceCompat;
import de.danoeh.antennapod.playback.service.PlaybackService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceStateManagerTest {
    private PlaybackService service;
    private PlaybackServiceStateManager stateManager;

    @Before
    public void setUp() {
        service = mock(PlaybackService.class);
        stateManager = new PlaybackServiceStateManager(service);
    }

    @Test
    public void startForegroundPassesTheNotificationOnToTheService() {
        Notification notification = mock(Notification.class);

        stateManager.startForeground(42, notification);

        verify(service).startForeground(42, notification);
    }

    @Test
    public void leavingTheForegroundRemovesTheNotificationWhenAskedTo() {
        stateManager.startForeground(42, mock(Notification.class));

        stateManager.stopForeground(true);

        verify(service).stopForeground(ServiceCompat.STOP_FOREGROUND_REMOVE);
    }

    @Test
    public void leavingTheForegroundCanDetachTheNotificationInsteadOfRemovingIt() {
        stateManager.startForeground(42, mock(Notification.class));

        stateManager.stopForeground(false);

        verify(service).stopForeground(ServiceCompat.STOP_FOREGROUND_DETACH);
    }

    @Test
    public void aServiceThatNeverEnteredTheForegroundIsNotAskedToLeaveIt() {
        stateManager.stopForeground(true);

        verify(service, never()).stopForeground(anyInt());
    }

    @Test
    public void theForegroundIsOnlyLeftOnceForASingleEntry() {
        stateManager.startForeground(42, mock(Notification.class));
        stateManager.stopForeground(true);
        stateManager.stopForeground(true);

        verify(service).stopForeground(ServiceCompat.STOP_FOREGROUND_REMOVE);
    }

    @Test
    public void stoppingTheServiceLeavesTheForegroundAndStopsItself() {
        stateManager.startForeground(42, mock(Notification.class));

        stateManager.stopService();

        verify(service).stopForeground(ServiceCompat.STOP_FOREGROUND_REMOVE);
        verify(service).stopSelf();
    }

    @Test
    public void noStartCommandIsRecordedBeforeOneArrives() {
        assertFalse(stateManager.hasReceivedValidStartCommand());
    }

    @Test
    public void aValidStartCommandIsRememberedUntilTheServiceStops() {
        stateManager.validStartCommandWasReceived();
        assertTrue(stateManager.hasReceivedValidStartCommand());

        stateManager.stopService();

        assertFalse(stateManager.hasReceivedValidStartCommand());
    }
}
