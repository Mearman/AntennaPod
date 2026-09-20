package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import androidx.media3.common.Player;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.preference.PreferenceManager;
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent;
import de.danoeh.antennapod.model.playback.TimerValue;
import de.danoeh.antennapod.playback.service.R;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;
import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class MediaLibrarySessionCommandsTest {
    private Context context;
    private MediaLibrarySessionCallback callback;
    private final MediaSession session = mock(MediaSession.class);
    private final MediaSession.ControllerInfo controller = mock(MediaSession.ControllerInfo.class);
    private Player player;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        player = mock(Player.class);
        when(session.getPlayer()).thenReturn(player);
        callback = new MediaLibrarySessionCallback(context);
    }

    @After
    public void tearDown() {
        PlaybackTestDatabase.tearDown();
    }

    private static Intent mediaButtonIntent(int keyCode, int action, String source) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(action, keyCode));
        if (source != null) {
            intent.putExtra(MediaButtonStarter.EXTRA_MEDIA_BUTTON_SOURCE, source);
        }
        return intent;
    }

    private List<String> customLayoutDisplayNames() {
        List<String> names = new ArrayList<>();
        for (CommandButton button : callback.onConnect(session, controller).mediaButtonPreferences) {
            names.add(String.valueOf(button.displayName));
        }
        return names;
    }

    @Test
    public void aConnectingControllerMayUseEveryAntennaPodSpecificCommand() {
        MediaSession.ConnectionResult result = callback.onConnect(session, controller);

        assertTrue(result.availableSessionCommands.contains(
                MediaLibrarySessionCallback.SESSION_COMMAND_SKIP_SILENCE));
        assertTrue(result.availableSessionCommands.contains(
                MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER));
        assertTrue(result.availableSessionCommands.contains(
                MediaLibrarySessionCallback.SESSION_COMMAND_DISABLE_SLEEP_TIMER));
        assertTrue(result.availableSessionCommands.contains(
                MediaLibrarySessionCallback.SESSION_COMMAND_EXTEND_SLEEP_TIMER));
    }

    @Test
    public void seekingToThePreviousEpisodeIsNotOfferedBecauseAntennaPodRewindsInstead() {
        MediaSession.ConnectionResult result = callback.onConnect(session, controller);

        assertFalse(result.availablePlayerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM));
        assertFalse(result.availablePlayerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS));
        assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM));
    }

    @Test
    public void rewindAndFastForwardAreAlwaysOfferedOnTheNotification() {
        UserPreferences.setFullNotificationButtons(Collections.emptyList());

        List<String> names = customLayoutDisplayNames();

        assertEquals(Arrays.asList(context.getString(R.string.rewind_label),
                context.getString(R.string.fast_forward_label)), names);
    }

    @Test
    public void theNotificationOffersExactlyTheButtonsTheUserEnabled() {
        UserPreferences.setFullNotificationButtons(Arrays.asList(
                UserPreferences.NOTIFICATION_BUTTON_PLAYBACK_SPEED,
                UserPreferences.NOTIFICATION_BUTTON_SKIP));

        List<String> names = customLayoutDisplayNames();

        assertTrue(names.contains(context.getString(
                R.string.playback_speed)));
        assertTrue(names.contains(context.getString(
                R.string.skip_episode_label)));
        assertFalse(names.contains(context.getString(
                R.string.next_chapter)));
    }

    @Test
    public void theSleepTimerButtonTurnsIntoADisableButtonWhileATimerIsRunning() {
        UserPreferences.setFullNotificationButtons(Collections.singletonList(
                UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER));
        EventBus.getDefault().postSticky(SleepTimerUpdatedEvent.updated(new TimerValue(5, 300000)));

        ImmutableList<CommandButton> buttons = callback.onConnect(session, controller).mediaButtonPreferences;

        CommandButton sleepButton = buttons.get(buttons.size() - 1);
        assertEquals(MediaLibrarySessionCallback.SESSION_COMMAND_DISABLE_SLEEP_TIMER.customAction,
                sleepButton.sessionCommand.customAction);
    }

    @Test
    public void theSleepTimerButtonStartsATimerWhenNoneIsRunning() {
        UserPreferences.setFullNotificationButtons(Collections.singletonList(
                UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER));
        EventBus.getDefault().postSticky(SleepTimerUpdatedEvent.cancelled());

        ImmutableList<CommandButton> buttons = callback.onConnect(session, controller).mediaButtonPreferences;

        CommandButton sleepButton = buttons.get(buttons.size() - 1);
        assertEquals(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER.customAction,
                sleepButton.sessionCommand.customAction);
    }

    @Test
    public void theRewindCommandSeeksBackAndReportsSuccess() throws ExecutionException, InterruptedException {
        SessionResult result = callback.onCustomCommand(session, controller,
                new SessionCommand("rewind", Bundle.EMPTY), Bundle.EMPTY).get();

        verify(player).seekBack();
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode);
    }

    @Test
    public void theFastForwardCommandSeeksForwardAndReportsSuccess()
            throws ExecutionException, InterruptedException {
        SessionResult result = callback.onCustomCommand(session, controller,
                new SessionCommand("fast_forward", Bundle.EMPTY), Bundle.EMPTY).get();

        verify(player).seekForward();
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode);
    }

    @Test
    public void anUnknownCommandIsRejectedWithoutTouchingThePlayer()
            throws ExecutionException, InterruptedException {
        SessionResult result = callback.onCustomCommand(session, controller,
                new SessionCommand("does_not_exist", Bundle.EMPTY), Bundle.EMPTY).get();

        assertFalse(result.resultCode == SessionResult.RESULT_SUCCESS);
        verifyNoInteractions(player);
    }

    @Test
    public void theHardwareFastForwardKeySeeksForward() {
        assertTrue(callback.onMediaButtonEvent(session, controller, mediaButtonIntent(
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.ACTION_DOWN, null)));

        verify(player).seekForward();
    }

    @Test
    public void theHardwareRewindKeySeeksBack() {
        assertTrue(callback.onMediaButtonEvent(session, controller, mediaButtonIntent(
                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.ACTION_DOWN, null)));

        verify(player).seekBack();
    }

    @Test
    public void theWidgetNextButtonAlwaysSkipsToTheNextEpisode() {
        assertTrue(callback.onMediaButtonEvent(session, controller, mediaButtonIntent(
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN,
                MediaButtonStarter.MEDIA_BUTTON_SOURCE_WIDGET)));

        verify(player).seekToNextMediaItem();
    }

    private void setHardwareButtonAction(String preferenceKey, int keyCode) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(preferenceKey, String.valueOf(keyCode)).commit();
    }

    @Test
    public void aHeadsetDoubleTapFollowsTheConfiguredForwardButtonAction() {
        setHardwareButtonAction(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, KeyEvent.KEYCODE_MEDIA_NEXT);

        assertTrue(callback.onMediaButtonEvent(session, controller, mediaButtonIntent(
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, null)));

        verify(player).seekToNextMediaItem();
    }

    @Test
    public void aHeadsetTripleTapConfiguredToRestartJumpsToTheStartOfTheEpisode() {
        setHardwareButtonAction(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON, KeyEvent.KEYCODE_MEDIA_PREVIOUS);

        assertTrue(callback.onMediaButtonEvent(session, controller, mediaButtonIntent(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.ACTION_DOWN, null)));

        verify(player).seekTo(0);
    }

    @Test
    public void aHeadsetTripleTapConfiguredToRewindSeeksBack() {
        setHardwareButtonAction(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON, KeyEvent.KEYCODE_MEDIA_REWIND);

        assertTrue(callback.onMediaButtonEvent(session, controller, mediaButtonIntent(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.ACTION_DOWN, null)));

        verify(player).seekBack();
    }

    @Test
    public void aKeyReleaseIsLeftToTheDefaultHandling() {
        assertFalse(callback.onMediaButtonEvent(session, controller, mediaButtonIntent(
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.ACTION_UP, null)));

        verifyNoInteractions(player);
    }

    @Test
    public void aKeyWithoutAnAntennaPodMeaningIsLeftToTheDefaultHandling() {
        assertFalse(callback.onMediaButtonEvent(session, controller, mediaButtonIntent(
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.ACTION_DOWN, null)));

        verifyNoInteractions(player);
    }

    @Test
    public void anIntentWithoutAKeyEventIsLeftToTheDefaultHandling() {
        assertFalse(callback.onMediaButtonEvent(session, controller, new Intent(Intent.ACTION_MEDIA_BUTTON)));

        verifyNoInteractions(player);
    }

    @Test
    public void commandArgumentsSurviveTheRoundTripThroughABundle() {
        assertTrue(MediaLibrarySessionCallback.getBoolean(
                MediaLibrarySessionCallback.createBundle(true), false));
        assertFalse(MediaLibrarySessionCallback.getBoolean(
                MediaLibrarySessionCallback.createBundle(false), true));
        assertEquals(900000L, MediaLibrarySessionCallback.getLong(
                MediaLibrarySessionCallback.createBundle(900000L), 0));
    }

    @Test
    public void missingCommandArgumentsFallBackToTheCallersDefault() {
        assertTrue(MediaLibrarySessionCallback.getBoolean(null, true));
        assertTrue(MediaLibrarySessionCallback.getBoolean(Bundle.EMPTY, true));
        assertEquals(42L, MediaLibrarySessionCallback.getLong(null, 42L));
        assertEquals(42L, MediaLibrarySessionCallback.getLong(Bundle.EMPTY, 42L));
    }
}
