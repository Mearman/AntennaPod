package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import de.danoeh.antennapod.playback.service.R;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class ExoPlayerUtilsTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
    }

    @After
    public void tearDown() {
        ExoPlayerUtils.releaseCache();
        PlaybackTestDatabase.tearDown();
    }

    private static HttpDataSource.InvalidResponseCodeException responseCodeException(int code, String message) {
        return new HttpDataSource.InvalidResponseCodeException(code, message, null, Collections.emptyMap(),
                new DataSpec(Uri.parse("http://localhost/episode.mp3")), new byte[0]);
    }

    @Test
    public void theBuiltPlayerStartsIdleAtNormalSpeed() {
        ExoPlayer player = ExoPlayerUtils.buildPlayer(context);

        assertEquals(ExoPlayer.STATE_IDLE, player.getPlaybackState());
        assertEquals(1.0f, player.getPlaybackParameters().speed, 0.0001f);
        player.release();
    }

    @Test
    public void aPlayerCanStillBeBuiltAfterTheStreamingCacheWasReleased() {
        ExoPlayerUtils.buildPlayer(context).release();

        ExoPlayerUtils.releaseCache();
        ExoPlayer second = ExoPlayerUtils.buildPlayer(context);

        assertEquals(ExoPlayer.STATE_IDLE, second.getPlaybackState());
        second.release();
    }

    @Test
    public void aDownloadBlockedByALocalFilterIsReportedAsBlockedRatherThanAsANetworkError() {
        PlaybackException error = new PlaybackException("Source error",
                new IOException("Failed to connect to 127.0.0.1:80"),
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED);

        assertEquals(context.getString(R.string.download_error_blocked),
                ExoPlayerUtils.translateErrorReason(error, context));
    }

    @Test
    public void theInnermostCauseIsWhatTheUserGetsToSee() {
        PlaybackException error = new PlaybackException("outer",
                new IOException("Source error", new IOException("Disk is full")),
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED);

        assertEquals("Disk is full", ExoPlayerUtils.translateErrorReason(error, context));
    }

    @Test
    public void anHttpFailureIsUnwrappedToTheCauseBehindIt() {
        HttpDataSource.InvalidResponseCodeException httpError = responseCodeException(404, "Not Found");
        PlaybackException error = new PlaybackException("outer", httpError,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS);

        assertEquals(httpError.getMessage(), ExoPlayerUtils.translateErrorReason(error, context));
    }

    @Test
    public void anErrorWithoutAnyMessageAtAllStillProducesSomethingToShow() {
        PlaybackException error = new PlaybackException(null, null,
                PlaybackException.ERROR_CODE_UNSPECIFIED);

        assertEquals("Unknown error", ExoPlayerUtils.translateErrorReason(error, context));
    }
}
