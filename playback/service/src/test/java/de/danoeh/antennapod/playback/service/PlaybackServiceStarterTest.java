package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.content.Intent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.internal.PlaybackTestDatabase;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceStarterTest {
    private Context context;
    private Feed feed;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        feed = PlaybackTestDatabase.storeFeed(context, "http://example.com/feed", "Main", 1);
    }

    @After
    public void tearDown() {
        PlaybackTestDatabase.tearDown();
    }

    private FeedMedia storedMedia() {
        return PlaybackTestDatabase.storedItems(feed.getId()).get(0).getMedia();
    }

    @Test
    public void theIntentCarriesTheStoredEpisodeToThePlaybackService() {
        FeedMedia media = storedMedia();

        Intent intent = new PlaybackServiceStarter(context, media).getIntent();

        assertEquals(PlaybackService.class.getName(), intent.getComponent().getClassName());
        FeedMedia extra = intent.getParcelableExtra(PlaybackServiceInterface.EXTRA_PLAYABLE);
        assertEquals(media.getId(), extra.getId());
        assertFalse(intent.getBooleanExtra(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME, true));
    }

    @Test
    public void askingToStreamThisTimeIsPassedOnToThePlaybackService() {
        Intent intent = new PlaybackServiceStarter(context, storedMedia())
                .shouldStreamThisTime(true)
                .getIntent();

        assertTrue(intent.getBooleanExtra(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME, false));
    }

    @Test
    public void theBuilderMethodsKeepReturningTheSameStarter() {
        PlaybackServiceStarter starter = new PlaybackServiceStarter(context, storedMedia());

        assertSame(starter, starter.callEvenIfRunning(true));
        assertSame(starter, starter.shouldStreamThisTime(true));
    }
}
