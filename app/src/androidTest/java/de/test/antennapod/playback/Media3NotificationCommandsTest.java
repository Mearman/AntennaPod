package de.test.antennapod.playback;

import android.os.Bundle;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.test.filters.LargeTest;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.playback.service.internal.MediaLibrarySessionCallback;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.storage.preferences.SleepTimerType;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.awaitility.Awaitility;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Presses the buttons that the playback service puts on its notification and on the lock screen. The buttons are sent back to the service as the session commands that they carry.
 */
@LargeTest
public class Media3NotificationCommandsTest extends Media3ServiceTest {

    @Override
    protected String mediaFileName() {
        return "30sec.mp3";
    }

    @Test
    public void testNotificationAlwaysOffersRewindAndFastForward() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);

        List<String> names = buttonNames();

        assertTrue("Rewind button is offered, got " + names,
                names.contains(context.getString(R.string.rewind_label)));
        assertTrue("Fast forward button is offered, got " + names,
                names.contains(context.getString(R.string.fast_forward_label)));
    }

    @Test
    public void testNotificationOnlyOffersTheConfiguredOptionalButtons() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_PLAYBACK_SPEED));
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);

        List<String> names = buttonNames();

        assertTrue("Playback speed button is offered, got " + names,
                names.contains(context.getString(R.string.playback_speed)));
        assertEquals("Skip button is not offered, got " + names, false,
                names.contains(context.getString(R.string.skip_episode_label)));
        assertEquals("Sleep timer button is not offered, got " + names, false,
                names.contains(context.getString(R.string.sleep_timer_label)));
    }

    @Test
    public void testPlaybackSpeedButtonCyclesThroughTheConfiguredSpeeds() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_PLAYBACK_SPEED));
        UserPreferences.setPlaybackSpeedArray(Arrays.asList(1.0f, 2.0f));
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);
        awaitSpeed(1.0f);

        sendButton(context.getString(R.string.playback_speed));
        awaitSpeed(2.0f);

        sendButton(context.getString(R.string.playback_speed));
        awaitSpeed(1.0f);
    }

    @Test
    public void testNextChapterButtonStartsTheNextEpisodeWhenThereAreNoChapters() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_NEXT_CHAPTER));
        FeedMedia first = DBReader.getQueue().get(0).getMedia();
        FeedMedia second = DBReader.getQueue().get(1).getMedia();
        play(first);
        awaitCurrentMedia(first);
        awaitReady();

        sendButton(context.getString(R.string.next_chapter));

        awaitCurrentMedia(second);
    }

    @Test
    public void testSleepTimerButtonTurnsIntoTheButtonThatStopsTheTimer() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER));
        SleepTimerPreferences.setSleepTimerType(SleepTimerType.CLOCK);
        SleepTimerPreferences.setLastTimer("10");
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);
        awaitPlaying();
        assertEquals(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER.customAction,
                buttonCommand(context.getString(R.string.sleep_timer_label)).customAction);

        sendButton(context.getString(R.string.sleep_timer_label));

        Awaitility.await("notification offers to stop the running timer")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> MediaLibrarySessionCallback.SESSION_COMMAND_DISABLE_SLEEP_TIMER.customAction
                        .equals(buttonCommand(context.getString(R.string.sleep_timer_label)).customAction));
    }

    @Test
    public void testSkipSilenceCommandIsStoredForTheRunningEpisode() {
        FeedMedia media = DBReader.getQueue().get(0).getMedia();
        play(media);
        awaitCurrentMedia(media);
        awaitReady();

        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_SKIP_SILENCE,
                MediaLibrarySessionCallback.createBundle(true));

        Awaitility.await("skip silence stored for the running episode")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence()
                        == FeedPreferences.SkipSilence.AGGRESSIVE);

        sendCommand(MediaLibrarySessionCallback.SESSION_COMMAND_SKIP_SILENCE,
                MediaLibrarySessionCallback.createBundle(false));

        Awaitility.await("skip silence turned off again")
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> PlaybackPreferences.getCurrentlyPlayingTemporarySkipSilence()
                        == FeedPreferences.SkipSilence.OFF);
    }

    @Test
    public void testAnUnknownCommandIsRejected() {
        MediaController mediaController = controller();
        SessionResult result = Media3TestUtils.awaitFuture(Media3TestUtils.getOnMain(() ->
                mediaController.sendCustomCommand(
                        new SessionCommand("not_a_command", Bundle.EMPTY), Bundle.EMPTY)));

        assertEquals(SessionResult.RESULT_ERROR_PERMISSION_DENIED, result.resultCode);
    }

    private void awaitSpeed(float speed) {
        Awaitility.await("playback speed is " + speed)
                .atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Media3TestUtils.getOnMain(() ->
                        controller().getPlaybackParameters().speed) == speed);
    }

    private List<CommandButton> buttons() {
        return Media3TestUtils.getOnMain(() -> controller().getMediaButtonPreferences());
    }

    private List<String> buttonNames() {
        List<CommandButton> buttons = buttons();
        List<String> names = new ArrayList<>();
        for (CommandButton button : buttons) {
            names.add(button.displayName.toString());
        }
        return names;
    }

    private SessionCommand buttonCommand(String displayName) {
        for (CommandButton button : buttons()) {
            if (displayName.equals(button.displayName.toString())) {
                if (button.sessionCommand == null) {
                    fail("Button " + displayName + " carries no session command");
                }
                return button.sessionCommand;
            }
        }
        fail("Button " + displayName + " is missing from " + buttonNames());
        return null;
    }

    private void sendButton(String displayName) {
        sendCommand(buttonCommand(displayName), Bundle.EMPTY);
    }

    private void sendCommand(SessionCommand command, Bundle args) {
        MediaController mediaController = controller();
        SessionResult result = Media3TestUtils.awaitFuture(Media3TestUtils.getOnMain(() ->
                mediaController.sendCustomCommand(command, args)));
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode);
    }
}
