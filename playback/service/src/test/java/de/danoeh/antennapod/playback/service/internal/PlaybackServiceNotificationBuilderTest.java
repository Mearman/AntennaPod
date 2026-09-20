package de.danoeh.antennapod.playback.service.internal;

import android.app.Notification;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.base.PlayerStatus;
import de.danoeh.antennapod.playback.service.R;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceNotificationBuilderTest {
    private static final String FEED_URL = "http://example.com/feed";
    private Context context;
    private Feed feed;
    private PlaybackServiceNotificationBuilder builder;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        feed = PlaybackTestDatabase.storeFeed(context, FEED_URL, "Main", 1);
        builder = new PlaybackServiceNotificationBuilder(context);
    }

    @After
    public void tearDown() {
        PlaybackTestDatabase.tearDown();
    }

    private FeedMedia storedMedia() {
        return PlaybackTestDatabase.storedItems(feed.getId()).get(0).getMedia();
    }

    private static List<String> actionTitles(Notification notification) {
        List<String> titles = new ArrayList<>();
        for (Notification.Action action : notification.actions) {
            titles.add(String.valueOf(action.title));
        }
        return titles;
    }

    @Test
    public void aNotificationWithoutAnEpisodeAsksTheUserToStartOneAndOffersNoControls() {
        Notification notification = builder.build();

        assertEquals(context.getString(R.string.app_name),
                notification.extras.getString(NotificationCompat.EXTRA_TITLE));
        assertNull(notification.actions);
    }

    @Test
    public void aNotificationForAnEpisodeShowsTheFeedTitleAndTheEpisodeTitle() {
        builder.setPlayable(storedMedia());
        builder.setPlayerStatus(PlayerStatus.PLAYING);

        Notification notification = builder.build();

        assertEquals("Main", notification.extras.getString(NotificationCompat.EXTRA_TITLE));
        assertEquals("Main Episode 0", notification.extras.getString(NotificationCompat.EXTRA_TEXT));
    }

    @Test
    public void aPlayingEpisodeOffersPauseBetweenRewindAndFastForward() {
        UserPreferences.setFullNotificationButtons(Collections.emptyList());
        builder.setPlayable(storedMedia());
        builder.setPlayerStatus(PlayerStatus.PLAYING);

        List<String> titles = actionTitles(builder.build());

        assertEquals(Arrays.asList(context.getString(R.string.rewind_label),
                context.getString(R.string.pause_label),
                context.getString(R.string.fast_forward_label)), titles);
    }

    @Test
    public void aPausedEpisodeOffersPlayInsteadOfPause() {
        UserPreferences.setFullNotificationButtons(Collections.emptyList());
        builder.setPlayable(storedMedia());
        builder.setPlayerStatus(PlayerStatus.PAUSED);

        List<String> titles = actionTitles(builder.build());

        assertTrue(titles.contains(context.getString(R.string.play_label)));
        assertFalse(titles.contains(context.getString(R.string.pause_label)));
    }

    @Test
    public void theSkipButtonIsAddedOnlyWhenTheUserAskedForIt() {
        builder.setPlayable(storedMedia());
        builder.setPlayerStatus(PlayerStatus.PLAYING);

        UserPreferences.setFullNotificationButtons(Collections.emptyList());
        assertFalse(actionTitles(builder.build()).contains(context.getString(R.string.skip_episode_label)));

        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_SKIP));
        assertTrue(actionTitles(builder.build()).contains(context.getString(R.string.skip_episode_label)));
    }

    @Test
    public void theNextChapterButtonIsAddedOnlyForAnEpisodeThatActuallyHasChapters() {
        UserPreferences.setFullNotificationButtons(
                Collections.singletonList(UserPreferences.NOTIFICATION_BUTTON_NEXT_CHAPTER));
        FeedMedia withoutChapters = storedMedia();
        builder.setPlayable(withoutChapters);
        builder.setPlayerStatus(PlayerStatus.PLAYING);

        assertFalse(actionTitles(builder.build()).contains(context.getString(R.string.next_chapter)));

        FeedMedia withChapters = storedMedia();
        withChapters.setChapters(new ArrayList<Chapter>());
        builder.setPlayable(withChapters);

        assertTrue(actionTitles(builder.build()).contains(context.getString(R.string.next_chapter)));
    }

    @Test
    public void switchingToAnotherEpisodeThrowsAwayTheCachedArtworkOfThePreviousOne() {
        builder.setPlayable(storedMedia());
        builder.loadIcon();

        builder.setPlayable(storedMedia());

        assertFalse(builder.isIconCached());
        assertNull(builder.getCachedIcon());
    }

    @Test
    public void anEpisodeThatIsNotPlayingRightNowOffersPlayEvenWhileItIsSeeking() {
        UserPreferences.setFullNotificationButtons(Collections.emptyList());
        builder.setPlayable(storedMedia());
        builder.setPlayerStatus(PlayerStatus.SEEKING);

        List<String> titles = actionTitles(builder.build());

        assertEquals(PlayerStatus.SEEKING, builder.getPlayerStatus());
        assertTrue(titles.contains(context.getString(R.string.play_label)));
        assertFalse(titles.contains(context.getString(R.string.pause_label)));
    }

    @Test
    @Config(sdk = 28)
    public void theCurrentPositionIsShownUnderTheEpisodeTitleOnOlderAndroidVersions() {
        builder.setPlayable(storedMedia());
        builder.setPlayerStatus(PlayerStatus.PLAYING);

        builder.updatePosition(60000, 1.0f);

        assertEquals("00:01:00", builder.build().extras.getString(NotificationCompat.EXTRA_SUB_TEXT));
    }

    @Test
    public void theCurrentPositionIsLeftToTheSystemOnModernAndroidVersions() {
        builder.setPlayable(storedMedia());
        builder.setPlayerStatus(PlayerStatus.PLAYING);

        builder.updatePosition(60000, 1.0f);

        assertNull(builder.build().extras.getString(NotificationCompat.EXTRA_SUB_TEXT));
    }
}
