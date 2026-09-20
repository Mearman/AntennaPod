package de.test.antennapod.playback;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.view.KeyEvent;
import androidx.media3.common.Player;
import androidx.preference.PreferenceManager;
import androidx.test.filters.LargeTest;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.Media3PlaybackService;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;
import org.awaitility.Awaitility;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@LargeTest
public class Media3MediaButtonTest extends Media3ServiceTest {

    @Override
    protected String mediaFileName() {
        return "30sec.mp3";
    }

    @Test
    public void testMediaButtonFastForwardSeeksForward() {
        UserPreferences.setFastForwardSecs(7);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        playAndPause(media);
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);

        Awaitility.await("seeked forward by the configured interval")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() >= 7000 && position() < 14000);
    }

    @Test
    public void testMediaButtonRewindSeeksBack() {
        UserPreferences.setRewindSecs(5);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        playAndPause(media);
        seekAndAwait(20000);
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_REWIND);

        Awaitility.await("seeked back by the configured interval")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() >= 14000 && position() <= 16000);
    }

    @Test
    public void testHeadsetPreviousButtonRestartsTheEpisode() {
        setHardwareButton(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        playAndPause(media);
        seekAndAwait(20000);
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS);

        Awaitility.await("restarted the episode")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() < 1000);
    }

    @Test
    public void testHeadsetPreviousButtonCanBeConfiguredToRewind() {
        setHardwareButton(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON, KeyEvent.KEYCODE_MEDIA_REWIND);
        UserPreferences.setRewindSecs(5);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        playAndPause(media);
        seekAndAwait(20000);
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS);

        Awaitility.await("rewound instead of restarting the episode")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() >= 14000 && position() <= 16000);
    }

    @Test
    public void testHeadsetForwardButtonCanBeConfiguredToFastForward() {
        setHardwareButton(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        UserPreferences.setFastForwardSecs(6);
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        playAndPause(media);
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT);

        Awaitility.await("fast forwarded instead of skipping the episode")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> position() >= 6000 && position() < 13000);
        assertEquals("The episode is still the current one",
                media.getId(), PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
    }

    @Test
    public void testHeadsetForwardButtonSkipsToTheNextEpisode() {
        setHardwareButton(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, KeyEvent.KEYCODE_MEDIA_NEXT);
        setSmartMarkAsPlayedSecs(0);
        setSkipKeepsEpisode(true);
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia first = queue.get(0).getMedia();
        FeedMedia second = queue.get(1).getMedia();

        playAndPause(first);
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT);

        awaitCurrentMedia(second);
        assertNotEquals(first.getId(), PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertTrue("Skipped episode stays in the queue",
                DBReader.getQueueIDList().contains(first.getItem().getId()));
        assertFalse("Skipped episode is not marked as played",
                DBReader.getFeedItem(first.getItem().getId()).isPlayed());
    }

    @Test
    public void testSkippedEpisodeLeavesTheQueueWhenConfigured() {
        setHardwareButton(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, KeyEvent.KEYCODE_MEDIA_NEXT);
        setSmartMarkAsPlayedSecs(0);
        setSkipKeepsEpisode(false);
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia first = queue.get(0).getMedia();
        FeedMedia second = queue.get(1).getMedia();

        playAndPause(first);
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT);

        awaitCurrentMedia(second);
        Awaitility.await("skipped episode removed from the queue")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> !DBReader.getQueueIDList().contains(first.getItem().getId()));
    }

    @Test
    public void testWidgetNextButtonSkipsTheEpisodeRegardlessOfTheHeadsetSetting() {
        setHardwareButton(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        setSmartMarkAsPlayedSecs(0);
        setSkipKeepsEpisode(true);
        List<FeedItem> queue = DBReader.getQueue();
        FeedMedia first = queue.get(0).getMedia();
        FeedMedia second = queue.get(1).getMedia();

        playAndPause(first);
        sendWidgetButton(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);

        awaitCurrentMedia(second);
        assertTrue("Skipped episode stays in the queue",
                DBReader.getQueueIDList().contains(first.getItem().getId()));
    }

    @Test
    public void testMediaButtonPauseStopsPlaybackAndPlayResumesIt() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();

        play(media);
        awaitCurrentMedia(media);
        awaitPlaying();
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_PAUSE);

        Awaitility.await("playback paused")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentPlayerStatus()
                        == PlaybackPreferences.PLAYER_STATUS_PAUSED);
        assertFalse(Media3TestUtils.getOnMain(controller()::isPlaying));

        sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY);

        Awaitility.await("playback resumed")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentPlayerStatus()
                        == PlaybackPreferences.PLAYER_STATUS_PLAYING);
    }

    private void playAndPause(FeedMedia media) {
        play(media);
        awaitCurrentMedia(media);
        awaitPlaying();
        pausePlayback();
    }

    private void seekAndAwait(long positionMs) {
        Media3TestUtils.runOnMain(() -> controller().seekTo(positionMs));
        awaitPositionAtLeast(positionMs);
    }

    private void sendMediaButton(int keyCode) {
        Intent intent = MediaButtonStarter.createIntent(context, keyCode);
        intent.setComponent(new ComponentName(context, Media3PlaybackService.class));
        context.startService(intent);
    }

    private void sendWidgetButton(int command) {
        PendingIntent pendingIntent = MediaButtonStarter.createPendingIntent(context, command);
        try {
            pendingIntent.send();
        } catch (PendingIntent.CanceledException e) {
            throw new AssertionError("Sending the widget button failed", e);
        }
    }

    private void setHardwareButton(String preference, int keyCode) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(preference, String.valueOf(keyCode)).commit();
    }
}
