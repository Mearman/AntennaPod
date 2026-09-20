package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import de.danoeh.antennapod.playback.service.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

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

    @Test
    public void releasingACacheThatWasNeverOpenedIsHarmless() {
        ExoPlayerUtils.releaseCache();
        ExoPlayerUtils.releaseCache();
    }

    @Test
    public void theMediaSourceFactoryHandlesTheSameContainerFormatsAsTheDefaultOne() {
        ExoPlayerUtils.ApMediaSourceFactory factory = new ExoPlayerUtils.ApMediaSourceFactory(context, null);

        assertArrayEquals(new DefaultMediaSourceFactory(context).getSupportedTypes(), factory.getSupportedTypes());
    }

    @Test
    public void configuringTheMediaSourceFactoryReturnsItSoTheCallsCanBeChained() {
        ExoPlayerUtils.ApMediaSourceFactory factory = new ExoPlayerUtils.ApMediaSourceFactory(context, null);

        assertSame(factory, factory.setDrmSessionManagerProvider(mock(DrmSessionManagerProvider.class)));
        assertSame(factory, factory.setLoadErrorHandlingPolicy(mock(LoadErrorHandlingPolicy.class)));
    }

    @Test
    public void aDownloadedEpisodeIsPlayedStraightFromTheLocalFile() {
        ExoPlayerUtils.ApMediaSourceFactory factory = new ExoPlayerUtils.ApMediaSourceFactory(context, null);
        MediaItem item = new MediaItem.Builder()
                .setUri(Uri.fromFile(new File(context.getCacheDir(), "episode.mp3")))
                .setMediaId("7")
                .build();

        MediaSource source = factory.createMediaSource(item);

        assertEquals("7", source.getMediaItem().mediaId);
    }

    @Test
    public void theConfiguredErrorPolicyAndDrmProviderAreHandedToTheSourceThatIsBuilt() {
        ExoPlayerUtils.ApMediaSourceFactory factory = new ExoPlayerUtils.ApMediaSourceFactory(context, null);
        factory.setDrmSessionManagerProvider(mediaItem -> DrmSessionManager.DRM_UNSUPPORTED);
        factory.setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy());
        MediaItem item = new MediaItem.Builder()
                .setUri(Uri.fromFile(new File(context.getCacheDir(), "episode.mp3")))
                .setMediaId("7")
                .build();

        assertNotNull(factory.createMediaSource(item));
    }

    private static PlaybackException playbackException(Throwable cause) {
        return new PlaybackException("Playback failed", cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }
}
