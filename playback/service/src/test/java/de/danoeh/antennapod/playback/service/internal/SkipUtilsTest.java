package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class SkipUtilsTest {
    private static final String FEED_URL = "http://example.com/feed";
    private Context context;
    private Feed feed;
    private final List<MessageEvent> messages = new ArrayList<>();

    @Subscribe
    public void onMessage(MessageEvent event) {
        messages.add(event);
    }

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        feed = PlaybackTestDatabase.storeFeed(context, FEED_URL, "Main", 1);
        EventBus.getDefault().register(this);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(this);
        PlaybackTestDatabase.tearDown();
    }

    private FeedMedia storedMedia() {
        return PlaybackTestDatabase.storedItems(feed.getId()).get(0).getMedia();
    }

    private void storeSkipSettings(int skipIntro, int skipEnding)
            throws ExecutionException, InterruptedException {
        FeedPreferences preferences = feed.getPreferences();
        preferences.setFeedSkipIntro(skipIntro);
        preferences.setFeedSkipEnding(skipEnding);
        DBWriter.setFeedPreferences(preferences).get();
    }

    @Test
    public void anEpisodeOfAFeedWithoutASkipIntroStartsWhereItWasLeftOff() {
        FeedMedia media = storedMedia();
        media.setPosition(45000);

        assertEquals(45000, SkipUtils.skipIntroIfNecessary(context, media));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void aFreshEpisodeOfAFeedWithASkipIntroStartsAfterTheIntroAndTellsTheUser()
            throws ExecutionException, InterruptedException {
        storeSkipSettings(30, 0);

        long startPosition = SkipUtils.skipIntroIfNecessary(context, storedMedia());

        assertEquals(30000, startPosition);
        assertEquals(1, messages.size());
        assertEquals("Skipped first 30 seconds", messages.get(0).message);
    }

    @Test
    public void anEpisodeAlreadyPlayedPastItsIntroIsNotRewoundToTheIntroMark()
            throws ExecutionException, InterruptedException {
        storeSkipSettings(30, 0);
        FeedMedia media = storedMedia();
        media.setPosition(60000);

        assertEquals(60000, SkipUtils.skipIntroIfNecessary(context, media));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void anIntroLongerThanTheEpisodeItselfIsIgnored() throws ExecutionException, InterruptedException {
        storeSkipSettings(6000, 0);
        FeedMedia media = storedMedia();

        assertEquals(0, SkipUtils.skipIntroIfNecessary(context, media));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void anIntroIsStillSkippedWhenTheEpisodeDurationIsUnknown()
            throws ExecutionException, InterruptedException {
        storeSkipSettings(30, 0);
        FeedMedia media = storedMedia();
        media.setDuration(0);

        assertEquals(30000, SkipUtils.skipIntroIfNecessary(context, media));
    }

    @Test
    public void theEndingIsSkippedOnceTheRemainingTimeFallsInsideTheConfiguredEnding()
            throws ExecutionException, InterruptedException {
        storeSkipSettings(0, 20);
        FeedMedia media = storedMedia();

        boolean skipped = SkipUtils.skipEndingIfNecessary(context, media, 579500, 600000, 1.0f);

        assertTrue(skipped);
        assertEquals(1, messages.size());
        assertEquals("Skipped last 20 seconds", messages.get(0).message);
    }

    @Test
    public void theEndingIsNotSkippedWhileThereIsStillMoreThanTheEndingLeft()
            throws ExecutionException, InterruptedException {
        storeSkipSettings(0, 20);

        assertFalse(SkipUtils.skipEndingIfNecessary(context, storedMedia(), 300000, 600000, 1.0f));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void theEndingIsNotSkippedOnceItHasAlreadyBeenPassed()
            throws ExecutionException, InterruptedException {
        storeSkipSettings(0, 20);

        assertFalse(SkipUtils.skipEndingIfNecessary(context, storedMedia(), 599000, 600000, 1.0f));
    }

    @Test
    public void aFasterPlaybackSpeedSkipsTheEndingEarlier()
            throws ExecutionException, InterruptedException {
        storeSkipSettings(0, 20);
        FeedMedia media = storedMedia();

        assertFalse(SkipUtils.skipEndingIfNecessary(context, media, 578500, 600000, 1.0f));
        assertTrue(SkipUtils.skipEndingIfNecessary(context, media, 578500, 600000, 2.0f));
    }

    @Test
    public void anEndingLongerThanTheEpisodeItselfIsIgnored() throws ExecutionException, InterruptedException {
        storeSkipSettings(0, 6000);

        assertFalse(SkipUtils.skipEndingIfNecessary(context, storedMedia(), 599000, 600000, 1.0f));
    }

    @Test
    public void aFeedWithNoSkipEndingConfiguredNeverSkipsTheEnding() {
        assertFalse(SkipUtils.skipEndingIfNecessary(context, storedMedia(), 599500, 600000, 1.0f));
    }
}
