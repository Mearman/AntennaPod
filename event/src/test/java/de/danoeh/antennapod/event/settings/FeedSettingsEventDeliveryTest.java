package de.danoeh.antennapod.event.settings;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
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
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import static org.junit.Assert.assertEquals;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class FeedSettingsEventDeliveryTest {
    private final List<Object> received = new ArrayList<>();
    private Feed feed;

    @Subscribe
    public void onSkipIntroEndingChanged(SkipIntroEndingChangedEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onSpeedPresetChanged(SpeedPresetChangedEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onVolumeAdaptionChanged(VolumeAdaptionChangedEvent event) {
        received.add(event);
    }

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
        AutoDownloadManager.setInstance(new AutoDownloadManager() {
            @Override
            public Future<?> autodownloadUndownloadedItems(Context ctx) {
                FutureTask<Void> done = new FutureTask<>(() -> null);
                done.run();
                return done;
            }

            @Override
            public void performAutoCleanup(Context ctx) {
            }
        });
        Feed newFeed = new Feed("http://example.com/feed", null, "Feed");
        newFeed.setItems(new ArrayList<>());
        feed = FeedDatabaseWriter.updateFeed(context, newFeed, false);
        EventBus.getDefault().register(this);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(this);
        PodDBAdapter.tearDownTests();
    }

    @Test
    public void skipIntroEndingEventReportsTheValuesThatWerePersistedForTheFeed()
            throws ExecutionException, InterruptedException {
        FeedPreferences preferences = feed.getPreferences();
        preferences.setFeedSkipIntro(30);
        preferences.setFeedSkipEnding(45);
        DBWriter.setFeedPreferences(preferences).get();

        FeedPreferences stored = DBReader.getFeed(feed.getId(), false, 0, 0).getPreferences();
        EventBus.getDefault().post(new SkipIntroEndingChangedEvent(
                stored.getFeedSkipIntro(), stored.getFeedSkipEnding(), feed.getId()));

        SkipIntroEndingChangedEvent event = (SkipIntroEndingChangedEvent) received.get(0);
        assertEquals(30, event.getSkipIntro());
        assertEquals(45, event.getSkipEnding());
        assertEquals(feed.getId(), event.getFeedId());
    }

    @Test
    public void speedPresetEventReportsThePlaybackSpeedAndSkipSilencePersistedForTheFeed()
            throws ExecutionException, InterruptedException {
        FeedPreferences preferences = feed.getPreferences();
        preferences.setFeedPlaybackSpeed(1.5f);
        preferences.setFeedSkipSilence(FeedPreferences.SkipSilence.AGGRESSIVE);
        DBWriter.setFeedPreferences(preferences).get();

        FeedPreferences stored = DBReader.getFeed(feed.getId(), false, 0, 0).getPreferences();
        EventBus.getDefault().post(new SpeedPresetChangedEvent(
                stored.getFeedPlaybackSpeed(), feed.getId(), stored.getFeedSkipSilence()));

        SpeedPresetChangedEvent event = (SpeedPresetChangedEvent) received.get(0);
        assertEquals(1.5f, event.getSpeed(), 0.0001f);
        assertEquals(FeedPreferences.SkipSilence.AGGRESSIVE, event.getSkipSilence());
        assertEquals(feed.getId(), event.getFeedId());
    }

    @Test
    public void volumeAdaptionEventReportsTheSettingPersistedForTheFeed()
            throws ExecutionException, InterruptedException {
        FeedPreferences preferences = feed.getPreferences();
        preferences.setVolumeAdaptionSetting(VolumeAdaptionSetting.HEAVY_REDUCTION);
        DBWriter.setFeedPreferences(preferences).get();

        FeedPreferences stored = DBReader.getFeed(feed.getId(), false, 0, 0).getPreferences();
        EventBus.getDefault().post(new VolumeAdaptionChangedEvent(
                stored.getVolumeAdaptionSetting(), feed.getId()));

        VolumeAdaptionChangedEvent event = (VolumeAdaptionChangedEvent) received.get(0);
        assertEquals(VolumeAdaptionSetting.HEAVY_REDUCTION, event.getVolumeAdaptionSetting());
        assertEquals(feed.getId(), event.getFeedId());
    }
}
