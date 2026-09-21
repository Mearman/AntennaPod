package de.test.antennapod.ui;

import android.content.Intent;
import android.util.Base64;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.service.download.StaticContentServer;
import de.test.antennapod.util.service.download.StaticContentServer.RecordedRequest;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickChildViewWithId;
import static de.test.antennapod.ui.FeedRobot.waitUntilDisplayed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FeedAuthenticationTest {
    private static final String FEED_PATH = "/feeds/protected.xml";
    private static final String USER = "alice";
    private static final String PASSWORD = "s3cret";
    private static final String RSS_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel>"
            + "<title>Protected Feed</title><link>https://example.com/protected</link>";
    private static final String RSS_TAIL = "</channel></rss>";

    private StaticContentServer server;

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        UserPreferences.setAllowMobileFeedRefresh(true);
        UserPreferences.setAllowMobileEpisodeDownload(true);
        EspressoTestUtils.setLaunchScreen(AddFeedFragment.TAG);
        server = new StaticContentServer();
        server.start();
        activityRule.launchActivity(new Intent());
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
        for (Feed candidate : DBReader.getFeedList()) {
            Feed feed = DBReader.getFeed(candidate.getId(), false, 0, Integer.MAX_VALUE);
            for (FeedItem item : feed.getItems()) {
                if (item.hasMedia() && item.getMedia().getLocalFileUrl() != null) {
                    FileUtils.deleteQuietly(new File(item.getMedia().getLocalFileUrl()));
                }
            }
        }
        PodDBAdapter.deleteDatabase();
    }

    private static String episode(String guid, String title, String pubDate) {
        return "<item><guid>" + guid + "</guid><title>" + title + "</title><pubDate>" + pubDate + "</pubDate>"
                + "<enclosure url=\"${BASE}/media/" + guid + ".mp3\" length=\"20\" type=\"audio/mpeg\"/></item>";
    }

    private static final String EPISODE_A = episode("a", "Episode A", "Mon, 02 Jan 2023 10:00:00 +0000");
    private static final String EPISODE_B = episode("b", "Episode B", "Tue, 03 Jan 2023 10:00:00 +0000");

    private String publishProtectedFeed(String user, String password, String... items) {
        return server.publishProtected(FEED_PATH, "application/rss+xml",
                RSS_HEAD + String.join("", items) + RSS_TAIL, user, password);
    }

    private static String basicHeader(String user, String password) {
        byte[] credentials = (user + ":" + password).getBytes(StandardCharsets.UTF_8);
        return "Basic " + Base64.encodeToString(credentials, Base64.NO_WRAP);
    }

    private void assertSawAuthorization(String path, String expected) {
        StringBuilder seen = new StringBuilder();
        for (RecordedRequest request : server.requestsFor(path)) {
            String header = request.header("Authorization");
            if (Objects.equals(expected, header)) {
                return;
            }
            seen.append('[').append(header).append(']');
        }
        throw new AssertionError("No request to " + path + " with Authorization " + expected + " but " + seen);
    }

    private void enterCredentials(String user, String password) {
        waitUntilDisplayed(withId(R.id.usernameEditText), FeedRobot.UI_TIMEOUT_MS);
        onView(withId(R.id.usernameEditText)).perform(replaceText(user));
        onView(withId(R.id.passwordEditText)).perform(replaceText(password));
        FeedRobot.confirmTypedDialog(R.string.confirm_label);
    }

    private Feed subscribeWithCredentials(String url) {
        FeedRobot.addFeedByUrl(url);
        enterCredentials(USER, PASSWORD);
        FeedRobot.subscribeToPreviewedFeed();
        return FeedRobot.awaitFeed(url);
    }

    private void downloadEpisode(String title) {
        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.actionOnItem(
                hasDescendant(withText(title)), clickChildViewWithId(R.id.secondaryActionButton)));
    }

    @Test
    public void protectedFeedAsksForCredentialsAndSubscribes() {
        String url = publishProtectedFeed(USER, PASSWORD, EPISODE_A);

        Feed feed = subscribeWithCredentials(url);

        assertEquals(USER, feed.getPreferences().getUsername());
        assertEquals(PASSWORD, feed.getPreferences().getPassword());
        assertEquals(1, feed.getItems().size());
        assertSawAuthorization(FEED_PATH, null);
        assertSawAuthorization(FEED_PATH, basicHeader(USER, PASSWORD));
    }

    @Test
    public void wrongCredentialsAreRejectedAndAskedAgain() {
        String url = publishProtectedFeed(USER, PASSWORD, EPISODE_A);

        FeedRobot.addFeedByUrl(url);
        enterCredentials(USER, "wrong");
        FeedRobot.awaitCondition(() -> server.requestsFor(FEED_PATH).size() >= 2);
        FeedRobot.awaitDialogText(R.string.authentication_notification_title);
        enterCredentials(USER, PASSWORD);
        FeedRobot.subscribeToPreviewedFeed();

        Feed feed = FeedRobot.awaitFeed(url);
        assertEquals(PASSWORD, feed.getPreferences().getPassword());
        assertSawAuthorization(FEED_PATH, basicHeader(USER, "wrong"));
    }

    @Test
    public void cancellingTheCredentialDialogSubscribesToNothing() {
        String url = publishProtectedFeed(USER, PASSWORD, EPISODE_A);

        FeedRobot.addFeedByUrl(url);
        FeedRobot.awaitDialogText(R.string.authentication_notification_title);
        FeedRobot.confirmDialog(R.string.cancel_label);

        waitUntilDisplayed(withId(R.id.addViaUrlButton), FeedRobot.UI_TIMEOUT_MS);
        assertTrue(DBReader.getFeedList().isEmpty());
    }

    @Test
    public void storedCredentialsAreSentOnRefreshAndEpisodeDownload() throws Exception {
        String url = publishProtectedFeed(USER, PASSWORD, EPISODE_A);
        server.publishProtected("/media/b.mp3", "audio/mpeg", "not really audio", USER, PASSWORD);
        Feed feed = subscribeWithCredentials(url);
        publishProtectedFeed(USER, PASSWORD, EPISODE_A, EPISODE_B);

        FeedRobot.refreshFromMenu();
        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getItems().size() == 2);
        downloadEpisode("Episode B");

        FeedRobot.awaitCondition(() -> FeedRobot.itemByGuid(FeedRobot.reload(feed), "b").getMedia().isDownloaded());
        assertSawAuthorization("/media/b.mp3", basicHeader(USER, PASSWORD));
        assertEquals("not really audio".length(),
                new File(FeedRobot.itemByGuid(FeedRobot.reload(feed), "b").getMedia().getLocalFileUrl()).length());
    }

    @Test
    public void changedServerCredentialsFailUntilUpdatedInFeedSettings() throws Exception {
        String url = publishProtectedFeed(USER, PASSWORD, EPISODE_A);
        Feed feed = subscribeWithCredentials(url);
        publishProtectedFeed("bob", "hunter2", EPISODE_A, EPISODE_B);

        FeedRobot.refreshFromMenu();
        FeedRobot.awaitCondition(() -> !DBReader.getFeedDownloadLog(feed.getId(), 1).isEmpty()
                && !DBReader.getFeedDownloadLog(feed.getId(), 1).get(0).isSuccessful());
        DownloadResult failure = DBReader.getFeedDownloadLog(feed.getId(), 1).get(0);
        assertEquals(DownloadError.ERROR_UNAUTHORIZED, failure.getReason());
        assertEquals(1, FeedRobot.reload(feed).getItems().size());

        FeedRobot.openFeedSettings();
        FeedRobot.clickSetting(R.string.authentication_label);
        enterCredentials("bob", "hunter2");

        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getItems().size() == 2);
        assertEquals("bob", FeedRobot.reload(feed).getPreferences().getUsername());
        assertEquals("hunter2", FeedRobot.reload(feed).getPreferences().getPassword());
    }
}
