package de.danoeh.antennapod.playback.service.internal;

import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class WearMediaSessionTest {
    private static final String EXTRA_SHOW_ON_WEAR =
            "android.support.wearable.media.extra.CUSTOM_ACTION_SHOW_ON_WEAR";
    private static final String EXTRA_RESERVE_PREVIOUS =
            "android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_PREVIOUS";
    private static final String EXTRA_RESERVE_NEXT =
            "android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_NEXT";

    @Test
    public void aCustomActionIsMarkedToBeShownOnWearDevices() {
        PlaybackStateCompat.CustomAction.Builder builder =
                new PlaybackStateCompat.CustomAction.Builder("rewind", "Rewind", 1);

        WearMediaSession.addWearExtrasToAction(builder);

        Bundle extras = builder.build().getExtras();
        assertTrue(extras.getBoolean(EXTRA_SHOW_ON_WEAR));
    }

    @Test
    public void theSessionReservesNeitherOfTheWearSkipSlotsForItsOwnCustomActions() {
        MediaSessionCompat session = mock(MediaSessionCompat.class);

        WearMediaSession.mediaSessionSetExtraForWear(session);

        ArgumentCaptor<Bundle> extras = ArgumentCaptor.forClass(Bundle.class);
        verify(session).setExtras(extras.capture());
        assertTrue(extras.getValue().containsKey(EXTRA_RESERVE_PREVIOUS));
        assertTrue(extras.getValue().containsKey(EXTRA_RESERVE_NEXT));
        assertFalse(extras.getValue().getBoolean(EXTRA_RESERVE_PREVIOUS));
        assertFalse(extras.getValue().getBoolean(EXTRA_RESERVE_NEXT));
    }
}
