package de.test.antennapod.service.download;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateManagerImpl;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateWorker;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.awaitility.Awaitility;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FeedUpdateManagerTest {
    private static final long TIMEOUT_SECONDS = 60;

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private final List<MessageEvent> messages = new CopyOnWriteArrayList<>();
    private final List<FeedUpdateRunningEvent> runningEvents = new CopyOnWriteArrayList<>();
    private Context context;
    private WorkManager workManager;
    private FeedUpdateManager manager;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        resetManualRefreshCooldown();
        workManager = WorkManager.getInstance(context);
        manager = FeedUpdateManager.getInstance();
        EventBus.getDefault().register(this);
    }

    private void resetManualRefreshCooldown() {
        ((FeedUpdateManagerImpl) FeedUpdateManager.getInstance()).resetManualRefreshCooldown();
    }

    @After
    public void tearDown() throws Exception {
        EventBus.getDefault().unregister(this);
        EventBus.getDefault().removeAllStickyEvents();
        workManager.cancelAllWorkByTag(FeedUpdateManagerImpl.WORK_TAG_FEED_UPDATE);
        workManager.cancelAllWorkByTag(FeedUpdateWorker.class.getName());
        workManager.pruneWork().getResult().get();
        fixture.tearDown();
    }

    @Subscribe(sticky = true)
    public void onMessage(MessageEvent event) {
        messages.add(event);
    }

    @Subscribe(sticky = true)
    public void onFeedUpdateRunning(FeedUpdateRunningEvent event) {
        runningEvents.add(event);
    }

    private Feed subscribeWithoutNewestEpisode(String title) throws Exception {
        Feed feed = fixture.newFeed(title, 3);
        feed.setDownloadUrl(fixture.hostFeed(feed));
        feed.getItems().remove(0);
        return fixture.subscribe(feed);
    }

    private void awaitEpisodeCount(Feed feed, int count) {
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE).getItems().size() == count);
    }

    private List<WorkInfo> periodicWork() throws Exception {
        return workManager.getWorkInfosByTag(FeedUpdateWorker.class.getName()).get();
    }

    @Test
    public void runOnceRefreshesASingleFeed() throws Exception {
        Feed feed = subscribeWithoutNewestEpisode("Single");
        Feed other = subscribeWithoutNewestEpisode("Other");

        manager.runOnce(context, feed);

        awaitEpisodeCount(feed, 3);
        assertEquals(2, DBReader.getFeed(other.getId(), false, 0, Integer.MAX_VALUE).getItems().size());
    }

    @Test
    public void runOnceRefreshesEveryFeed() throws Exception {
        Feed first = subscribeWithoutNewestEpisode("First");
        Feed second = subscribeWithoutNewestEpisode("Second");

        manager.runOnce(context);

        awaitEpisodeCount(first, 3);
        awaitEpisodeCount(second, 3);
    }

    @Test
    public void runOnceOrAskRefreshesWhenTheNetworkAllowsIt() throws Exception {
        UserPreferences.setAllowMobileFeedRefresh(true);
        Feed feed = subscribeWithoutNewestEpisode("Asked");
        manager.runOnce(context);
        awaitEpisodeCount(feed, 3);
        Feed later = subscribeWithoutNewestEpisode("Asked later");

        manager.runOnceOrAsk(context, later);

        awaitEpisodeCount(later, 3);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void runOnceOrAskRefreshesEveryFeedWithoutAFeed() throws Exception {
        UserPreferences.setAllowMobileFeedRefresh(true);
        Feed feed = subscribeWithoutNewestEpisode("Everything");
        manager.runOnce(context, feed);
        awaitEpisodeCount(feed, 3);
        Feed later = subscribeWithoutNewestEpisode("Everything later");

        manager.runOnceOrAsk(context);

        awaitEpisodeCount(later, 3);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void runOnceOrAskAsksToWaitAfterARecentRefreshOfTheSameFeed() throws Exception {
        Feed feed = subscribeWithoutNewestEpisode("Cooldown");
        manager.runOnce(context, feed);

        manager.runOnceOrAsk(context, feed);

        assertEquals(1, messages.size());
        assertEquals(context.getString(R.string.please_wait_before_refreshing), messages.get(0).message);
        assertFalse(runningEvents.get(runningEvents.size() - 1).isFeedUpdateRunning);
    }

    @Test
    public void periodicUpdateIsScheduledWhileAutomaticUpdatesAreEnabled() throws Exception {
        UserPreferences.setUpdateInterval(TimeUnit.HOURS.toMinutes(12));

        manager.restartUpdateAlarm(context, true);

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> {
            for (WorkInfo info : periodicWork()) {
                if (info.getState() == WorkInfo.State.ENQUEUED) {
                    return true;
                }
            }
            return false;
        });
    }

    @Test
    public void periodicUpdateIsCancelledWhenAutomaticUpdatesAreDisabled() throws Exception {
        UserPreferences.setUpdateInterval(TimeUnit.HOURS.toMinutes(12));
        manager.restartUpdateAlarm(context, true);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> !periodicWork().isEmpty());

        UserPreferences.setUpdateInterval(0);
        manager.restartUpdateAlarm(context, false);

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> {
            for (WorkInfo info : periodicWork()) {
                if (info.getState() == WorkInfo.State.ENQUEUED) {
                    return false;
                }
            }
            return true;
        });
    }
}
