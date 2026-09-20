package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class MediaButtonReceiverTest {

    @Test
    public void aKeyPressIsNotForwardedToTheLegacyServiceWhileMedia3HandlesPlayback() {
        Context context = mock(Context.class);
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));

        new MediaButtonReceiver().onReceive(context, intent);

        verify(context, never()).startForegroundService(any());
        verify(context, never()).startService(any());
    }
}
