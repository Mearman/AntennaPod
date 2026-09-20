package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.service.quicksettings.Tile;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@RunWith(RobolectricTestRunner.class)
public class QuickSettingsTileServiceTest {
    private QuickSettingsTileService service;
    private Tile tile;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        PlaybackPreferences.init(context);
        PlaybackService.isRunning = false;
        service = spy(new QuickSettingsTileService());
        tile = mock(Tile.class);
    }

    @After
    public void tearDown() {
        PlaybackService.isRunning = false;
    }

    @Test
    public void theTileIsActiveWhileTheRunningServiceReportsPlayback() {
        doReturn(tile).when(service).getQsTile();
        PlaybackService.isRunning = true;
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING);

        service.updateTile();

        verify(tile).setState(Tile.STATE_ACTIVE);
        verify(tile).updateTile();
    }

    @Test
    public void theTileIsInactiveWhilePlaybackIsPaused() {
        doReturn(tile).when(service).getQsTile();
        PlaybackService.isRunning = true;
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PAUSED);

        service.updateTile();

        verify(tile).setState(Tile.STATE_INACTIVE);
        verify(tile).updateTile();
    }

    @Test
    public void theTileIsInactiveWhileThePlaybackServiceIsNotRunning() {
        doReturn(tile).when(service).getQsTile();
        PlaybackService.isRunning = false;
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING);

        service.updateTile();

        verify(tile).setState(Tile.STATE_INACTIVE);
    }

    @Test
    public void anUpdateWithoutATileAttachedIsIgnored() {
        doReturn(null).when(service).getQsTile();

        service.updateTile();

        verifyNoInteractions(tile);
    }
}
