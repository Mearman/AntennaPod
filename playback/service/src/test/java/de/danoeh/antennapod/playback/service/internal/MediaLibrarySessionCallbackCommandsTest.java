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
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent;
import de.danoeh.antennapod.model.playback.TimerValue;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;
import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class MediaLibrarySessionCallbackCommandsTest {
    private MediaLibrarySessionCallback callback;
    private MediaSession session;
    private MediaSession.ControllerInfo controllerInfo;
    private Player player;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        UserPreferences.setFullNotificationButtons(Collections.emptyList());
        EventBus.getDefault().removeAllStickyEvents();
        callback = new MediaLibrarySessionCallback(context);
        session = mock(MediaSession.class);
        controllerInfo = mock(MediaSession.ControllerInfo.class);
        player = mock(Player.class);
        when(session.getPlayer()).thenReturn(player);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().removeAllStickyEvents();
        UserPreferences.setFullNotificationButtons(Collections.emptyList());
        setPreference(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON,
                String.valueOf(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD));
        setPreference(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON,
                String.valueOf(KeyEvent.KEYCODE_MEDIA_REWIND));
    }

    @Test
    public void aBooleanArgumentSurvivesTheRoundTripThroughACommandBundle() {
        assertTrue(MediaLibrarySessionCallback.getBoolean(MediaLibrarySessionCallback.createBundle(true), false));
        assertFalse(MediaLibrarySessionCallback.getBoolean(MediaLibrarySessionCallback.createBundle(false), true));
    }

    @Test
    public void aLongArgumentSurvivesTheRoundTripThroughACommandBundle() {
        assertEquals(900000L, MediaLibrarySessionCallback.getLong(
                MediaLibrarySessionCallback.createBundle(900000L), 0));
    }

    @Test
    public void aCommandWithoutArgumentsFallsBackToTheCallersDefault() {
        assertTrue(MediaLibrarySessionCallback.getBoolean(null, true));
        assertEquals(42L, MediaLibrarySessionCallback.getLong(null, 42L));
    }

    @Test
    public void aConnectingControllerMayUseEveryCustomCommandTheServiceImplements() {
        MediaSession.ConnectionResult result = callback.onConnect(session, controllerInfo);

        assertTrue(result.availableSessionCommands.contains(MediaLibrarySessionCallback.SESSION_COMMAND_REWIND));
        assertTrue(result.availableSessionCommands.contains(MediaLibrarySessionCallback.SESSION_COMMAND_FAST_FORWARD));
        assertTrue(result.availableSessionCommands.contains(
                MediaLibrarySessionCallback.SESSION_COMMAND_PLAYBACK_SPEED));
        assertTrue(result.availableSessionCommands.contains(MediaLibrarySessionCallback.SESSION_COMMAND_NEXT_CHAPTER));
        assertTrue(result.availableSessionCommands.contains(MediaLibrarySessionCallback.SESSION_COMMAND_SKIP_SILENCE));
        assertTrue(result.availableSessionCommands.contains(
                MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER));
        assertTrue(result.availableSessionCommands.contains(
                MediaLibrarySessionCallback.SESSION_COMMAND_DISABLE_SLEEP_TIMER));
        assertTrue(result.availableSessionCommands.contains(
                MediaLibrarySessionCallback.SESSION_COMMAND_EXTEND_SLEEP_TIMER));
    }

    @Test
    public void seekingToThePreviousEpisodeIsWithheldSoThatRewindKeepsThatSlot() {
        MediaSession.ConnectionResult result = callback.onConnect(session, controllerInfo);

        assertFalse(result.availablePlayerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS));
        assertFalse(result.availablePlayerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM));
        assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM));
    }

    @Test
    public void rewindAndFastForwardAreAlwaysOfferedAsNotificationButtons() {
        MediaSession.ConnectionResult result = callback.onConnect(session, controllerInfo);

        assertEquals(2, result.mediaButtonPreferences.size());
        assertSame(MediaLibrarySessionCallback.SESSION_COMMAND_REWIND,
                result.mediaButtonPreferences.get(0).sessionCommand);
        assertSame(MediaLibrarySessionCallback.SESSION_COMMAND_FAST_FORWARD,
                result.mediaButtonPreferences.get(1).sessionCommand);
    }

    @Test
    public void theEnabledOptionalButtonsAreAddedAfterTheFixedOnes() {
        UserPreferences.setFullNotificationButtons(Arrays.asList(
                UserPreferences.NOTIFICATION_BUTTON_PLAYBACK_SPEED,
                UserPreferences.NOTIFICATION_BUTTON_NEXT_CHAPTER,
                UserPreferences.NOTIFICATION_BUTTON_SKIP,
                UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER));

        List<CommandButton> buttons = callback.onConnect(session, controllerInfo).mediaButtonPreferences;

        assertEquals(6, buttons.size());
        assertSame(MediaLibrarySessionCallback.SESSION_COMMAND_PLAYBACK_SPEED, buttons.get(2).sessionCommand);
        assertSame(MediaLibrarySessionCallback.SESSION_COMMAND_NEXT_CHAPTER, buttons.get(3).sessionCommand);
        assertEquals(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, buttons.get(4).playerCommand);
    }

    @Test
    public void theSleepTimerButtonStartsATimerWhileNoneIsRunning() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER));

        List<CommandButton> buttons = callback.onConnect(session, controllerInfo).mediaButtonPreferences;

        assertEquals(3, buttons.size());
        assertSame(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, buttons.get(2).sessionCommand);
    }

    @Test
    public void theSleepTimerButtonCancelsTheTimerWhileOneIsRunning() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER));
        EventBus.getDefault().postSticky(SleepTimerUpdatedEvent.updated(
                new TimerValue(TimeUnit.MINUTES.toMillis(10), TimeUnit.MINUTES.toMillis(10))));

        List<CommandButton> buttons = callback.onConnect(session, controllerInfo).mediaButtonPreferences;

        assertSame(MediaLibrarySessionCallback.SESSION_COMMAND_DISABLE_SLEEP_TIMER, buttons.get(2).sessionCommand);
    }

    @Test
    public void anExpiredSleepTimerLeavesTheButtonOfferingToStartANewOne() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER));
        EventBus.getDefault().postSticky(SleepTimerUpdatedEvent.updated(new TimerValue(0, 0)));

        List<CommandButton> buttons = callback.onConnect(session, controllerInfo).mediaButtonPreferences;

        assertSame(MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, buttons.get(2).sessionCommand);
    }

    @Test
    public void connectingAControllerPushesTheCurrentButtonsToTheSession() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_SKIP));

        callback.onPostConnect(session, controllerInfo);

        ArgumentCaptor<ImmutableList<CommandButton>> buttons = ArgumentCaptor.forClass(ImmutableList.class);
        verify(session).setMediaButtonPreferences(buttons.capture());
        assertEquals(3, buttons.getValue().size());
    }

    @Test
    public void theRewindCommandSeeksBackwardsAndReportsSuccess() throws Exception {
        SessionResult result = callback.onCustomCommand(session, controllerInfo,
                MediaLibrarySessionCallback.SESSION_COMMAND_REWIND, Bundle.EMPTY).get();

        verify(player).seekBack();
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode);
    }

    @Test
    public void theFastForwardCommandSeeksForwardAndReportsSuccess() throws Exception {
        SessionResult result = callback.onCustomCommand(session, controllerInfo,
                MediaLibrarySessionCallback.SESSION_COMMAND_FAST_FORWARD, Bundle.EMPTY).get();

        verify(player).seekForward();
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode);
    }

    @Test
    public void aCommandTheSessionDoesNotHandleItselfIsRejected() throws Exception {
        SessionResult result = callback.onCustomCommand(session, controllerInfo,
                new SessionCommand("something_else", Bundle.EMPTY), Bundle.EMPTY).get();

        assertNotEquals(SessionResult.RESULT_SUCCESS, result.resultCode);
        verifyNoInteractions(player);
    }

    @Test
    public void theFastForwardKeySeeksForward() {
        assertTrue(callback.onMediaButtonEvent(session, controllerInfo,
                mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, false)));

        verify(player).seekForward();
    }

    @Test
    public void theRewindKeySeeksBackwards() {
        assertTrue(callback.onMediaButtonEvent(session, controllerInfo,
                mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_REWIND, false)));

        verify(player).seekBack();
    }

    @Test
    public void theWidgetSkipButtonAlwaysMovesToTheNextEpisode() {
        setPreference(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, String.valueOf(KeyEvent.KEYCODE_MEDIA_REWIND));

        assertTrue(callback.onMediaButtonEvent(session, controllerInfo,
                mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT, true)));

        verify(player).seekToNextMediaItem();
    }

    @Test
    public void aHeadsetDoubleTapFollowsTheConfiguredForwardAction() {
        setPreference(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, String.valueOf(KeyEvent.KEYCODE_MEDIA_NEXT));

        assertTrue(callback.onMediaButtonEvent(session, controllerInfo,
                mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT, false)));

        verify(player).seekToNextMediaItem();
    }

    @Test
    public void aHeadsetTripleTapFollowsTheConfiguredPreviousAction() {
        setPreference(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON,
                String.valueOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS));

        assertTrue(callback.onMediaButtonEvent(session, controllerInfo,
                mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, false)));

        verify(player).seekTo(0);
    }

    @Test
    public void anUnrecognisedHardwareButtonActionFallsBackToFastForward() {
        setPreference(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON, String.valueOf(KeyEvent.KEYCODE_UNKNOWN));

        assertTrue(callback.onMediaButtonEvent(session, controllerInfo,
                mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, false)));

        verify(player).seekForward();
    }

    @Test
    public void aHardwareButtonMappedToRewindSeeksBackwards() {
        setPreference(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, String.valueOf(KeyEvent.KEYCODE_MEDIA_REWIND));

        assertTrue(callback.onMediaButtonEvent(session, controllerInfo,
                mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT, false)));

        verify(player).seekBack();
    }

    @Test
    public void releasingAKeyIsLeftToTheDefaultHandling() {
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT,
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD));

        assertFalse(callback.onMediaButtonEvent(session, controllerInfo, intent));
        verifyNoInteractions(player);
    }

    @Test
    public void aRepeatedKeyPressIsLeftToTheDefaultHandling() {
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 1));

        assertFalse(callback.onMediaButtonEvent(session, controllerInfo, intent));
        verifyNoInteractions(player);
    }

    @Test
    public void anIntentWithoutAKeyEventIsLeftToTheDefaultHandling() {
        assertFalse(callback.onMediaButtonEvent(session, controllerInfo, new Intent(Intent.ACTION_MEDIA_BUTTON)));

        verifyNoInteractions(player);
    }

    @Test
    public void playPauseIsLeftToTheDefaultHandling() {
        assertFalse(callback.onMediaButtonEvent(session, controllerInfo,
                mediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false)));

        verifyNoInteractions(player);
    }

    private static void setPreference(String key, String value) {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
                .edit().putString(key, value).apply();
    }

    private static Intent mediaButtonIntent(int keyCode, boolean fromWidget) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        if (fromWidget) {
            intent.putExtra(MediaButtonStarter.EXTRA_MEDIA_BUTTON_SOURCE,
                    MediaButtonStarter.MEDIA_BUTTON_SOURCE_WIDGET);
        }
        return intent;
    }
}
