package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.os.Looper;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.robolectric.Shadows.shadowOf;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class ExoPlayerWrapperTest {
    private ExoPlayerWrapper player;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        player = new ExoPlayerWrapper(context);
    }

    @After
    public void tearDown() {
        player.release();
        PlaybackTestDatabase.tearDown();
    }

    @Test
    public void aFreshPlayerIsStoppedAtTheStartWithNoKnownDuration() {
        assertFalse(player.isPlaying());
        assertEquals(0, player.getCurrentPosition());
        assertEquals(Playable.INVALID_TIME, player.getDuration());
        assertEquals(1.0f, player.getCurrentSpeedMultiplier(), 0.0001f);
        assertFalse(player.getCurrentSkipSilence());
    }

    @Test
    public void startingAndPausingFlipsThePlayingFlag() {
        player.start();
        assertTrue(player.isPlaying());

        player.pause();

        assertFalse(player.isPlaying());
    }

    @Test
    public void thePlaybackSpeedAndSkipSilenceSurviveAPause() {
        player.setPlaybackParams(1.75f, true);

        player.pause();
        player.start();

        assertEquals(1.75f, player.getCurrentSpeedMultiplier(), 0.0001f);
        assertTrue(player.getCurrentSkipSilence());
    }

    @Test
    public void seekingNotifiesTheSeekListenerWithTheNewPosition() {
        AtomicReference<Integer> seekedTo = new AtomicReference<>();
        player.setOnSeekCompleteListener(() -> seekedTo.set(player.getCurrentPosition()));

        player.seekTo(30000);

        assertEquals(Integer.valueOf(30000), seekedTo.get());
        assertEquals(30000, player.getCurrentPosition());
    }

    @Test
    public void seekingWithoutAListenerStillMovesThePlayer() {
        player.seekTo(12000);

        assertEquals(12000, player.getCurrentPosition());
    }

    @Test
    public void aPlayerWithoutAnyLoadedTrackReportsNoAudioTracks() {
        assertTrue(player.getAudioTracks().isEmpty());
        assertEquals(-1, player.getSelectedAudioTrack());
    }

    @Test
    public void selectingATrackOnAPlayerWithoutTracksIsIgnored() {
        player.setAudioTrack(0);

        assertEquals(-1, player.getSelectedAudioTrack());
    }

    @Test
    public void aPlayerWithoutAVideoTrackReportsNoVideoSize() {
        assertEquals(0, player.getVideoWidth());
        assertEquals(0, player.getVideoHeight());
    }

    @Test
    public void resettingThePlayerBringsItBackToItsInitialState() {
        player.setPlaybackParams(2.0f, true);
        player.seekTo(5000);

        player.reset();

        assertEquals(0, player.getCurrentPosition());
        assertFalse(player.isPlaying());
        assertEquals(Playable.INVALID_TIME, player.getDuration());
    }

    @Test
    public void releasingThePlayerForgetsTheSeekListener() {
        AtomicReference<String> seeks = new AtomicReference<>("never seeked");
        player.setOnSeekCompleteListener(() -> seeks.set("seeked"));

        player.release();
        player.seekTo(1000);

        assertEquals("never seeked", seeks.get());
        player = new ExoPlayerWrapper(RuntimeEnvironment.getApplication());
    }

    @Test
    public void aStreamedEpisodeOfAFeedWithCredentialsSendsThemToTheServer() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("not really audio"));
        server.start();
        player.setDataSource(server.url("/episode.mp3").toString(), "user", "secret");

        player.prepare();
        shadowOf(Looper.getMainLooper()).idle();
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);

        assertNotNull(request);
        assertEquals("Basic dXNlcjpzZWNyZXQ=", request.getHeader("Authorization"));
        server.shutdown();
    }
}
