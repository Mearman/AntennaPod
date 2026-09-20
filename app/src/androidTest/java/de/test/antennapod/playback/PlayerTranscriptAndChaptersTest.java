package de.test.antennapod.playback;

import android.content.Intent;
import android.view.View;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.ui.FeedRobot;
import de.test.antennapod.util.TestAssets;
import de.test.antennapod.util.media.MediaFixtures;
import de.test.antennapod.util.media.MediaFixtures.ChapterSpec;
import de.test.antennapod.util.service.download.StaticContentServer;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickChildViewWithId;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class PlayerTranscriptAndChaptersTest {
    private static final long PLAYER_TIMEOUT_MS = 60000;

    private static final List<ChapterSpec> FILE_CHAPTERS = Arrays.asList(
            new ChapterSpec(0, "File chapter one"),
            new ChapterSpec(10000, "File chapter two", "https://example.com/file-two", null),
            new ChapterSpec(20000, "File chapter three"));

    private StaticContentServer server;

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        UserPreferences.setAllowMobileFeedRefresh(true);
        UserPreferences.setAllowMobileStreaming(true);
        UserPreferences.setAllowMobileEpisodeDownload(true);
        UserPreferences.setStreamOverDownload(true);
        EspressoTestUtils.setLaunchScreen(AddFeedFragment.TAG);
        server = new StaticContentServer();
        server.start();
        activityRule.launchActivity(new Intent());
    }

    @After
    public void tearDown() throws Exception {
        activityRule.finishActivity();
        EspressoTestUtils.tryKillPlaybackService();
        server.stop();
        for (Feed candidate : DBReader.getFeedList()) {
            Feed feed = DBReader.getFeed(candidate.getId(), false, 0, Integer.MAX_VALUE);
            for (FeedItem item : feed.getItems()) {
                if (item.hasMedia() && item.getMedia().getLocalFileUrl() != null) {
                    FileUtils.deleteQuietly(new File(item.getMedia().getLocalFileUrl()));
                    FileUtils.deleteQuietly(new File(item.getMedia().getTranscriptFileUrl()));
                }
            }
        }
        PodDBAdapter.deleteDatabase();
    }

    private Feed subscribeToEpisode(byte[] audio, String extraTags) throws Exception {
        server.publish("/media/episode.mp3", "audio/mpeg", audio);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\" xmlns:podcast=\"https://podcastindex.org/namespace/1.0\""
                + " xmlns:psc=\"http://podlove.org/simple-chapters\"><channel>"
                + "<title>Playback Feed</title><link>https://example.com/playback</link>"
                + "<item><guid>episode</guid><title>Playable episode</title>"
                + "<pubDate>10 Jan 2023 10:00:00 +0000</pubDate>"
                + "<enclosure url=\"" + server.getBaseUrl() + "/media/episode.mp3\" length=\"" + audio.length
                + "\" type=\"audio/mpeg\"/>" + extraTags + "</item></channel></rss>";
        String url = server.publish("/feeds/playback.xml", "application/rss+xml", xml);
        FeedRobot.subscribeByUrl(url);
        return FeedRobot.awaitFeed(url);
    }

    private String transcriptTag(String path, String type, String assetName) throws Exception {
        server.publish(path, type, TestAssets.readText(assetName));
        return "<podcast:transcript url=\"" + server.getBaseUrl() + path + "\" type=\"" + type + "\"/>";
    }

    private void startPlayback(Feed feed) {
        FeedItem item = FeedRobot.itemByGuid(FeedRobot.reload(feed), "episode");
        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.actionOnItem(
                hasDescendant(withText("Playable episode")), clickChildViewWithId(R.id.secondaryActionButton)));
        Awaitility.await().atMost(PLAYER_TIMEOUT_MS, TimeUnit.MILLISECONDS).until(
                () -> item.getMedia().getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
    }

    private void openPlayer() {
        waitForViewGlobally(allOf(withId(R.id.fragmentLayout), isDisplayed()), PLAYER_TIMEOUT_MS);
        onView(allOf(withId(R.id.fragmentLayout), isDisplayed())).perform(click());
        waitForViewGlobally(allOf(withId(R.id.txtvEpisodeTitle), withText("Playable episode")), PLAYER_TIMEOUT_MS);
    }

    private void openPlayerMenu() {
        Matcher<View> overflow = allOf(withContentDescription("More options"),
                isDescendantOfA(withId(R.id.playerContent)), isDisplayed());
        waitForViewGlobally(overflow, PLAYER_TIMEOUT_MS);
        onView(overflow).perform(click());
    }

    private void openChapterList() {
        waitForViewGlobally(allOf(withId(R.id.chapterButton), isDisplayed()), PLAYER_TIMEOUT_MS);
        onView(allOf(withId(R.id.chapterButton), isDisplayed())).perform(click());
    }

    private void assertTranscriptShows(String text, String speaker) {
        waitForViewGlobally(withText(containsString(text)), PLAYER_TIMEOUT_MS);
        waitForViewGlobally(withText(containsString(speaker)), PLAYER_TIMEOUT_MS);
    }

    @Test
    public void srtTranscriptIsShownForStreamedEpisode() throws Exception {
        Feed feed = subscribeToEpisode(MediaFixtures.plainAudio(MediaFixtures.LONG_AUDIO_ASSET),
                transcriptTag("/transcripts/episode.srt", "application/srt", "transcripts/ep1.srt"));

        startPlayback(feed);
        openPlayer();
        openPlayerMenu();
        onView(withText(R.string.show_transcript)).perform(click());

        assertTranscriptShows("Welcome to the subtitle show.", "Alice");
        assertEquals(1, server.requestsFor("/transcripts/episode.srt").size());
    }

    @Test
    public void vttTranscriptIsShownForStreamedEpisode() throws Exception {
        Feed feed = subscribeToEpisode(MediaFixtures.plainAudio(MediaFixtures.LONG_AUDIO_ASSET),
                transcriptTag("/transcripts/episode.vtt", "text/vtt", "transcripts/ep1.vtt"));

        startPlayback(feed);
        openPlayer();
        openPlayerMenu();
        onView(withText(R.string.show_transcript)).perform(click());

        assertTranscriptShows("Welcome to the captions show.", "Alice");
    }

    @Test
    public void jsonTranscriptIsShownForStreamedEpisode() throws Exception {
        Feed feed = subscribeToEpisode(MediaFixtures.plainAudio(MediaFixtures.LONG_AUDIO_ASSET),
                transcriptTag("/transcripts/episode.json", "application/json", "transcripts/ep1.json"));

        startPlayback(feed);
        openPlayer();
        openPlayerMenu();
        onView(withText(R.string.show_transcript)).perform(click());

        assertTranscriptShows("Welcome to the structured show.", "Alice");
    }

    @Test
    public void transcriptOfDownloadedEpisodeIsReadFromTheStoredFile() throws Exception {
        UserPreferences.setStreamOverDownload(false);
        Feed feed = subscribeToEpisode(MediaFixtures.plainAudio(MediaFixtures.LONG_AUDIO_ASSET),
                transcriptTag("/transcripts/episode.vtt", "text/vtt", "transcripts/ep1.vtt"));
        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.actionOnItem(
                hasDescendant(withText("Playable episode")), clickChildViewWithId(R.id.secondaryActionButton)));
        Awaitility.await().atMost(PLAYER_TIMEOUT_MS, TimeUnit.MILLISECONDS).until(() -> {
            List<DownloadResult> log = DBReader.getDownloadLog();
            return !log.isEmpty() && log.get(0).isSuccessful();
        });
        assertTrue(new File(FeedRobot.itemByGuid(FeedRobot.reload(feed), "episode").getMedia()
                .getTranscriptFileUrl()).exists());

        startPlayback(feed);
        openPlayer();
        openPlayerMenu();
        onView(withText(R.string.show_transcript)).perform(click());

        assertTranscriptShows("Welcome to the captions show.", "Alice");
        assertEquals(1, server.requestsFor("/transcripts/episode.vtt").size());
    }

    @Test
    public void transcriptEntryIsMissingWithoutTranscriptTag() throws Exception {
        Feed feed = subscribeToEpisode(MediaFixtures.plainAudio(MediaFixtures.LONG_AUDIO_ASSET), "");

        startPlayback(feed);
        openPlayer();
        openPlayerMenu();

        waitForViewGlobally(withText(R.string.open_podcast), PLAYER_TIMEOUT_MS);
        onView(withText(R.string.show_transcript)).check(doesNotExist());
    }

    @Test
    public void chaptersInsideTheStreamedFileAreListed() throws Exception {
        Feed feed = subscribeToEpisode(
                MediaFixtures.mp3WithChapters(MediaFixtures.LONG_AUDIO_ASSET, 3, FILE_CHAPTERS, null), "");

        startPlayback(feed);
        openPlayer();
        openChapterList();

        waitForViewGlobally(withText("File chapter one"), PLAYER_TIMEOUT_MS);
        onView(withText("File chapter two")).check(matches(isDisplayed()));
        onView(withText("https://example.com/file-two")).check(matches(isDisplayed()));
    }
}
