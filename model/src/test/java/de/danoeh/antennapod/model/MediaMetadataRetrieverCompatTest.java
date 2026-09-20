package de.danoeh.antennapod.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class MediaMetadataRetrieverCompatTest {

    @Test
    public void close_releasesRetriever() throws IOException {
        MediaMetadataRetrieverCompat retriever = spy(new MediaMetadataRetrieverCompat());
        retriever.close();
        verify(retriever).release();
    }

    @Test
    public void close_swallowsIoExceptionFromRelease() throws IOException {
        MediaMetadataRetrieverCompat retriever = spy(new MediaMetadataRetrieverCompat());
        doThrow(new IOException("release failed")).when(retriever).release();
        retriever.close();
        verify(retriever).release();
    }
}
