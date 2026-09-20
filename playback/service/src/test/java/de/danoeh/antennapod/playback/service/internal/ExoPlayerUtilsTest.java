package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import de.danoeh.antennapod.playback.service.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class ExoPlayerUtilsTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void aFailureCausedByALoopbackAddressIsExplainedAsABlockedDownload() {
        PlaybackException error = playbackException(
                new IOException("Failed to connect to /127.0.0.1:8080"));

        assertEquals(context.getString(R.string.download_error_blocked),
                ExoPlayerUtils.translateErrorReason(error, context));
    }

    @Test
    public void theMessageOfTheUnderlyingCauseIsWhatTheUserGetsToSee() {
        PlaybackException error = playbackException(new IOException("Unexpected end of stream"));

        assertEquals("Unexpected end of stream", ExoPlayerUtils.translateErrorReason(error, context));
    }

    @Test
    public void anHttpFailureIsUnwrappedToTheRealNetworkError() {
        HttpDataSource.HttpDataSourceException httpException = new HttpDataSource.HttpDataSourceException(
                new IOException("Name or service not known"),
                new DataSpec.Builder().setUri("http://example.com/e.mp3").build(),
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN);

        assertEquals("Name or service not known",
                ExoPlayerUtils.translateErrorReason(playbackException(httpException), context));
    }

    @Test
    public void theGenericSourceErrorWrapperIsSkippedInFavourOfWhatCausedIt() {
        PlaybackException error = playbackException(
                new IOException("Source error", new IOException("Response code: 404")));

        assertEquals("Response code: 404", ExoPlayerUtils.translateErrorReason(error, context));
    }

    @Test
    public void aCauseWithoutAMessageIsReportedAsTheErrorPlusTheCauseType() {
        PlaybackException error = playbackException(new IllegalStateException());

        assertEquals("Playback failed: IllegalStateException",
                ExoPlayerUtils.translateErrorReason(error, context));
    }

    @Test
    public void aFailureWithNothingToReportAtAllIsCalledUnknown() {
        PlaybackException error = new PlaybackException(null, null,
                PlaybackException.ERROR_CODE_UNSPECIFIED);

        assertEquals("Unknown error", ExoPlayerUtils.translateErrorReason(error, context));
    }

    private static PlaybackException playbackException(Throwable cause) {
        return new PlaybackException("Playback failed", cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }
}
