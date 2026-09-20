package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import de.danoeh.antennapod.playback.service.R;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class WearMediaSessionTest {
    private static final String SHOW_ON_WEAR =
            "android.support.wearable.media.extra.CUSTOM_ACTION_SHOW_ON_WEAR";
    private MediaSessionCompat session;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        session = new MediaSessionCompat(context, "WearMediaSessionTest");
    }

    @After
    public void tearDown() {
        session.release();
        PlaybackTestDatabase.tearDown();
    }

    @Test
    public void aCustomActionIsMarkedToShowOnTheWatch() {
        PlaybackStateCompat.CustomAction.Builder builder = new PlaybackStateCompat.CustomAction.Builder(
                "rewind", "Rewind", R.drawable.ic_notification_fast_rewind);

        WearMediaSession.addWearExtrasToAction(builder);

        assertTrue(builder.build().getExtras().getBoolean(SHOW_ON_WEAR));
    }

}
