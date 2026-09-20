package de.test.antennapod.playback;

import android.content.Context;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.ui.UITestUtils;
import org.awaitility.Awaitility;
import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;

import java.util.concurrent.TimeUnit;

/**
 * Base class for tests that drive the Media3 playback service through a media session. Each test starts with an empty database that is filled with the feeds of the local test server and ends with a destroyed playback service, so that tests do not influence each other.
 */
public abstract class Media3ServiceTest {
    protected static final long TIMEOUT_SECONDS = 30;

    protected Context context;
    protected UITestUtils uiTestUtils;
    protected MediaController controller;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Media3TestUtils.awaitServiceStopped(context);
        DBWriter.tearDownTests();
        EventBus.getDefault().removeAllStickyEvents();
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        uiTestUtils = new UITestUtils(context);
        uiTestUtils.setMediaFileName(mediaFileName());
        uiTestUtils.setup();
        uiTestUtils.addLocalFeedData(downloadEpisodes());
    }

    @After
    public void tearDown() throws Exception {
        Media3TestUtils.stopPlaybackService(context, controller);
        controller = null;
        uiTestUtils.tearDown();
    }

    protected String mediaFileName() {
        return "3sec.mp3";
    }

    protected boolean downloadEpisodes() {
        return true;
    }

    protected MediaController controller() {
        if (controller == null) {
            controller = Media3TestUtils.connectController(context);
        }
        return controller;
    }

    protected void prepare(FeedMedia media) {
        MediaController mediaController = controller();
        Media3TestUtils.runOnMain(() -> {
            mediaController.setMediaItem(MediaItemAdapter.fromMediaIdStub(media.getId()));
            mediaController.prepare();
        });
    }

    protected void play(FeedMedia media) {
        prepare(media);
        Media3TestUtils.runOnMain(controller()::play);
    }

    protected void awaitCurrentMedia(FeedMedia media) {
        Awaitility.await("media " + media.getId() + " is the currently playing one")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == media.getId());
    }

    protected void awaitPlaying() {
        Awaitility.await("playback started")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller()::isPlaying));
    }

    protected void awaitReady() {
        Awaitility.await("player is ready")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(controller()::getPlaybackState) == Player.STATE_READY);
    }

    protected void awaitPositionAtLeast(long positionMs) {
        Awaitility.await("position " + positionMs + " reached")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() >= positionMs);
    }

    protected void setSkipKeepsEpisode(boolean value) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(UserPreferences.PREF_SKIP_KEEPS_EPISODE, value).commit();
    }

    protected void setSmartMarkAsPlayedSecs(int seconds) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(UserPreferences.PREF_SMART_MARK_AS_PLAYED_SECS, Integer.toString(seconds)).commit();
    }

    protected long position() {
        return Media3TestUtils.getOnMain(controller()::getCurrentPosition);
    }
}
