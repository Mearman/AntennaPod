package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PlayableUtilsTest {
    private static final String FEED_URL = "http://example.com/feed";
    private Context context;
    private Feed feed;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        feed = PlaybackTestDatabase.storeFeed(context, FEED_URL, "Main", 1);
    }

    @After
    public void tearDown() {
        PlaybackTestDatabase.tearDown();
    }

    private FeedMedia storedMedia() {
        return PlaybackTestDatabase.storedItems(feed.getId()).get(0).getMedia();
    }

    private void waitForDatabase() throws ExecutionException, InterruptedException {
        DBWriter.clearDownloadLog().get();
    }

    @Test
    public void savingThePositionStoresItAlongsideTheTimeItWasLastPlayed()
            throws ExecutionException, InterruptedException {
        FeedMedia media = storedMedia();
        long timestamp = System.currentTimeMillis();

        PlayableUtils.saveCurrentPosition(media, 42000, timestamp);
        waitForDatabase();

        FeedMedia reloaded = DBReader.getFeedMedia(media.getId());
        assertEquals(42000, reloaded.getPosition());
        assertEquals(timestamp, reloaded.getLastPlayedTimeStatistics());
        assertEquals(timestamp, reloaded.getLastPlayedTimeHistory().getTime());
    }

    private FeedMedia storedNewEpisode() throws ExecutionException, InterruptedException {
        FeedItem item = PlaybackTestDatabase.storedItems(feed.getId()).get(0);
        DBWriter.markItemsPlayed(FeedItem.NEW, false, Collections.singletonList(item)).get();
        FeedMedia reloaded = PlaybackTestDatabase.storedItems(feed.getId()).get(0).getMedia();
        assertTrue(reloaded.getItem().isNew());
        return reloaded;
    }

    @Test
    public void startingANewEpisodeFromItsBeginningTakesItOutOfTheNewStateInTheDatabase()
            throws ExecutionException, InterruptedException {
        FeedMedia media = storedNewEpisode();

        PlayableUtils.saveCurrentPosition(media, 0, System.currentTimeMillis());
        waitForDatabase();

        assertFalse(DBReader.getFeedItem(media.getItem().getId()).isNew());
    }

    @Test
    public void savingAPositionInsideANewEpisodeAlreadyClearsItsNewFlag()
            throws ExecutionException, InterruptedException {
        FeedMedia media = storedNewEpisode();

        PlayableUtils.saveCurrentPosition(media, 1000, System.currentTimeMillis());

        assertFalse(media.getItem().isNew());
    }

    @Test
    public void listeningTimeIsAccumulatedFromThePositionPlaybackStartedAt() {
        FeedMedia media = storedMedia();
        media.setPosition(10000);
        media.onPlaybackStart();

        PlayableUtils.saveCurrentPosition(media, 25000, System.currentTimeMillis());

        assertEquals(15000, media.getPlayedDuration());
    }

    @Test
    public void noListeningTimeIsAddedWhenTheListenerSeekedBackBeforeWhereTheyStarted() {
        FeedMedia media = storedMedia();
        media.setPosition(30000);
        media.onPlaybackStart();

        PlayableUtils.saveCurrentPosition(media, 5000, System.currentTimeMillis());

        assertEquals(0, media.getPlayedDuration());
    }

    @Test
    public void noListeningTimeIsAddedWhenPlaybackNeverProperlyStarted() {
        FeedMedia media = storedMedia();

        PlayableUtils.saveCurrentPosition(media, 5000, System.currentTimeMillis());

        assertEquals(0, media.getPlayedDuration());
        assertEquals(5000, media.getPosition());
    }
}
