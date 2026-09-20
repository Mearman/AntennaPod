package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter;
import de.danoeh.antennapod.ui.appstartintent.VideoPlayerActivityStarter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        PlaybackPreferences.writeNoMediaPlaying();
    }

    @After
    public void tearDown() {
        PlaybackService.isRunning = false;
    }

    private String videoPlayerAction() {
        return new VideoPlayerActivityStarter(context).getIntent().getAction();
    }

    private static Playable playableOfType(MediaType type) {
        Playable playable = mock(Playable.class);
        when(playable.getMediaType()).thenReturn(type);
        return playable;
    }

    @Test
    public void theLegacyServiceRefusesToStart() {
        assertThrows(IllegalStateException.class,
                () -> Robolectric.buildService(PlaybackService.class).create());
    }

    @Test
    public void aVideoPlayableOpensTheVideoPlayer() {
        Intent intent = PlaybackService.getPlayerActivityIntent(context, playableOfType(MediaType.VIDEO));

        assertEquals(videoPlayerAction(), intent.getAction());
    }

    @Test
    public void anAudioPlayableOpensThePlayerInTheMainActivity() {
        Intent intent = PlaybackService.getPlayerActivityIntent(context, playableOfType(MediaType.AUDIO));

        assertEquals(MainActivityStarter.INTENT, intent.getAction());
        assertTrue(intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_PLAYER, false));
        assertTrue(intent.getBooleanExtra(MainActivityStarter.EXTRA_CLEAR_BACK_STACK, false));
    }

    @Test
    public void aStoppedServiceFallsBackToTheLastPlayedEpisodeBeingAVideo() {
        PlaybackService.isRunning = false;
        PlaybackPreferences.writeMediaPlaying(playableOfType(MediaType.VIDEO));

        Intent intent = PlaybackService.getPlayerActivityIntent(context);

        assertEquals(videoPlayerAction(), intent.getAction());
    }

    @Test
    public void aStoppedServiceFallsBackToTheLastPlayedEpisodeBeingAudio() {
        PlaybackService.isRunning = false;
        PlaybackPreferences.writeMediaPlaying(playableOfType(MediaType.AUDIO));

        Intent intent = PlaybackService.getPlayerActivityIntent(context);

        assertEquals(MainActivityStarter.INTENT, intent.getAction());
    }

    @Test
    public void aRunningServiceIgnoresTheStoredEpisodeTypeAndUsesTheCurrentOne() {
        PlaybackPreferences.writeMediaPlaying(playableOfType(MediaType.VIDEO));
        PlaybackService.isRunning = true;

        Intent intent = PlaybackService.getPlayerActivityIntent(context);

        assertEquals(MainActivityStarter.INTENT, intent.getAction());
    }

    @Test
    public void nothingIsPlayingOrCastingWhileTheLegacyServiceIsUnused() {
        assertFalse(PlaybackService.isCasting());
        assertEquals(MediaType.UNKNOWN, PlaybackService.getCurrentMediaType());
    }
}
