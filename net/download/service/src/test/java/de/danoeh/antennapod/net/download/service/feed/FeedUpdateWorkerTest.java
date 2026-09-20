package de.danoeh.antennapod.net.download.service.feed;

import android.Manifest;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.net.download.service.DownloadIntegrationTestBase;
import de.danoeh.antennapod.net.download.service.R;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

@Category(IntegrationTest.class)
public class FeedUpdateWorkerTest extends DownloadIntegrationTestBase {
    private final Map<String, Integer> requestsPerPath = new ConcurrentHashMap<>();

    private Data.Builder manualInput() {
        return new Data.Builder()
                .putBoolean(FeedUpdateManagerImpl.EXTRA_MANUAL, true)
                .putBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, true);
    }

    private ListenableWorker.Result runWorker(Data input) {
        ListenableWorker.Result result = new FeedUpdateWorker(context, workerParameters(input, 0)).doWork();
        DBWriter.tearDownTests();
        return result;
    }

    private ListenableWorker.Result refreshSingle(Feed feed) {
        return runWorker(manualInput().putLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, feed.getId()).build());
    }

    private void serve(Map<String, MockResponse> routes) {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                requestsPerPath.merge(request.getPath(), 1, Integer::sum);
                MockResponse response = routes.get(request.getPath());
                return response != null ? response : new MockResponse().setResponseCode(404);
            }
        });
    }

    private static MockResponse rssResponse(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/rss+xml");
    }

    private String url(String path) {
        return server.url(path).toString();
    }

    private Feed reload(Feed feed) {
        return DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
    }

    @Test
    public void refreshingSingleFeedStoresNewEpisodes() {
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title", "First", "Second"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));

        ListenableWorker.Result result = refreshSingle(feed);

        assertEquals(ListenableWorker.Result.success(), result);
        Feed stored = reload(feed);
        assertEquals("Remote Title", stored.getTitle());
        List<String> titles = stored.getItems().stream().map(FeedItem::getTitle).sorted().collect(Collectors.toList());
        assertEquals(List.of("First", "Second"), titles);
        assertFalse(stored.hasLastUpdateFailed());
        assertTrue(stored.getLastRefreshAttempt() > 0);
    }

    @Test
    public void refreshTriggersAutoDownloadAndSynchronisation() {
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title", "First"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));

        refreshSingle(feed);

        verify(AutoDownloadManager.getInstance()).autodownloadUndownloadedItems(any());
        verify(synchronizationQueue).syncImmediately();
    }

    @Test
    public void refreshingUnknownFeedSucceedsWithoutRequests() {
        serve(Map.of());

        ListenableWorker.Result result = runWorker(
                manualInput().putLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, 4711).build());

        assertEquals(ListenableWorker.Result.success(), result);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void notFoundMarksFeedAsFailedAndLogsError() {
        serve(Map.of());
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));

        ListenableWorker.Result result = refreshSingle(feed);

        assertEquals(ListenableWorker.Result.success(), result);
        assertTrue(reload(feed).hasLastUpdateFailed());
        List<DownloadResult> log = DBReader.getFeedDownloadLog(feed.getId(), 10);
        assertEquals(1, log.size());
        assertFalse(log.get(0).isSuccessful());
        assertEquals(DownloadError.ERROR_NOT_FOUND, log.get(0).getReason());
    }

    @Test
    public void truncatedXmlIsLoggedAsParserError() {
        serve(Map.of("/feed.xml", rssResponse("<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>Broken")));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));

        refreshSingle(feed);

        assertTrue(reload(feed).hasLastUpdateFailed());
        DownloadResult entry = DBReader.getFeedDownloadLog(feed.getId(), 10).get(0);
        assertFalse(entry.isSuccessful());
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION, entry.getReason());
    }

    @Test
    public void htmlPageIsLoggedAsUnsupportedHtml() {
        serve(Map.of("/feed.xml", new MockResponse().setBody("<html><body>Login</body></html>")
                .addHeader("Content-Type", "text/html")));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));

        refreshSingle(feed);

        DownloadResult entry = DBReader.getFeedDownloadLog(feed.getId(), 10).get(0);
        assertEquals(DownloadError.ERROR_UNSUPPORTED_TYPE_HTML, entry.getReason());
    }

    @Test
    public void unsupportedRssVersionIsLoggedAsUnsupportedType() {
        String unsupported = rss("Remote Title", "First").replace("version=\"2.0\"", "version=\"9.9\"");
        serve(Map.of("/feed.xml", rssResponse(unsupported)));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));

        refreshSingle(feed);

        DownloadResult entry = DBReader.getFeedDownloadLog(feed.getId(), 10).get(0);
        assertEquals(DownloadError.ERROR_UNSUPPORTED_TYPE, entry.getReason());
        assertEquals("Unsupported rss version", entry.getReasonDetailed());
    }

    @Test
    public void feedWithoutTitleIsRejectedAndKeepsStoredData() {
        serve(Map.of("/feed.xml", rssResponse(rss(null, "First"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));

        refreshSingle(feed);

        Feed stored = reload(feed);
        assertTrue(stored.hasLastUpdateFailed());
        assertEquals("Local Title", stored.getTitle());
        assertTrue(stored.getItems().isEmpty());
        DownloadResult entry = DBReader.getFeedDownloadLog(feed.getId(), 10).get(0);
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION, entry.getReason());
        assertEquals("Feed has no title", entry.getReasonDetailed());
    }

    @Test
    public void episodeWithoutTitleIsRejected() {
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title", "First").replace("<title>First</title>", ""))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));

        refreshSingle(feed);

        assertTrue(reload(feed).hasLastUpdateFailed());
        DownloadResult entry = DBReader.getFeedDownloadLog(feed.getId(), 10).get(0);
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION, entry.getReason());
    }

    @Test
    public void successAfterFailureClearsFailedFlagAndLogsSuccess() {
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title", "First"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));
        DBWriter.setFeedLastUpdateFailed(feed.getId(), true);
        DBWriter.addDownloadStatus(new DownloadResult("Local Title", feed.getId(), Feed.FEEDFILETYPE_FEED, false,
                DownloadError.ERROR_HTTP_DATA_ERROR, "500"));
        DBWriter.tearDownTests();

        refreshSingle(feed);

        assertFalse(reload(feed).hasLastUpdateFailed());
        DownloadResult latest = DBReader.getFeedDownloadLog(feed.getId(), 1).get(0);
        assertTrue(latest.isSuccessful());
    }

    @Test
    public void successfulRefreshOfHealthyFeedAddsNoDownloadLogEntry() {
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title", "First"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));

        refreshSingle(feed);

        assertTrue(DBReader.getFeedDownloadLog(feed.getId(), 10).isEmpty());
    }

    @Test
    public void permanentRedirectUpdatesStoredFeedUrl() {
        serve(Map.of(
                "/old.xml", new MockResponse().setResponseCode(301).addHeader("Location", url("/new.xml")),
                "/new.xml", rssResponse(rss("Remote Title", "First"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/old.xml")));

        refreshSingle(feed);

        assertEquals(url("/new.xml"), reload(feed).getDownloadUrl());
    }

    @Test
    public void temporaryRedirectKeepsStoredFeedUrl() {
        serve(Map.of(
                "/old.xml", new MockResponse().setResponseCode(302).addHeader("Location", url("/temp.xml")),
                "/temp.xml", rssResponse(rss("Remote Title", "First"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/old.xml")));

        refreshSingle(feed);

        Feed stored = reload(feed);
        assertEquals(url("/old.xml"), stored.getDownloadUrl());
        assertEquals(1, stored.getItems().size());
    }

    @Test
    public void nextPageRequestFetchesLinkedPageAndIncrementsPageNumber() {
        serve(Map.of("/page2.xml", rssResponse(rss("Remote Title", "Second Page Episode"))));
        Feed unsaved = newFeed("Local Title", url("/feed.xml"));
        unsaved.setPaged(true);
        unsaved.setNextPageLink(url("/page2.xml"));
        Feed feed = saveFeed(unsaved);

        runWorker(manualInput().putLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, feed.getId())
                .putBoolean(FeedUpdateManagerImpl.EXTRA_NEXT_PAGE, true).build());

        assertEquals(1, requestsPerPath.get("/page2.xml").intValue());
        assertNull(requestsPerPath.get("/feed.xml"));
        assertEquals("Second Page Episode", reload(feed).getItems().get(0).getTitle());
    }

    @Test
    public void nextPageFlagWithoutLinkRefreshesFeedUrl() {
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title", "First"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));

        runWorker(manualInput().putLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, feed.getId())
                .putBoolean(FeedUpdateManagerImpl.EXTRA_NEXT_PAGE, true).build());

        assertEquals(1, requestsPerPath.get("/feed.xml").intValue());
        assertEquals("First", reload(feed).getItems().get(0).getTitle());
    }

    @Test
    public void refreshingAllFeedsSkipsFeedsWithUpdatesDisabledAndUnsubscribedFeeds() {
        serve(Map.of(
                "/active.xml", rssResponse(rss("Active Remote", "One")),
                "/paused.xml", rssResponse(rss("Paused Remote", "One")),
                "/unsubscribed.xml", rssResponse(rss("Unsubscribed Remote", "One"))));
        Feed active = saveFeed(newFeed("Active", url("/active.xml")));
        Feed paused = saveFeed(newFeed("Paused", url("/paused.xml")));
        FeedPreferences pausedPreferences = paused.getPreferences();
        pausedPreferences.setKeepUpdated(false);
        DBWriter.setFeedPreferences(pausedPreferences);
        Feed unsubscribedUnsaved = newFeed("Unsubscribed", url("/unsubscribed.xml"));
        unsubscribedUnsaved.setState(Feed.STATE_NOT_SUBSCRIBED);
        saveFeed(unsubscribedUnsaved);
        DBWriter.tearDownTests();

        runWorker(manualInput().build());

        assertEquals(1, requestsPerPath.get("/active.xml").intValue());
        assertNull(requestsPerPath.get("/paused.xml"));
        assertNull(requestsPerPath.get("/unsubscribed.xml"));
        assertEquals("Active Remote", reload(active).getTitle());
    }

    @Test
    public void automaticRefreshSkipsFeedsRefreshedRecently() {
        serve(Map.of(
                "/stale.xml", rssResponse(rss("Stale Remote", "One")),
                "/fresh.xml", rssResponse(rss("Fresh Remote", "One"))));
        Feed stale = newFeed("Stale", url("/stale.xml"));
        Feed fresh = newFeed("Fresh", url("/fresh.xml"));
        fresh.setLastRefreshAttempt(System.currentTimeMillis());
        saveFeed(stale);
        saveFeed(fresh);

        runWorker(new Data.Builder().putBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, true).build());

        assertEquals(1, requestsPerPath.get("/stale.xml").intValue());
        assertNull(requestsPerPath.get("/fresh.xml"));
    }

    @Test
    public void manualRefreshIgnoresRecentRefreshTime() {
        serve(Map.of("/fresh.xml", rssResponse(rss("Fresh Remote", "One"))));
        Feed fresh = newFeed("Fresh", url("/fresh.xml"));
        fresh.setLastRefreshAttempt(System.currentTimeMillis());
        saveFeed(fresh);

        runWorker(manualInput().build());

        assertEquals(1, requestsPerPath.get("/fresh.xml").intValue());
    }

    @Test
    public void automaticRefreshWithDisabledIntervalStillRefreshesRecentlyRefreshedFeeds() {
        UserPreferences.setUpdateInterval(0);
        serve(Map.of("/fresh.xml", rssResponse(rss("Fresh Remote", "One"))));
        Feed fresh = newFeed("Fresh", url("/fresh.xml"));
        fresh.setLastRefreshAttempt(System.currentTimeMillis());
        saveFeed(fresh);

        runWorker(new Data.Builder().putBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, true).build());

        assertEquals(1, requestsPerPath.get("/fresh.xml").intValue());
    }

    @Test
    public void unmodifiedFeedIsNotMarkedAsFailed() throws Exception {
        serve(Map.of("/feed.xml", new MockResponse().setResponseCode(304)));
        Feed unsaved = newFeed("Local Title", url("/feed.xml"));
        unsaved.setLastModified("\"etag-1\"");
        Feed feed = saveFeed(unsaved);

        runWorker(new Data.Builder().putBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, true).build());

        RecordedRequest recorded = server.takeRequest();
        assertEquals("\"etag-1\"", recorded.getHeader("If-None-Match"));
        Feed stored = reload(feed);
        assertFalse(stored.hasLastUpdateFailed());
        assertEquals("Local Title", stored.getTitle());
        assertTrue(DBReader.getFeedDownloadLog(feed.getId(), 10).isEmpty());
    }

    @Test
    public void forcedRefreshDoesNotSendConditionalHeaders() throws Exception {
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title", "First"))));
        Feed unsaved = newFeed("Local Title", url("/feed.xml"));
        unsaved.setLastModified("\"etag-1\"");
        Feed feed = saveFeed(unsaved);

        refreshSingle(feed);

        RecordedRequest recorded = server.takeRequest();
        assertNull(recorded.getHeader("If-None-Match"));
        assertNull(recorded.getHeader("If-Modified-Since"));
    }

    @Test
    public void refreshIsDeferredWhileOffline() {
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title", "First"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(connectivityManager).setActiveNetworkInfo(null);

        ListenableWorker.Result result = runWorker(new Data.Builder()
                .putLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, feed.getId()).build());

        assertEquals(ListenableWorker.Result.retry(), result);
        assertEquals(0, server.getRequestCount());
        verify(AutoDownloadManager.getInstance(), never()).autodownloadUndownloadedItems(any());
    }

    @Test
    public void newEpisodesPostNotificationAndGroupSummary() {
        shadowOf((Application) context.getApplicationContext())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title", "First", "Second"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));
        FeedPreferences preferences = feed.getPreferences();
        preferences.setShowEpisodeNotification(true);
        DBWriter.setFeedPreferences(preferences);
        DBWriter.tearDownTests();

        refreshSingle(feed);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertEquals(2, shadowOf(manager).getAllNotifications().size());
        List<String> texts = shadowOf(manager).getAllNotifications().stream()
                .map(notification -> String.valueOf(shadowOf(notification).getContentText()))
                .collect(Collectors.toList());
        assertTrue(texts.toString(), texts.stream().anyMatch(text -> text.contains("Remote Title")));
    }

    @Test
    public void episodeNotificationsAreSkippedWhenDisabledForFeed() {
        shadowOf((Application) context.getApplicationContext())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title", "First"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));
        FeedPreferences preferences = feed.getPreferences();
        preferences.setShowEpisodeNotification(false);
        DBWriter.setFeedPreferences(preferences);
        DBWriter.tearDownTests();

        refreshSingle(feed);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertTrue(shadowOf(manager).getAllNotifications().isEmpty());
    }

    @Test
    public void refreshWithoutNewEpisodesPostsNoNotification() {
        shadowOf((Application) context.getApplicationContext())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        serve(Map.of("/feed.xml", rssResponse(rss("Remote Title"))));
        Feed feed = saveFeed(newFeed("Local Title", url("/feed.xml")));
        FeedPreferences preferences = feed.getPreferences();
        preferences.setShowEpisodeNotification(true);
        DBWriter.setFeedPreferences(preferences);
        DBWriter.tearDownTests();

        refreshSingle(feed);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertTrue(shadowOf(manager).getAllNotifications().isEmpty());
    }

    @Test
    public void foregroundInfoDescribesRefreshInProgress() throws Exception {
        ForegroundInfo info = new FeedUpdateWorker(context,
                workerParameters(new Data.Builder().build(), 0)).getForegroundInfoAsync().get();

        assertNotNull(info.getNotification());
        assertEquals(R.id.notification_updating_feeds,
                info.getNotificationId());
    }

    @Test
    public void refreshCollectsResultsForEveryFeedOfAFullRun() {
        serve(Map.of(
                "/a.xml", rssResponse(rss("A Remote", "One")),
                "/b.xml", new MockResponse().setResponseCode(500),
                "/c.xml", rssResponse(rss("C Remote", "One"))));
        Feed a = saveFeed(newFeed("A", url("/a.xml")));
        Feed b = saveFeed(newFeed("B", url("/b.xml")));
        Feed c = saveFeed(newFeed("C", url("/c.xml")));

        ListenableWorker.Result result = runWorker(manualInput().build());

        assertEquals(ListenableWorker.Result.success(), result);
        assertEquals("A Remote", reload(a).getTitle());
        assertEquals("C Remote", reload(c).getTitle());
        assertTrue(reload(b).hasLastUpdateFailed());
        assertEquals(Collections.emptyList(), DBReader.getFeedDownloadLog(a.getId(), 10));
    }
}
