package de.test.antennapod.playback;

import android.content.Intent;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.ui.FeedRobot;
import de.test.antennapod.util.media.MediaFixtures;
import de.test.antennapod.util.service.download.StaticContentServer;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickChildViewWithId;
import static de.test.antennapod.ui.FeedRobot.waitUntilDisplayed;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;

@RunWith(AndroidJUnit4.class)
public class SleepTimerPlaybackTest {
    private static final long PLAYER_TIMEOUT_MS = 60000;

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
        PodDBAdapter.deleteDatabase();
    }

    private Feed subscribeToStreamableEpisode() throws Exception {
        byte[] audio = MediaFixtures.plainAudio(MediaFixtures.LONG_AUDIO_ASSET);
        server.publish("/media/episode.mp3", "audio/mpeg", audio);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\"><channel><title>Sleep Timer Feed</title>"
                + "<link>https://example.com/sleep</link>"
                + "<item><guid>episode</guid><title>Playable episode</title>"
                + "<pubDate>10 Jan 2023 10:00:00 +0000</pubDate>"
                + "<enclosure url=\"" + server.getBaseUrl() + "/media/episode.mp3\" length=\"" + audio.length
                + "\" type=\"audio/mpeg\"/></item></channel></rss>";
        String url = server.publish("/feeds/sleep.xml", "application/rss+xml", xml);
        FeedRobot.subscribeByUrl(url);
        return FeedRobot.awaitFeed(url);
    }

    private void startPlayback(Feed feed) {
        long mediaId = FeedRobot.itemByGuid(FeedRobot.reload(feed), "episode").getMedia().getId();
        waitUntilDisplayed(allOf(withId(R.id.secondaryActionButton), isDisplayed(), anyOf(
                withContentDescription(R.string.play_label), withContentDescription(R.string.stream_label))),
                PLAYER_TIMEOUT_MS);
        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.actionOnItem(
                hasDescendant(withText("Playable episode")), clickChildViewWithId(R.id.secondaryActionButton)));
        Awaitility.await().atMost(PLAYER_TIMEOUT_MS, TimeUnit.MILLISECONDS).until(
                () -> mediaId == PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
    }

    private void openPlayer() {
        waitUntilDisplayed(allOf(withId(R.id.fragmentLayout), isDisplayed()), PLAYER_TIMEOUT_MS);
        onView(allOf(withId(R.id.fragmentLayout), isDisplayed())).perform(click());
        waitUntilDisplayed(allOf(withId(R.id.txtvEpisodeTitle), withText("Playable episode")), PLAYER_TIMEOUT_MS);
    }

    @Test
    public void sleepTimerShowsRemainingTimeAndCanBeExtendedAndDisabled() throws Exception {
        Feed feed = subscribeToStreamableEpisode();

        startPlayback(feed);
        openPlayer();

        waitUntilDisplayed(withContentDescription(R.string.set_sleeptimer_label), PLAYER_TIMEOUT_MS);
        onView(withContentDescription(R.string.set_sleeptimer_label)).perform(click());

        waitUntilDisplayed(withId(R.id.timeEditText), PLAYER_TIMEOUT_MS);
        onView(withId(R.id.timeEditText)).perform(replaceText("1"));
        Espresso.closeSoftKeyboard();
        onView(withId(R.id.setSleeptimerButton)).perform(click());

        waitUntilDisplayed(withId(R.id.timeDisplayContainer), PLAYER_TIMEOUT_MS);
        FeedRobot.awaitAssertion(() -> onView(withId(R.id.time)).check(matches(anyOf(
                withText(containsString("00:01")), withText(containsString("00:00"))))));
        onView(withId(R.id.extendSleepFiveMinutesButton)).perform(click());
        FeedRobot.awaitAssertion(() -> onView(withId(R.id.time)).check(matches(anyOf(
                withText(containsString("00:06")), withText(containsString("00:05"))))));

        onView(withId(R.id.disableSleeptimerButton)).perform(click());

        waitUntilDisplayed(withId(R.id.timeSetupContainer), PLAYER_TIMEOUT_MS);
    }
}
