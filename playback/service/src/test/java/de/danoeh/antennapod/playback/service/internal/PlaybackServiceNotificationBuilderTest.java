package de.danoeh.antennapod.playback.service.internal;

import android.app.Notification;
import android.content.Context;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.base.PlayerStatus;
import de.danoeh.antennapod.playback.service.MediaButtonReceiver;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.playback.service.R;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceNotificationBuilderTest {
    private Context context;
    private PlaybackServiceNotificationBuilder builder;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        UserPreferences.setFullNotificationButtons(Collections.emptyList());
        builder = new PlaybackServiceNotificationBuilder(context);
    }

    @After
    public void tearDown() {
        UserPreferences.setFullNotificationButtons(Collections.emptyList());
    }

    @Test
    public void aNotificationWithoutAnEpisodeTellsTheUserThatLoadingIsStillGoingOn() {
        Notification notification = builder.build();

        assertEquals(context.getString(R.string.app_name), notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Loading. If this does not go away, play any episode and contact us.",
                notification.extras.getString(Notification.EXTRA_TEXT));
        assertNull(notification.actions);
    }

    @Test
    public void aNotificationForAnEpisodeShowsTheFeedAndEpisodeTitle() {
        builder.setPlayable(playable("Feed title", "Episode title"));

        Notification notification = builder.build();

        assertEquals("Feed title", notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Episode title", notification.extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void rewindPlayAndFastForwardAreAlwaysOffered() {
        builder.setPlayable(playable("Feed title", "Episode title"));
        builder.setPlayerStatus(PlayerStatus.PAUSED);

        Notification notification = builder.build();

        assertEquals(3, notification.actions.length);
        assertEquals(context.getString(R.string.rewind_label), notification.actions[0].title);
        assertEquals(context.getString(R.string.play_label), notification.actions[1].title);
        assertEquals(context.getString(R.string.fast_forward_label), notification.actions[2].title);
    }

    @Test
    public void aPlayingEpisodeOffersPauseInsteadOfPlay() {
        builder.setPlayable(playable("Feed title", "Episode title"));
        builder.setPlayerStatus(PlayerStatus.PLAYING);

        Notification notification = builder.build();

        assertEquals(context.getString(R.string.pause_label), notification.actions[1].title);
    }

    @Test
    public void theSkipButtonIsOnlyAddedWhenTheUserAskedForIt() {
        builder.setPlayable(playable("Feed title", "Episode title"));
        builder.setPlayerStatus(PlayerStatus.PLAYING);
        assertEquals(3, builder.build().actions.length);

        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_SKIP));

        Notification notification = builder.build();

        assertEquals(4, notification.actions.length);
        assertEquals(context.getString(R.string.skip_episode_label), notification.actions[3].title);
    }

    @Test
    public void theNextChapterButtonIsOnlyAddedForAnEpisodeThatHasChapters() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_NEXT_CHAPTER));
        builder.setPlayable(playableWithoutChapters());
        builder.setPlayerStatus(PlayerStatus.PLAYING);
        assertEquals(3, builder.build().actions.length);

        builder.setPlayable(playableWithChapters());

        Notification notification = builder.build();

        assertEquals(4, notification.actions.length);
        assertEquals(context.getString(R.string.next_chapter), notification.actions[3].title);
    }

    @Test
    public void theNotificationIsRaisedToTheTopWhenTheUserAskedForAnExpandedOne() {
        builder.setPlayable(playable("Feed title", "Episode title"));
        builder.setPlayerStatus(PlayerStatus.PLAYING);
        int defaultPriority = builder.build().priority;

        context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean(UserPreferences.PREF_EXPANDED_NOTIFICATION, true).apply();

        assertTrue(builder.build().priority > defaultPriority);
    }

    @Test
    public void noIconIsCachedBeforeOneWasLoaded() {
        assertFalse(builder.isIconCached());
        assertNull(builder.getCachedIcon());
    }

    @Test
    @Config(sdk = 28)
    public void switchingToAnotherEpisodeDropsWhateverWasCachedForThePreviousOne() {
        builder.setPlayable(playable("Feed title", "Episode title"));
        builder.setPlayerStatus(PlayerStatus.PLAYING);
        builder.updatePosition(60000, 1.0f);
        assertEquals("00:01:00", builder.build().extras.getString(Notification.EXTRA_SUB_TEXT));

        builder.setPlayable(playable("Other feed", "Other episode"));

        assertNull(builder.build().extras.getString(Notification.EXTRA_SUB_TEXT));
        assertFalse(builder.isIconCached());
        assertNull(builder.getCachedIcon());
    }

    @Test
    public void thePlayerStatusThatWasSetIsTheOneReportedBack() {
        builder.setPlayerStatus(PlayerStatus.SEEKING);

        assertEquals(PlayerStatus.SEEKING, builder.getPlayerStatus());
    }

    @Test
    @Config(sdk = 28)
    public void versionsWithoutAMediaStyleProgressBarShowThePositionAsSubText() {
        builder.setPlayable(playable("Feed title", "Episode title"));
        builder.setPlayerStatus(PlayerStatus.PLAYING);
        builder.updatePosition(3723000, 1.0f);

        Notification notification = builder.build();

        assertEquals("01:02:03", notification.extras.getString(Notification.EXTRA_SUB_TEXT));
    }

    @Test
    @Config(sdk = 28)
    public void thePositionShownIsShortenedByThePlaybackSpeedWhenTheUserAskedForThat() {
        context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean("prefPlaybackTimeRespectsSpeed", true).apply();
        builder.setPlayable(playable("Feed title", "Episode title"));
        builder.setPlayerStatus(PlayerStatus.PLAYING);
        builder.updatePosition(3600000, 2.0f);

        Notification notification = builder.build();

        assertEquals("00:30:00", notification.extras.getString(Notification.EXTRA_SUB_TEXT));
    }

    @Test
    public void artworkThatCannotBeLoadedLeavesTheNotificationWithoutACachedIcon() {
        Playable playable = playable("Feed title", "Episode title");
        when(playable.getImageLocation())
                .thenReturn(new File(context.getCacheDir(), "no-such-cover.jpg").getAbsolutePath());
        builder.setPlayable(playable);
        builder.setPlayerStatus(PlayerStatus.PLAYING);

        builder.loadIcon();

        verify(playable, times(1)).getImageLocation();
        assertFalse(builder.isIconCached());
        assertNull(builder.getCachedIcon());
        assertEquals("Episode title", builder.build().extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void theNotificationIsTiedToTheMediaSessionItWasGiven() {
        builder.setPlayable(playable("Feed title", "Episode title"));
        builder.setPlayerStatus(PlayerStatus.PLAYING);
        assertNull(builder.build().extras.getParcelable(Notification.EXTRA_MEDIA_SESSION));

        MediaSessionCompat session = new MediaSessionCompat(context, "PlaybackServiceNotificationBuilderTest");
        builder.setMediaSessionToken(session.getSessionToken());

        assertNotNull(builder.build().extras.getParcelable(Notification.EXTRA_MEDIA_SESSION));
        session.release();
    }

    @Test
    @Config(sdk = 25)
    public void olderVersionsStartThePlaybackServiceAsAPlainServiceFromTheButtons() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_NEXT_CHAPTER));
        builder.setPlayable(playableWithChapters());
        builder.setPlayerStatus(PlayerStatus.PLAYING);

        Notification notification = builder.build();

        assertEquals(4, notification.actions.length);
        for (Notification.Action action : notification.actions) {
            assertTrue(shadowOf(action.actionIntent).isService());
            assertFalse(shadowOf(action.actionIntent).isForegroundService());
        }
        assertEquals(KeyEvent.KEYCODE_MEDIA_REWIND, shadowOf(notification.actions[0].actionIntent)
                .getSavedIntent().getIntExtra(MediaButtonReceiver.EXTRA_KEYCODE, 0));
        assertEquals(PlaybackService.CUSTOM_ACTION_NEXT_CHAPTER, shadowOf(notification.actions[3].actionIntent)
                .getSavedIntent().getStringExtra(MediaButtonReceiver.EXTRA_CUSTOM_ACTION));
    }

    @Test
    public void newerVersionsStartThePlaybackServiceInTheForegroundFromTheButtons() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_NEXT_CHAPTER));
        builder.setPlayable(playableWithChapters());
        builder.setPlayerStatus(PlayerStatus.PLAYING);

        Notification notification = builder.build();

        assertEquals(4, notification.actions.length);
        for (Notification.Action action : notification.actions) {
            assertTrue(shadowOf(action.actionIntent).isForegroundService());
            assertFalse(shadowOf(action.actionIntent).isService());
        }
    }

    private static Playable playable(String feedTitle, String episodeTitle) {
        Playable playable = mock(Playable.class);
        when(playable.getFeedTitle()).thenReturn(feedTitle);
        when(playable.getEpisodeTitle()).thenReturn(episodeTitle);
        return playable;
    }

    private static Playable playableWithoutChapters() {
        Playable playable = playable("Feed title", "Episode title");
        when(playable.getChapters()).thenReturn(null);
        return playable;
    }

    private static Playable playableWithChapters() {
        Playable playable = playable("Feed title", "Episode title");
        List<Chapter> chapters = Collections.singletonList(new Chapter(0, "Chapter", null, null));
        when(playable.getChapters()).thenReturn(chapters);
        return playable;
    }
}
