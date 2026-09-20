package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import androidx.preference.PreferenceManager;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.media.MediaFixtures;
import de.test.antennapod.util.service.download.StaticContentServer;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(AndroidJUnit4.class)
public class FeedAutoDownloadTest {
    private static final String FEED_PATH = "/feeds/auto.xml";
    private static final String OTHER_FEED_PATH = "/feeds/auto-other.xml";
    private static final String RSS_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\"><channel>";
    private static final String RSS_TAIL = "</channel></rss>";
    private static final int FILTER_MINUTES = 5;

    private StaticContentServer server;

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        UserPreferences.setAllowMobileFeedRefresh(true);
        UserPreferences.setAllowMobileAutoDownload(true);
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

    private String episode(String guid, String title, String pubDate, String duration) throws Exception {
        server.publish("/media/" + guid + ".mp3", "audio/mpeg", MediaFixtures.plainAudio());
        return "<item><guid>" + guid + "</guid><title>" + title + "</title><pubDate>" + pubDate + "</pubDate>"
                + "<itunes:duration>" + duration + "</itunes:duration>"
                + "<enclosure url=\"${BASE}/media/" + guid + ".mp3\" length=\"20000\" type=\"audio/mpeg\"/></item>";
    }

    private String publishFeed(String path, String title, String... items) {
        return server.publish(path, "application/rss+xml", RSS_HEAD + "<title>" + title
                + "</title><link>https://example.com/auto</link>" + String.join("", items) + RSS_TAIL);
    }

    private Feed subscribe(String url) {
        FeedRobot.subscribeByUrl(url);
        return FeedRobot.awaitFeed(url);
    }

    private void configure(Feed feed, FeedPreferences.AutoDownloadSetting setting, FeedFilter filter)
            throws Exception {
        FeedPreferences preferences = FeedRobot.reload(feed).getPreferences();
        preferences.setAutoDownload(setting);
        preferences.setFilter(filter);
        DBWriter.setFeedPreferences(preferences).get();
    }

    private void enableGlobalAutoDownload() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(UserPreferences.PREF_AUTODL_GLOBAL, true).commit();
    }

    private boolean isDownloaded(Feed feed, String guid) {
        return FeedRobot.itemByGuid(FeedRobot.reload(feed), guid).getMedia().isDownloaded();
    }

    private void assertRequested(String guid, boolean requested) {
        assertEquals(requested, !server.requestsFor("/media/" + guid + ".mp3").isEmpty());
    }

    @Test
    public void includeTermsLimitAutomaticDownloadsToMatchingTitles() throws Exception {
        String old = episode("old", "Old episode", "Mon, 02 Jan 2023 10:00:00 +0000", "00:20:00");
        Feed feed = subscribe(publishFeed(FEED_PATH, "Auto Feed", old));
        configure(feed, FeedPreferences.AutoDownloadSetting.ENABLED, new FeedFilter("\"interview\" ", ""));
        publishFeed(FEED_PATH, "Auto Feed", old,
                episode("guest", "Interview with a guest", "Tue, 03 Jan 2023 10:00:00 +0000", "00:20:00"),
                episode("ad", "Sponsored break", "Wed, 04 Jan 2023 10:00:00 +0000", "00:20:00"));

        FeedRobot.refreshFromMenu();

        FeedRobot.awaitCondition(() -> isDownloaded(feed, "guest"));
        assertFalse(isDownloaded(feed, "ad"));
        assertFalse(isDownloaded(feed, "old"));
        assertRequested("ad", false);
        assertRequested("old", false);
    }

    @Test
    public void excludedTermsAndShortEpisodesAreLeftOutOfAutomaticDownloads() throws Exception {
        String old = episode("old", "Old trailer", "Mon, 02 Jan 2023 10:00:00 +0000", "00:20:00");
        Feed feed = subscribe(publishFeed(FEED_PATH, "Auto Feed", old));
        configure(feed, FeedPreferences.AutoDownloadSetting.ENABLED,
                new FeedFilter("", "\"trailer\" ", FILTER_MINUTES * 60));
        publishFeed(FEED_PATH, "Auto Feed", old,
                episode("talk", "Long talk", "Tue, 03 Jan 2023 10:00:00 +0000", "00:30:00"),
                episode("bonus", "Bonus TRAILER", "Wed, 04 Jan 2023 10:00:00 +0000", "00:30:00"),
                episode("short", "Short talk", "Thu, 05 Jan 2023 10:00:00 +0000", "00:02:00"));

        FeedRobot.refreshFromMenu();

        FeedRobot.awaitCondition(() -> isDownloaded(feed, "talk"));
        assertFalse(isDownloaded(feed, "bonus"));
        assertFalse(isDownloaded(feed, "short"));
        assertRequested("bonus", false);
        assertRequested("short", false);
        assertRequested("old", false);
    }

    @Test
    public void feedSettingOverridesTheGlobalAutomaticDownloadSetting() throws Exception {
        enableGlobalAutoDownload();
        String optedOutUrl = publishFeed(OTHER_FEED_PATH, "Opted Out",
                episode("first", "Opted out one", "Mon, 02 Jan 2023 10:00:00 +0000", "00:20:00"));
        Feed optedOut = subscribe(optedOutUrl);
        Espresso.pressBack();
        waitForViewGlobally(withId(R.id.addViaUrlButton), FeedRobot.UI_TIMEOUT_MS);
        Feed following = subscribe(publishFeed(FEED_PATH, "Following Global",
                episode("second", "Following one", "Mon, 02 Jan 2023 10:00:00 +0000", "00:20:00")));
        configure(optedOut, FeedPreferences.AutoDownloadSetting.DISABLED, new FeedFilter());
        publishFeed(OTHER_FEED_PATH, "Opted Out",
                episode("first", "Opted out one", "Mon, 02 Jan 2023 10:00:00 +0000", "00:20:00"),
                episode("third", "Opted out two", "Tue, 03 Jan 2023 10:00:00 +0000", "00:20:00"));
        publishFeed(FEED_PATH, "Following Global",
                episode("second", "Following one", "Mon, 02 Jan 2023 10:00:00 +0000", "00:20:00"),
                episode("fourth", "Following two", "Tue, 03 Jan 2023 10:00:00 +0000", "00:20:00"));

        FeedRobot.refreshAllFromSubscriptions();

        FeedRobot.awaitCondition(() -> isDownloaded(following, "fourth"));
        FeedRobot.awaitCondition(() -> FeedRobot.reload(optedOut).getItems().size() == 2);
        assertFalse(isDownloaded(optedOut, "third"));
        assertRequested("third", false);
    }
}
