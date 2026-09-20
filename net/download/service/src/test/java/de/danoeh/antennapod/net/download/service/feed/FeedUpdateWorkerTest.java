package de.danoeh.antennapod.net.download.service.feed;

import android.app.Notification;
import android.content.Context;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.download.service.R;
import de.danoeh.antennapod.net.download.service.WorkManagerMocks;
import de.danoeh.antennapod.net.download.service.feed.remote.DefaultDownloaderFactory;
import de.danoeh.antennapod.net.download.service.feed.remote.Downloader;
import de.danoeh.antennapod.net.download.service.feed.remote.FeedParserTask;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequestBuilder;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequestCreator;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.parser.feed.FeedHandlerResult;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.NonSubscribedFeedsCleaner;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(RobolectricTestRunner.class)
public class FeedUpdateWorkerTest {
    private static final String SOURCE = "http://example.com/feed.xml";
    private static final String DESTINATION = "/nonexistent-directory/feed.xml";
    private static final long FEED_ID = 12;

    private final Context context = RuntimeEnvironment.getApplication();
    private final AutoDownloadManager autoDownload = Mockito.mock(AutoDownloadManager.class);
    private final SynchronizationQueue syncQueue = Mockito.mock(SynchronizationQueue.class);
    private final Downloader downloader = Mockito.mock(Downloader.class);
    private final FeedHandlerResult parsed = new FeedHandlerResult(new Feed(SOURCE, null, "Parsed"),
            Collections.emptyMap(), null);
    private MockedStatic<UserPreferences> preferences;
    private MockedStatic<NetworkUtils> network;
    private MockedStatic<DBReader> reader;
    private MockedStatic<DBWriter> writer;
    private MockedStatic<DownloadRequestCreator> requestCreator;
    private MockedStatic<FeedDatabaseWriter> databaseWriter;
    private MockedStatic<NonSubscribedFeedsCleaner> cleaner;
    private MockedStatic<SynchronizationQueue> queue;
    private MockedConstruction<NewEpisodesNotification> notification;
    private MockedConstruction<DefaultDownloaderFactory> factory;
    private MockedConstruction<FeedParserTask> parserTask;
    private boolean parseSucceeds = true;
    private DownloadResult parserStatus;
    private Feed savedFeed;

    @Before
    public void setUp() {
        savedFeed = new Feed(SOURCE, null, "Saved");
        parserStatus = new DownloadResult("Feed", FEED_ID, Feed.FEEDFILETYPE_FEED, true, DownloadError.SUCCESS, null);
        preferences = Mockito.mockStatic(UserPreferences.class);
        network = Mockito.mockStatic(NetworkUtils.class);
        reader = Mockito.mockStatic(DBReader.class);
        writer = Mockito.mockStatic(DBWriter.class);
        requestCreator = Mockito.mockStatic(DownloadRequestCreator.class);
        databaseWriter = Mockito.mockStatic(FeedDatabaseWriter.class);
        cleaner = Mockito.mockStatic(NonSubscribedFeedsCleaner.class);
        queue = Mockito.mockStatic(SynchronizationQueue.class);
        queue.when(SynchronizationQueue::getInstance).thenReturn(syncQueue);
        AutoDownloadManager.setInstance(autoDownload);
        notification = Mockito.mockConstruction(NewEpisodesNotification.class);
        factory = Mockito.mockConstruction(DefaultDownloaderFactory.class, (mock, ctx) ->
                Mockito.when(mock.create(any(DownloadRequest.class))).thenReturn(downloader));
        parserTask = Mockito.mockConstruction(FeedParserTask.class, (mock, ctx) -> {
            Mockito.when(mock.call()).thenReturn(parsed);
            Mockito.when(mock.isSuccessful()).thenAnswer(invocation -> parseSucceeds);
            Mockito.when(mock.getDownloadStatus()).thenAnswer(invocation -> parserStatus);
        });
        databaseWriter.when(() -> FeedDatabaseWriter.updateFeed(any(Context.class), any(Feed.class), anyBoolean()))
                .thenReturn(savedFeed);
        Mockito.when(downloader.getResult()).thenReturn(
                new DownloadResult("Feed", FEED_ID, Feed.FEEDFILETYPE_FEED, true, DownloadError.SUCCESS, null));
        reader.when(() -> DBReader.getFeedDownloadLog(anyLong(), anyLong())).thenReturn(new ArrayList<>());
    }

    @After
    public void tearDown() {
        parserTask.close();
        factory.close();
        notification.close();
        AutoDownloadManager.setInstance(null);
        queue.close();
        cleaner.close();
        databaseWriter.close();
        requestCreator.close();
        writer.close();
        reader.close();
        network.close();
        preferences.close();
    }

    private FeedUpdateWorker worker(Data data) {
        WorkerParameters params = Mockito.mock(WorkerParameters.class);
        Mockito.when(params.getInputData()).thenReturn(data);
        Mockito.when(params.getId()).thenReturn(UUID.randomUUID());
        return new FeedUpdateWorker(context, params);
    }

    private FeedUpdateWorker worker() {
        return worker(new Data.Builder().build());
    }

    private static Data manual() {
        return new Data.Builder().putBoolean(FeedUpdateManagerImpl.EXTRA_MANUAL, true).build();
    }

    private static Feed feed(String url, int state, boolean keepUpdated, long lastRefreshAttempt) {
        Feed feed = new Feed(url, null, "Feed");
        feed.setId(FEED_ID);
        feed.setState(state);
        feed.setLastRefreshAttempt(lastRefreshAttempt);
        feed.setPreferences(new FeedPreferences(FEED_ID, FeedPreferences.AutoDownloadSetting.GLOBAL, keepUpdated,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, null, null,
                new FeedFilter(), FeedPreferences.SPEED_USE_GLOBAL, 0, 0,
                FeedPreferences.SkipSilence.GLOBAL, false, FeedPreferences.NewEpisodesAction.GLOBAL,
                Collections.emptySet()));
        return feed;
    }

    private void provideFeeds(Feed... feeds) {
        reader.when(DBReader::getFeedList).thenReturn(new ArrayList<>(List.of(feeds)));
    }

    private void networkIsUnavailable() {
        network.when(NetworkUtils::networkAvailable).thenReturn(false);
    }

    private DownloadRequest requestSentToDownloader() {
        ArgumentCaptor<DownloadRequest> captor = ArgumentCaptor.forClass(DownloadRequest.class);
        Mockito.verify(factory.constructed().get(0)).create(captor.capture());
        return captor.getValue();
    }

    private void createRequestsFor(Feed feed, String lastModified) {
        requestCreator.when(() -> DownloadRequestCreator.create(any(Feed.class)))
                .thenAnswer(invocation -> new DownloadRequestBuilder(DESTINATION, feed).lastModified(lastModified));
    }

    @Test
    public void unknownFeedFinishesWithoutRefreshing() {
        reader.when(() -> DBReader.getFeed(anyLong(), anyBoolean(), anyInt(), anyInt())).thenReturn(null);
        Data data = new Data.Builder().putLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, FEED_ID).build();

        assertEquals(ListenableWorker.Result.success(), worker(data).doWork());

        cleaner.verify(() -> NonSubscribedFeedsCleaner.deleteOldNonSubscribedFeeds(any(Context.class)),
                Mockito.never());
    }

    @Test
    public void finishedRefreshCleansUpStartsAutoDownloadAndSynchronises() {
        provideFeeds();

        assertEquals(ListenableWorker.Result.success(), worker().doWork());

        cleaner.verify(() -> NonSubscribedFeedsCleaner.deleteOldNonSubscribedFeeds(context));
        Mockito.verify(autoDownload).autodownloadUndownloadedItems(context);
        Mockito.verify(syncQueue).syncImmediately();
    }

    @Test
    public void episodeCountersAreLoadedBeforeTheFeedListIsRead() {
        FeedUpdateWorker worker = worker();
        NewEpisodesNotification counters = notification.constructed().get(0);
        AtomicBoolean loadedWhenFeedsWereRead = new AtomicBoolean();
        reader.when(DBReader::getFeedList).thenAnswer(invocation -> {
            loadedWhenFeedsWereRead.set(Mockito.mockingDetails(counters).getInvocations().stream()
                    .anyMatch(call -> call.getMethod().getName().equals("loadCountersBeforeRefresh")));
            return new ArrayList<Feed>();
        });

        worker.doWork();

        assertTrue(loadedWhenFeedsWereRead.get());
    }

    @Test
    public void remoteFeedIsRetriedLaterWhenThereIsNoNetwork() {
        provideFeeds(feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0));
        networkIsUnavailable();

        assertEquals(ListenableWorker.Result.retry(), worker().doWork());

        Mockito.verify(autoDownload, Mockito.never()).autodownloadUndownloadedItems(any(Context.class));
    }

    @Test
    public void remoteFeedIsRetriedLaterWhenTheNetworkDoesNotAllowRefreshing() {
        provideFeeds(feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0));
        network.when(NetworkUtils::networkAvailable).thenReturn(true);
        network.when(NetworkUtils::isFeedRefreshAllowed).thenReturn(false);

        assertEquals(ListenableWorker.Result.retry(), worker().doWork());
    }

    @Test
    public void feedsThatDoNotWantUpdatesAreNotRefreshed() {
        provideFeeds(feed(SOURCE, Feed.STATE_SUBSCRIBED, false, 0));
        networkIsUnavailable();

        assertEquals(ListenableWorker.Result.success(), worker().doWork());
    }

    @Test
    public void feedsThatAreNotSubscribedAreNotRefreshed() {
        provideFeeds(feed(SOURCE, Feed.STATE_NOT_SUBSCRIBED, true, 0), feed(SOURCE, Feed.STATE_ARCHIVED, true, 0));
        networkIsUnavailable();

        assertEquals(ListenableWorker.Result.success(), worker().doWork());
    }

    @Test
    public void recentlyRefreshedFeedIsSkippedByAutomaticRefresh() {
        provideFeeds(feed(SOURCE, Feed.STATE_SUBSCRIBED, true, System.currentTimeMillis()));
        networkIsUnavailable();

        assertEquals(ListenableWorker.Result.success(), worker().doWork());
    }

    @Test
    public void recentlyRefreshedFeedIsStillRefreshedManually() {
        provideFeeds(feed(SOURCE, Feed.STATE_SUBSCRIBED, true, System.currentTimeMillis()));
        networkIsUnavailable();

        assertEquals(ListenableWorker.Result.retry(), worker(manual()).doWork());
    }

    @Test
    public void recentlyRefreshedFeedIsRefreshedWhenAutomaticUpdatesAreDisabled() {
        preferences.when(UserPreferences::isAutoUpdateDisabled).thenReturn(true);
        provideFeeds(feed(SOURCE, Feed.STATE_SUBSCRIBED, true, System.currentTimeMillis()));
        networkIsUnavailable();

        assertEquals(ListenableWorker.Result.retry(), worker().doWork());
    }

    @Test
    public void singleRemoteFeedIsRetriedLaterWhenThereIsNoNetwork() {
        reader.when(() -> DBReader.getFeed(eq(FEED_ID), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0));
        networkIsUnavailable();
        Data data = new Data.Builder().putLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, FEED_ID).build();

        assertEquals(ListenableWorker.Result.retry(), worker(data).doWork());
    }

    @Test
    public void foregroundInfoShowsTheRefreshNotification() throws Exception {
        WorkManagerMocks workManager = new WorkManagerMocks();
        try {
            ForegroundInfo info = worker().getForegroundInfoAsync().get();

            assertEquals(R.id.notification_updating_feeds, info.getNotificationId());
            assertEquals(context.getString(R.string.download_notification_title_feeds),
                    info.getNotification().extras.getString(Notification.EXTRA_TITLE));
        } finally {
            workManager.close();
        }
    }

    @Test
    public void refreshFailsWhenNoDownloaderCanHandleTheSource() {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, null);
        factory.close();
        factory = Mockito.mockConstruction(DefaultDownloaderFactory.class, (mock, ctx) ->
                Mockito.when(mock.create(any(DownloadRequest.class))).thenReturn(null));

        Exception thrown = assertThrows(Exception.class, () -> worker().refreshFeed(feed, false));

        assertEquals("Unable to create downloader", thrown.getMessage());
    }

    @Test
    public void cancelledDownloadRefreshesNothingAndLogsNothing() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, null);
        Mockito.when(downloader.getResult()).thenReturn(
                new DownloadResult("Feed", FEED_ID, Feed.FEEDFILETYPE_FEED, false,
                        DownloadError.ERROR_DOWNLOAD_CANCELLED, null));

        assertNull(worker().refreshFeed(feed, false));

        writer.verify(() -> DBWriter.setFeedLastUpdateFailed(anyLong(), anyBoolean()), Mockito.never());
        writer.verify(() -> DBWriter.addDownloadStatus(any(DownloadResult.class)), Mockito.never());
    }

    @Test
    public void failedDownloadMarksTheFeedAsFailedAndLogsTheError() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, null);
        DownloadResult failure = new DownloadResult("Feed", FEED_ID, Feed.FEEDFILETYPE_FEED, false,
                DownloadError.ERROR_NOT_FOUND, "404");
        Mockito.when(downloader.getResult()).thenReturn(failure);

        assertNull(worker().refreshFeed(feed, false));

        writer.verify(() -> DBWriter.setFeedLastUpdateFailed(FEED_ID, true));
        writer.verify(() -> DBWriter.addDownloadStatus(failure));
    }

    @Test
    public void unparsableFeedMarksTheFeedAsFailedAndLogsTheParserError() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, null);
        parseSucceeds = false;
        parserStatus = new DownloadResult("Feed", FEED_ID, Feed.FEEDFILETYPE_FEED, false,
                DownloadError.ERROR_PARSER_EXCEPTION, "bad xml");

        assertNull(worker().refreshFeed(feed, false));

        writer.verify(() -> DBWriter.setFeedLastUpdateFailed(FEED_ID, true));
        writer.verify(() -> DBWriter.addDownloadStatus(parserStatus));
        databaseWriter.verify(() -> FeedDatabaseWriter.updateFeed(any(Context.class), any(Feed.class), anyBoolean()),
                Mockito.never());
    }

    @Test
    public void parsedFeedIsSavedWithARefreshTimestamp() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, null);

        Feed result = worker().refreshFeed(feed, false);

        assertSame(savedFeed, result);
        databaseWriter.verify(() -> FeedDatabaseWriter.updateFeed(context, parsed.feed, false));
        assertTrue(parsed.feed.getLastRefreshAttempt() > 0);
    }

    @Test
    public void newSubscriptionIsSavedWithoutConsultingTheDownloadLog() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        feed.setId(0);
        createRequestsFor(feed, null);

        assertSame(savedFeed, worker().refreshFeed(feed, false));

        reader.verify(() -> DBReader.getFeedDownloadLog(anyLong(), anyLong()), Mockito.never());
    }

    @Test
    public void successAfterAFailedRefreshIsLoggedAsSuccessful() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, null);
        DownloadResult previousFailure = new DownloadResult("Feed", FEED_ID, Feed.FEEDFILETYPE_FEED, false,
                DownloadError.ERROR_IO_ERROR, "reset");
        reader.when(() -> DBReader.getFeedDownloadLog(FEED_ID, 1)).thenReturn(List.of(previousFailure));

        worker().refreshFeed(feed, false);

        writer.verify(() -> DBWriter.addDownloadStatus(parserStatus));
    }

    @Test
    public void successAfterASuccessfulRefreshIsNotLoggedAgain() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, null);
        reader.when(() -> DBReader.getFeedDownloadLog(FEED_ID, 1)).thenReturn(List.of(parserStatus));

        worker().refreshFeed(feed, false);

        writer.verify(() -> DBWriter.addDownloadStatus(any(DownloadResult.class)), Mockito.never());
    }

    @Test
    public void permanentRedirectOfTheDownloadUpdatesTheFeedUrl() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, null);
        downloader.permanentRedirectUrl = "http://example.com/moved.xml";

        worker().refreshFeed(feed, false);

        writer.verify(() -> DBWriter.updateFeedDownloadURL(SOURCE, "http://example.com/moved.xml"));
    }

    @Test
    public void redirectDetectedByTheParserUpdatesTheFeedUrl() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, null);
        parserTask.close();
        FeedHandlerResult redirected = new FeedHandlerResult(parsed.feed, Collections.emptyMap(),
                "http://example.com/redirected.xml");
        parserTask = Mockito.mockConstruction(FeedParserTask.class, (mock, ctx) -> {
            Mockito.when(mock.call()).thenReturn(redirected);
            Mockito.when(mock.isSuccessful()).thenReturn(true);
            Mockito.when(mock.getDownloadStatus()).thenReturn(parserStatus);
        });

        worker().refreshFeed(feed, false);

        writer.verify(() -> DBWriter.updateFeedDownloadURL(SOURCE, "http://example.com/redirected.xml"));
    }

    @Test
    public void unchangedFeedUrlIsNotUpdated() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, null);

        worker().refreshFeed(feed, false);

        writer.verify(() -> DBWriter.updateFeedDownloadURL(any(String.class), any(String.class)), Mockito.never());
    }

    @Test
    public void forcedRefreshIgnoresTheLastModifiedValue() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, "etag-1");

        worker().refreshFeed(feed, true);

        assertNull(requestSentToDownloader().getLastModified());
    }

    @Test
    public void refreshAfterAFailedUpdateIgnoresTheLastModifiedValue() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        feed.setLastUpdateFailed(true);
        createRequestsFor(feed, "etag-1");

        worker().refreshFeed(feed, false);

        assertNull(requestSentToDownloader().getLastModified());
    }

    @Test
    public void ordinaryRefreshKeepsTheLastModifiedValue() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        createRequestsFor(feed, "etag-1");

        worker().refreshFeed(feed, false);

        assertEquals("etag-1", requestSentToDownloader().getLastModified());
    }

    @Test
    public void nextPageRefreshRequestsTheNextPageLink() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        feed.setNextPageLink("http://example.com/feed.xml?page=2");
        feed.setPageNr(1);
        createRequestsFor(feed, null);
        Data data = new Data.Builder().putBoolean(FeedUpdateManagerImpl.EXTRA_NEXT_PAGE, true).build();

        worker(data).refreshFeed(feed, false);

        assertEquals(2, feed.getPageNr());
        assertEquals("http://example.com/feed.xml?page=2", requestSentToDownloader().getSource());
    }

    @Test
    public void nextPageRefreshWithoutNextPageLinkRequestsTheFeedItself() throws Exception {
        Feed feed = feed(SOURCE, Feed.STATE_SUBSCRIBED, true, 0);
        feed.setPageNr(1);
        createRequestsFor(feed, null);
        Data data = new Data.Builder().putBoolean(FeedUpdateManagerImpl.EXTRA_NEXT_PAGE, true).build();

        worker(data).refreshFeed(feed, false);

        assertEquals(1, feed.getPageNr());
        assertEquals(SOURCE, requestSentToDownloader().getSource());
    }
}
