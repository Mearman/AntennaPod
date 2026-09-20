package de.test.antennapod.service.download;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateManagerImpl;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateWorker;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.test.antennapod.util.service.download.HTTPBin;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Runs the feed refresh worker against feeds that are hosted on the local test server.
 */
@RunWith(AndroidJUnit4.class)
public class FeedUpdateWorkerTest {
    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Context context;
    private NotificationManager notificationManager;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @After
    public void tearDown() throws Exception {
        notificationManager.cancelAll();
        fixture.tearDown();
    }

    private ListenableWorker.Result refresh(Feed feed) throws Exception {
        Data.Builder input = new Data.Builder()
                .putBoolean(FeedUpdateManagerImpl.EXTRA_MANUAL, true)
                .putBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, true);
        if (feed != null) {
            input.putLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, feed.getId());
        }
        return run(input.build());
    }

    private ListenableWorker.Result run(Data input) throws Exception {
        return TestListenableWorkerBuilder.from(context, FeedUpdateWorker.class).setInputData(input).build()
                .startWork().get(60, TimeUnit.SECONDS);
    }

    private Feed reload(Feed feed) {
        return DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
    }

    private List<String> titlesOf(Feed feed) {
        List<String> titles = new ArrayList<>();
        for (FeedItem item : reload(feed).getItems()) {
            titles.add(item.getTitle());
        }
        return titles;
    }

    private Feed subscribeWithoutNewestEpisode(String title) throws Exception {
        Feed feed = fixture.newFeed(title, 3);
        feed.setDownloadUrl(fixture.hostFeed(feed));
        feed.getItems().remove(0);
        return fixture.subscribe(feed);
    }

    private DownloadResult awaitLogEntry(Feed feed, DownloadError reason) {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> findLogEntry(feed, reason) != null);
        return findLogEntry(feed, reason);
    }

    private DownloadResult findLogEntry(Feed feed, DownloadError reason) {
        for (DownloadResult result : DBReader.getFeedDownloadLog(feed.getId(), Integer.MAX_VALUE)) {
            if (result.getReason() == reason) {
                return result;
            }
        }
        return null;
    }

    private void awaitFeedUpdateFailed(Feed feed, boolean failed) {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> reload(feed).hasLastUpdateFailed() == failed);
    }

    @Test
    public void refreshAddsEpisodesThatAreNewInTheFeed() throws Exception {
        Feed feed = subscribeWithoutNewestEpisode("Refreshed");
        assertEquals(2, feed.getItems().size());

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        List<String> titles = titlesOf(feed);
        assertEquals(3, titles.size());
        assertTrue(titles.contains("Refreshed episode 0"));
        FeedItem added = null;
        for (FeedItem item : reload(feed).getItems()) {
            if (item.getTitle().equals("Refreshed episode 0")) {
                added = item;
            }
        }
        assertNotNull(added);
        assertTrue(added.isNew());
        assertTrue(added.hasMedia());
        assertTrue(reload(feed).getLastRefreshAttempt() > 0);
        assertFalse(reload(feed).hasLastUpdateFailed());
    }

    @Test
    public void refreshOfAllFeedsSkipsFeedsThatAreNotKeptUpdated() throws Exception {
        Feed updated = subscribeWithoutNewestEpisode("Kept updated");
        Feed ignored = subscribeWithoutNewestEpisode("Not kept updated");
        ignored.getPreferences().setKeepUpdated(false);
        DBWriter.setFeedPreferences(ignored.getPreferences()).get();

        assertEquals(ListenableWorker.Result.success(), refresh(null));

        assertEquals(3, titlesOf(updated).size());
        assertEquals(2, titlesOf(ignored).size());
        assertTrue(fixture.requestsFor(ignored.getDownloadUrl()).isEmpty());
    }

    @Test
    public void automaticRefreshSkipsFeedsThatWereRefreshedRecently() throws Exception {
        UserPreferences.setUpdateInterval(TimeUnit.HOURS.toMinutes(12));
        Feed recent = subscribeWithoutNewestEpisode("Recent");
        Feed stale = subscribeWithoutNewestEpisode("Stale");
        assertEquals(ListenableWorker.Result.success(), refresh(recent));

        assertEquals(ListenableWorker.Result.success(), run(new Data.Builder()
                .putBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, true).build()));

        assertEquals(3, titlesOf(stale).size());
        assertEquals(1, fixture.requestsFor(recent.getDownloadUrl()).size());
        assertEquals(1, fixture.requestsFor(stale.getDownloadUrl()).size());
    }

    @Test
    public void automaticRefreshChecksTheNetworkBeforeUpdating() throws Exception {
        Feed feed = subscribeWithoutNewestEpisode("Network checked");
        UserPreferences.setAllowMobileFeedRefresh(true);

        assertEquals(ListenableWorker.Result.success(), run(new Data.Builder()
                .putLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, feed.getId()).build()));

        assertEquals(3, titlesOf(feed).size());
    }

    @Test
    public void refreshOfAMissingFeedSucceedsWithoutRequests() throws Exception {
        assertEquals(ListenableWorker.Result.success(), run(new Data.Builder()
                .putLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, 12345).build()));

        assertTrue(fixture.server().getRequests().isEmpty());
    }

    @Test
    public void feedThatIsNotFoundIsMarkedAsFailedAndLogged() throws Exception {
        Feed feed = fixture.newFeed("Missing", 1);
        feed.setDownloadUrl(fixture.url("/status/404"));
        feed = fixture.subscribe(feed);

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        awaitFeedUpdateFailed(feed, true);
        DownloadResult logged = awaitLogEntry(feed, DownloadError.ERROR_NOT_FOUND);
        assertFalse(logged.isSuccessful());
        assertEquals(1, titlesOf(feed).size());
    }

    @Test
    public void feedWithWrongCredentialsIsLoggedAsUnauthorized() throws Exception {
        Feed feed = fixture.newFeed("Secret", 1);
        feed.setDownloadUrl(fixture.url("/basic-auth-file/user/secret/"
                + fixture.idOf(fixture.hostFeed(feed))));
        feed = fixture.subscribe(feed);
        feed.getPreferences().setUsername("user");
        feed.getPreferences().setPassword("wrong");
        DBWriter.setFeedPreferences(feed.getPreferences()).get();

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        awaitLogEntry(feed, DownloadError.ERROR_UNAUTHORIZED);
        awaitFeedUpdateFailed(feed, true);
    }

    @Test
    public void feedWithCredentialsIsRefreshed() throws Exception {
        Feed hosted = fixture.newFeed("Secret", 3);
        hosted.setDownloadUrl(fixture.url("/basic-auth-file/user/secret/" + fixture.idOf(fixture.hostFeed(hosted))));
        hosted.getItems().remove(0);
        Feed feed = fixture.subscribe(hosted);
        feed.getPreferences().setUsername("user");
        feed.getPreferences().setPassword("secret");
        DBWriter.setFeedPreferences(feed.getPreferences()).get();

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        assertEquals(3, titlesOf(feed).size());
        assertFalse(reload(feed).hasLastUpdateFailed());
    }

    @Test
    public void malformedFeedIsLoggedAsParserError() throws Exception {
        Feed feed = fixture.newFeed("Broken", 1);
        feed.setDownloadUrl(fixture.hostText("broken.xml",
                "<rss version=\"2.0\"><channel><title>Broken</title><item><title>Cut off</channel></rss>"));
        feed = fixture.subscribe(feed);

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        awaitLogEntry(feed, DownloadError.ERROR_PARSER_EXCEPTION);
        awaitFeedUpdateFailed(feed, true);
    }

    @Test
    public void webPageInsteadOfFeedIsLoggedAsHtml() throws Exception {
        Feed feed = fixture.newFeed("Web page", 1);
        feed.setDownloadUrl(fixture.hostText("page.html", "<html><head><title>Not a feed</title></head></html>"));
        feed = fixture.subscribe(feed);

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        awaitLogEntry(feed, DownloadError.ERROR_UNSUPPORTED_TYPE_HTML);
    }

    @Test
    public void unsupportedRssVersionIsLoggedAsUnsupported() throws Exception {
        Feed feed = fixture.newFeed("Unknown format", 1);
        feed.setDownloadUrl(fixture.hostText("unknown.xml",
                "<rss version=\"3.0\"><channel><title>Unknown format</title></channel></rss>"));
        feed = fixture.subscribe(feed);

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        awaitLogEntry(feed, DownloadError.ERROR_UNSUPPORTED_TYPE);
    }

    @Test
    public void feedWithoutTitleIsRejected() throws Exception {
        Feed feed = fixture.newFeed("Untitled", 1);
        feed.setDownloadUrl(fixture.hostText("untitled.xml",
                "<rss version=\"2.0\"><channel><description>No title</description></channel></rss>"));
        feed = fixture.subscribe(feed);

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        assertEquals("Feed has no title", awaitLogEntry(feed, DownloadError.ERROR_PARSER_EXCEPTION)
                .getReasonDetailed());
        assertEquals(1, titlesOf(feed).size());
    }

    @Test
    public void unchangedFeedIsRequestedConditionallyAndNotParsedAgain() throws Exception {
        Feed feed = subscribeWithoutNewestEpisode("Conditional");
        assertEquals(ListenableWorker.Result.success(), refresh(null));
        String lastModified = reload(feed).getLastModified();
        assertNotNull(lastModified);
        int itemsBefore = titlesOf(feed).size();

        assertEquals(ListenableWorker.Result.success(), refresh(null));

        List<HTTPBin.RecordedRequest> requests = fixture.requestsFor(feed.getDownloadUrl());
        assertEquals(2, requests.size());
        assertNull(requests.get(0).headers.get("if-none-match"));
        assertEquals(lastModified, requests.get(1).headers.get("if-none-match"));
        assertEquals(itemsBefore, titlesOf(feed).size());
        assertFalse(reload(feed).hasLastUpdateFailed());
    }

    @Test
    public void permanentRedirectReplacesTheFeedUrl() throws Exception {
        Feed hosted = fixture.newFeed("Moved", 2);
        String targetUrl = fixture.hostFeed(hosted);
        hosted.setDownloadUrl(fixture.url("/moved/301/" + fixture.idOf(targetUrl)));
        Feed feed = fixture.subscribe(hosted);

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> targetUrl.equals(reload(feed).getDownloadUrl()));
    }

    @Test
    public void successfulRefreshAfterAFailureIsLogged() throws Exception {
        Feed hosted = fixture.newFeed("Recovering", 2);
        String workingUrl = fixture.hostFeed(hosted);
        hosted.setDownloadUrl(fixture.url("/status/500"));
        Feed feed = fixture.subscribe(hosted);
        assertEquals(ListenableWorker.Result.success(), refresh(feed));
        awaitFeedUpdateFailed(feed, true);
        DBWriter.updateFeedDownloadURL(feed.getDownloadUrl(), workingUrl).get();

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        awaitFeedUpdateFailed(feed, false);
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
            List<DownloadResult> log = DBReader.getFeedDownloadLog(feed.getId(), Integer.MAX_VALUE);
            return !log.isEmpty() && log.get(0).isSuccessful();
        });
        assertNotNull(findLogEntry(feed, DownloadError.ERROR_HTTP_DATA_ERROR));
    }

    @Test
    public void newEpisodeNotificationIsShownWhenEnabledForTheFeed() throws Exception {
        Feed feed = subscribeWithoutNewestEpisode("Notified");
        feed.getPreferences().setShowEpisodeNotification(true);
        DBWriter.setFeedPreferences(feed.getPreferences()).get();

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        String expected = context.getResources().getQuantityString(
                R.plurals.new_episode_notification_message, 1, 1, "Notified");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> hasNotificationWithText(expected));
    }

    @Test
    public void newEpisodeNotificationIsNotShownWhenDisabledForTheFeed() throws Exception {
        Feed feed = subscribeWithoutNewestEpisode("Silent");

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        assertEquals(3, titlesOf(feed).size());
        assertFalse(hasNotificationWithText(context.getResources().getQuantityString(
                R.plurals.new_episode_notification_message, 1, 1, "Silent")));
    }

    @Test
    public void automaticDownloadIsStartedForNewEpisodesAfterRefresh() throws Exception {
        UserPreferences.setAllowMobileAutoDownload(true);
        UserPreferences.setAllowMobileEpisodeDownload(true);
        DownloadTestFixture.putBoolean(UserPreferences.PREF_AUTODL_GLOBAL, true);
        Feed feed = subscribeWithoutNewestEpisode("Automatic");

        assertEquals(ListenableWorker.Result.success(), refresh(feed));

        Awaitility.await().atMost(90, TimeUnit.SECONDS).until(() -> {
            for (FeedItem item : reload(feed).getItems()) {
                if (item.getTitle().equals("Automatic episode 0")) {
                    return item.isDownloaded();
                }
            }
            return false;
        });
    }

    private boolean hasNotificationWithText(String text) {
        for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
            CharSequence shown = notification.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
            if (shown != null && text.contentEquals(shown)) {
                return true;
            }
        }
        return false;
    }
}
