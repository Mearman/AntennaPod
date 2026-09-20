package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.content.Intent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceStarterTest {
    private Context context;
    private FeedMedia media;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Feed feed = new Feed(1, null, "Feed", "http://example.com", "d", null, null, null, null,
                "id", null, null, "http://example.com/feed.xml", 0);
        FeedItem item = new FeedItem(7, "Episode", "id7", "http://example.com", new Date(0),
                FeedItem.UNPLAYED, feed);
        media = new FeedMedia(7, item, 300000, 0, 1, "audio/mp3", null,
                "http://example.com/e.mp3", 0, null, 0, 0);
        item.setMedia(media);
    }

    @Test
    public void theLaunchIntentTargetsThePlaybackServiceAndCarriesTheEpisode() {
        Intent intent = new PlaybackServiceStarter(context, media).getIntent();

        assertEquals(PlaybackService.class.getName(), intent.getComponent().getClassName());
        assertSame(media, intent.getParcelableExtra(PlaybackServiceInterface.EXTRA_PLAYABLE));
    }

    @Test
    public void streamingIsNotAllowedUnlessItWasRequestedForThisStart() {
        Intent intent = new PlaybackServiceStarter(context, media).getIntent();

        assertFalse(intent.getBooleanExtra(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME, true));
    }

    @Test
    public void requestingStreamingForThisStartIsCarriedInTheIntent() {
        Intent intent = new PlaybackServiceStarter(context, media)
                .shouldStreamThisTime(true)
                .getIntent();

        assertTrue(intent.getBooleanExtra(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME, false));
    }

    @Test
    public void theConfigurationCallsReturnTheSameStarterSoTheyCanBeChained() {
        PlaybackServiceStarter starter = new PlaybackServiceStarter(context, media);

        assertSame(starter, starter.callEvenIfRunning(true));
        assertSame(starter, starter.shouldStreamThisTime(true));
    }
}
