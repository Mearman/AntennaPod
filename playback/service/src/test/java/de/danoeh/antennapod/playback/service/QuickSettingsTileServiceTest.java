package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.view.KeyEvent;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.robolectric.Shadows.shadowOf;

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

    @Test
    public void addingTheTileImmediatelyPutsItIntoTheRightState() {
        doReturn(tile).when(service).getQsTile();
        PlaybackService.isRunning = true;
        PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PLAYING);

        service.onTileAdded();

        verify(tile).setState(Tile.STATE_ACTIVE);
    }

    @Test
    public void theTileIsRefreshedWheneverTheSystemStartsListeningToIt() {
        doReturn(tile).when(service).getQsTile();
        PlaybackService.isRunning = false;

        service.onStartListening();

        verify(tile).setState(Tile.STATE_INACTIVE);
        verify(tile).updateTile();
    }

    @Test
    public void tappingTheTileSendsAPlayPauseMediaButton() {
        QuickSettingsTileService attached =
                Robolectric.buildService(QuickSettingsTileService.class).create().get();

        attached.onClick();

        List<Intent> broadcasts = shadowOf(RuntimeEnvironment.getApplication()).getBroadcastIntents();
        Intent sent = broadcasts.get(broadcasts.size() - 1);
        KeyEvent event = sent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, event.getKeyCode());
    }
}
