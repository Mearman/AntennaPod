package de.test.antennapod.ui;

import android.content.Intent;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.EditText;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.GeneralClickAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Tap;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFilter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.service.download.StaticContentServer;
import com.google.android.material.chip.Chip;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickBottomNavItem;
import static de.test.antennapod.ui.FeedRobot.waitUntilDisplayed;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FeedPreferencesTest {
    private static final String FEED_PATH = "/feeds/preferences.xml";
    private static final String OTHER_FEED_PATH = "/feeds/other.xml";
    private static final String RSS_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel>";
    private static final String RSS_TAIL = "</channel></rss>";
    private static final float CLOSE_ICON_END_OFFSET_DP = 24;

    private StaticContentServer server;

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        UserPreferences.setAllowMobileFeedRefresh(true);
        EspressoTestUtils.setLaunchScreen(AddFeedFragment.TAG);
        server = new StaticContentServer();
        server.start();
        activityRule.launchActivity(new Intent());
    }

    @After
    public void tearDown() {
        server.stop();
        PodDBAdapter.deleteDatabase();
    }

    private static String episode(String guid, String title, String pubDate) {
        return "<item><guid>" + guid + "</guid><title>" + title + "</title><pubDate>" + pubDate + "</pubDate>"
                + "<enclosure url=\"${BASE}/media/" + guid + ".mp3\" length=\"20000\" type=\"audio/mpeg\"/></item>";
    }

    private static final String EPISODE_A = episode("a", "Episode A", "Mon, 02 Jan 2023 10:00:00 +0000");
    private static final String EPISODE_B = episode("b", "Episode B", "Tue, 03 Jan 2023 10:00:00 +0000");
    private static final String EPISODE_C = episode("c", "Episode C", "Wed, 04 Jan 2023 10:00:00 +0000");

    private String publishFeed(String path, String title, String... items) {
        return server.publish(path, "application/rss+xml", RSS_HEAD + "<title>" + title
                + "</title><link>https://example.com/preferences</link>" + String.join("", items) + RSS_TAIL);
    }

    private Feed subscribeToFeed(String... items) {
        String url = publishFeed(FEED_PATH, "Preferences Feed", items);
        FeedRobot.subscribeByUrl(url);
        return FeedRobot.awaitFeed(url);
    }

    private static FeedPreferences preferences(Feed feed) {
        return FeedRobot.reload(feed).getPreferences();
    }

    private void refreshFromFeedScreen() throws Exception {
        Espresso.pressBack();
        waitUntilDisplayed(withId(R.id.butShowSettings), FeedRobot.UI_TIMEOUT_MS);
        FeedRobot.refreshFromMenu();
    }

    private static ViewAction clickCloseIcon() {
        return new GeneralClickAction(Tap.SINGLE, view -> {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            float closeIconOffset = view.getResources().getDisplayMetrics().density * CLOSE_ICON_END_OFFSET_DP;
            return new float[] {location[0] + view.getWidth() - closeIconOffset, location[1] + view.getHeight() / 2f};
        }, Press.FINGER, InputDevice.SOURCE_UNKNOWN, MotionEvent.BUTTON_PRIMARY);
    }

    private static boolean isSettingListed(int titleRes) {
        try {
            onView(withId(R.id.recycler_view)).perform(
                    RecyclerViewActions.scrollTo(hasDescendant(withText(titleRes))));
            return true;
        } catch (PerformException e) {
            return false;
        }
    }

    private static List<String> titles(List<FeedItem> items) {
        List<String> titles = new ArrayList<>();
        for (FeedItem item : items) {
            titles.add(item.getTitle());
        }
        return titles;
    }

    @Test
    public void disablingKeepUpdatedExcludesTheFeedFromRefreshAll() throws Exception {
        String otherUrl = publishFeed(OTHER_FEED_PATH, "Other Feed", EPISODE_B);
        FeedRobot.subscribeByUrl(otherUrl);
        FeedRobot.awaitFeed(otherUrl);
        Espresso.pressBack();
        waitUntilDisplayed(withId(R.id.addViaUrlButton), FeedRobot.UI_TIMEOUT_MS);
        Feed feed = subscribeToFeed(EPISODE_A);
        FeedRobot.openFeedSettings();
        FeedRobot.clickSetting(R.string.keep_updated);
        FeedRobot.awaitCondition(() -> !preferences(feed).getKeepUpdated());
        int requestsBefore = server.requestsFor(FEED_PATH).size();
        int otherRequestsBefore = server.requestsFor(OTHER_FEED_PATH).size();

        FeedRobot.refreshAllFromSubscriptions();

        FeedRobot.awaitCondition(() -> server.requestsFor(OTHER_FEED_PATH).size() > otherRequestsBefore);
        assertEquals(requestsBefore, server.requestsFor(FEED_PATH).size());
    }

    @Test
    public void enablingKeepUpdatedAgainIncludesTheFeedInRefreshAll() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        FeedRobot.openFeedSettings();
        FeedRobot.clickSetting(R.string.keep_updated);
        FeedRobot.awaitCondition(() -> !preferences(feed).getKeepUpdated());
        FeedRobot.clickSetting(R.string.keep_updated);
        FeedRobot.awaitCondition(() -> preferences(feed).getKeepUpdated());
        int requestsBefore = server.requestsFor(FEED_PATH).size();

        FeedRobot.refreshAllFromSubscriptions();

        FeedRobot.awaitCondition(() -> server.requestsFor(FEED_PATH).size() > requestsBefore);
    }

    @Test
    public void renamingTheFeedSetsACustomTitleAndResetRestoresTheOriginal() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        FeedRobot.openFeedSettings();

        FeedRobot.clickSetting(R.string.rename_feed_label);
        onView(withId(R.id.textInput)).perform(replaceText("My own name"));
        FeedRobot.confirmTypedDialog(android.R.string.ok);

        FeedRobot.awaitCondition(() -> "My own name".equals(FeedRobot.reload(feed).getTitle()));
        assertEquals("My own name", FeedRobot.reload(feed).getCustomTitle());
        assertEquals("Preferences Feed", FeedRobot.reload(feed).getFeedTitle());

        FeedRobot.clickSetting(R.string.rename_feed_label);
        waitUntilDisplayed(withId(R.id.textInput), FeedRobot.UI_TIMEOUT_MS);
        onView(withId(R.id.textInput)).check(matches(withText("My own name")));
        onView(withId(R.id.textInput)).perform(replaceText("Something else"));
        onView(withText(R.string.reset)).inRoot(isDialog()).perform(click());
        onView(withId(R.id.textInput)).check(matches(withText("My own name")));
        onView(withId(R.id.textInput)).perform(replaceText("Preferences Feed"));
        FeedRobot.confirmTypedDialog(android.R.string.ok);

        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getCustomTitle() == null);
        assertEquals("Preferences Feed", FeedRobot.reload(feed).getTitle());
    }

    @Test
    public void tagsAreStoredAndListedInTheSubscriptions() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        FeedRobot.openFeedSettings();

        FeedRobot.clickSetting(R.string.feed_tags_label);
        waitUntilDisplayed(withId(R.id.newTagEditText), FeedRobot.UI_TIMEOUT_MS);
        onView(withId(R.id.newTagEditText)).perform(replaceText("Comedy"));
        onView(allOf(withId(R.id.text_input_end_icon), isDescendantOfA(withId(R.id.newTagTextInput))))
                .perform(click());
        onView(withId(R.id.newTagEditText)).perform(replaceText("News"));
        FeedRobot.confirmTypedDialog(android.R.string.ok);

        FeedRobot.awaitCondition(() -> preferences(feed).getTags().contains("Comedy")
                && preferences(feed).getTags().contains("News"));
        clickBottomNavItem(R.string.subscriptions_label_short);
        waitUntilDisplayed(allOf(withId(R.id.tag_chip), withText("Comedy")), FeedRobot.UI_TIMEOUT_MS);
        onView(allOf(withId(R.id.tag_chip), withText("News"))).check(matches(isDisplayed()));
    }

    @Test
    public void removedTagIsDroppedFromThePreferences() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        FeedPreferences stored = preferences(feed);
        stored.getTags().add("Temporary");
        DBWriter.setFeedPreferences(stored);
        FeedRobot.awaitCondition(() -> preferences(feed).getTags().contains("Temporary"));
        FeedRobot.openFeedSettings();

        FeedRobot.clickSetting(R.string.feed_tags_label);
        waitUntilDisplayed(withText("Temporary"), FeedRobot.UI_TIMEOUT_MS);
        onView(allOf(instanceOf(Chip.class), withText("Temporary"))).perform(clickCloseIcon());
        FeedRobot.confirmDialog(android.R.string.ok);

        FeedRobot.awaitCondition(() -> !preferences(feed).getTags().contains("Temporary"));
    }

    @Test
    public void playbackSpeedAndSilenceSkippingCanBeSetForOneFeed() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL, preferences(feed).getFeedPlaybackSpeed(), 0);
        FeedRobot.openFeedSettings();

        FeedRobot.clickSetting(R.string.playback_speed);
        waitUntilDisplayed(withId(R.id.useGlobalCheckbox), FeedRobot.UI_TIMEOUT_MS);
        onView(withId(R.id.useGlobalCheckbox)).check(matches(isChecked()));
        onView(withId(R.id.useGlobalCheckbox)).perform(click());
        onView(withId(R.id.skipSilenceFeed)).perform(click());
        FeedRobot.confirmDialog(android.R.string.ok);

        FeedRobot.awaitCondition(() -> preferences(feed).getFeedSkipSilence()
                == FeedPreferences.SkipSilence.AGGRESSIVE);
        assertTrue(preferences(feed).getFeedPlaybackSpeed() > 0);

        FeedRobot.clickSetting(R.string.playback_speed);
        waitUntilDisplayed(withId(R.id.useGlobalCheckbox), FeedRobot.UI_TIMEOUT_MS);
        onView(withId(R.id.useGlobalCheckbox)).check(matches(isNotChecked()));
        onView(withId(R.id.skipSilenceFeed)).check(matches(isChecked()));
        onView(withId(R.id.useGlobalCheckbox)).perform(click());
        FeedRobot.confirmDialog(android.R.string.ok);

        FeedRobot.awaitCondition(() -> preferences(feed).getFeedSkipSilence()
                == FeedPreferences.SkipSilence.GLOBAL);
        assertEquals(FeedPreferences.SPEED_USE_GLOBAL, preferences(feed).getFeedPlaybackSpeed(), 0);
    }

    @Test
    public void silenceSkippingCanBeTurnedOffForOneFeed() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        FeedRobot.openFeedSettings();

        FeedRobot.clickSetting(R.string.playback_speed);
        waitUntilDisplayed(withId(R.id.useGlobalCheckbox), FeedRobot.UI_TIMEOUT_MS);
        onView(withId(R.id.useGlobalCheckbox)).perform(click());
        FeedRobot.confirmDialog(android.R.string.ok);

        FeedRobot.awaitCondition(() -> preferences(feed).getFeedSkipSilence() == FeedPreferences.SkipSilence.OFF);
    }

    @Test
    public void skippedIntroAndEndingAreStoredInSeconds() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        FeedRobot.openFeedSettings();

        FeedRobot.clickSetting(R.string.pref_feed_skip);
        waitUntilDisplayed(withId(R.id.etxtSkipIntro), FeedRobot.UI_TIMEOUT_MS);
        onView(withId(R.id.etxtSkipIntro)).perform(replaceText("12"));
        onView(withId(R.id.etxtSkipEnd)).perform(replaceText("7"));
        FeedRobot.confirmTypedDialog(R.string.confirm_label);

        FeedRobot.awaitCondition(() -> preferences(feed).getFeedSkipIntro() == 12);
        assertEquals(7, preferences(feed).getFeedSkipEnding());

        FeedRobot.clickSetting(R.string.pref_feed_skip);
        waitUntilDisplayed(withId(R.id.etxtSkipIntro), FeedRobot.UI_TIMEOUT_MS);
        onView(withId(R.id.etxtSkipIntro)).check(matches(withText("12")));
        onView(withId(R.id.etxtSkipIntro)).perform(replaceText(""));
        onView(withId(R.id.etxtSkipEnd)).perform(replaceText(""));
        FeedRobot.confirmTypedDialog(R.string.confirm_label);

        FeedRobot.awaitCondition(() -> preferences(feed).getFeedSkipIntro() == 0);
        assertEquals(0, preferences(feed).getFeedSkipEnding());
    }

    @Test
    public void volumeReductionIsStoredForTheFeed() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        assertEquals(VolumeAdaptionSetting.OFF, preferences(feed).getVolumeAdaptionSetting());
        FeedRobot.openFeedSettings();

        FeedRobot.clickSetting(R.string.feed_volume_adapdation);
        FeedRobot.chooseOption(R.string.feed_volume_reduction_light);

        FeedRobot.awaitCondition(() -> preferences(feed).getVolumeAdaptionSetting()
                == VolumeAdaptionSetting.LIGHT_REDUCTION);
    }

    @Test
    public void autoDeleteChoiceIsStoredAndShownInTheSummary() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        assertEquals(FeedPreferences.AutoDeleteAction.GLOBAL, preferences(feed).getAutoDeleteAction());
        FeedRobot.openFeedSettings();

        FeedRobot.clickSetting(R.string.pref_auto_delete_playback_title);
        FeedRobot.chooseOption(R.string.feed_auto_download_always);

        FeedRobot.awaitCondition(() -> preferences(feed).getAutoDeleteAction()
                == FeedPreferences.AutoDeleteAction.ALWAYS);
        assertTrue(preferences(feed).getCurrentAutoDelete() == FeedPreferences.AutoDeleteAction.ALWAYS);

        FeedRobot.clickSetting(R.string.pref_auto_delete_playback_title);
        FeedRobot.chooseOption(R.string.feed_auto_download_never);

        FeedRobot.awaitCondition(() -> preferences(feed).getAutoDeleteAction()
                == FeedPreferences.AutoDeleteAction.NEVER);
    }

    @Test
    public void newEpisodesCanBeAddedToTheQueueOnRefresh() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A, EPISODE_B);
        FeedRobot.openFeedSettings();
        FeedRobot.clickSetting(R.string.pref_new_episodes_action_title);
        FeedRobot.chooseOption(R.string.feed_new_episodes_action_add_to_queue);
        FeedRobot.awaitCondition(() -> preferences(feed).getNewEpisodesAction()
                == FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE);
        assertTrue(DBReader.getQueue().isEmpty());
        publishFeed(FEED_PATH, "Preferences Feed", EPISODE_A, EPISODE_B, EPISODE_C);

        refreshFromFeedScreen();

        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getItems().size() == 3);
        FeedRobot.awaitCondition(() -> !DBReader.getQueue().isEmpty());
        assertEquals(Arrays.asList("Episode C"), titles(DBReader.getQueue()));
        assertFalse(FeedRobot.itemByGuid(FeedRobot.reload(feed), "c").isNew());
    }

    @Test
    public void newEpisodesCanBeKeptOutOfInboxAndQueue() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A, EPISODE_B);
        FeedRobot.openFeedSettings();
        FeedRobot.clickSetting(R.string.pref_new_episodes_action_title);
        FeedRobot.chooseOption(R.string.feed_new_episodes_action_nothing);
        FeedRobot.awaitCondition(() -> preferences(feed).getNewEpisodesAction()
                == FeedPreferences.NewEpisodesAction.NOTHING);
        publishFeed(FEED_PATH, "Preferences Feed", EPISODE_A, EPISODE_B, EPISODE_C);

        refreshFromFeedScreen();

        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getItems().size() == 3);
        assertFalse(FeedRobot.itemByGuid(FeedRobot.reload(feed), "c").isNew());
        assertTrue(DBReader.getQueue().isEmpty());
    }

    @Test
    public void newEpisodesCanBeAddedToTheInbox() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A, EPISODE_B);
        FeedRobot.openFeedSettings();
        FeedRobot.clickSetting(R.string.pref_new_episodes_action_title);
        FeedRobot.chooseOption(R.string.feed_new_episodes_action_add_to_inbox);
        FeedRobot.awaitCondition(() -> preferences(feed).getNewEpisodesAction()
                == FeedPreferences.NewEpisodesAction.ADD_TO_INBOX);
        publishFeed(FEED_PATH, "Preferences Feed", EPISODE_A, EPISODE_B, EPISODE_C);

        refreshFromFeedScreen();

        FeedRobot.awaitCondition(() -> FeedRobot.reload(feed).getItems().size() == 3);
        assertTrue(FeedRobot.itemByGuid(FeedRobot.reload(feed), "c").isNew());
        assertTrue(DBReader.getQueue().isEmpty());
    }

    @Test
    public void autoDownloadCanBeEnabledAndEpisodeFilterIsStored() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        assertFalse(preferences(feed).isAutoDownload(false));
        FeedRobot.openFeedSettings();
        FeedRobot.clickSetting(R.string.auto_download_label);
        FeedRobot.chooseOption(R.string.enabled);
        FeedRobot.awaitCondition(() -> preferences(feed).isAutoDownload(false));

        FeedRobot.clickSetting(R.string.episode_filters_label);
        waitUntilDisplayed(withId(R.id.includeRadio), FeedRobot.UI_TIMEOUT_MS);
        onView(withId(R.id.includeRadio)).check(matches(isChecked()));
        addFilterTerm("Interview");
        addFilterTerm("Special edition");
        onView(withId(R.id.durationCheckBox)).perform(click());
        onView(withId(R.id.episodeFilterDurationText)).perform(replaceText("5"));
        FeedRobot.confirmTypedDialog(R.string.confirm_label);

        FeedRobot.awaitCondition(() -> preferences(feed).getFilter().hasIncludeFilter());
        FeedFilter filter = preferences(feed).getFilter();
        assertEquals(Arrays.asList("Interview", "Special edition"), filter.getIncludeFilter());
        assertTrue(filter.getExcludeFilter().isEmpty());
        assertEquals(5 * 60, filter.getMinimalDurationFilter());

        FeedRobot.clickSetting(R.string.episode_filters_label);
        waitUntilDisplayed(withText("Interview"), FeedRobot.UI_TIMEOUT_MS);
        onView(withText("Special edition")).check(matches(isDisplayed()));
        onView(withId(R.id.durationCheckBox)).check(matches(isChecked()));
        onView(withId(R.id.episodeFilterDurationText)).check(matches(withText("5")));
    }

    @Test
    public void excludedTermsAreStoredSeparatelyFromIncludedOnes() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        FeedRobot.openFeedSettings();
        FeedRobot.clickSetting(R.string.auto_download_label);
        FeedRobot.chooseOption(R.string.enabled);
        FeedRobot.awaitCondition(() -> preferences(feed).isAutoDownload(false));

        FeedRobot.clickSetting(R.string.episode_filters_label);
        waitUntilDisplayed(withId(R.id.excludeRadio), FeedRobot.UI_TIMEOUT_MS);
        onView(withId(R.id.excludeRadio)).perform(click());
        addFilterTerm("Trailer");
        FeedRobot.confirmTypedDialog(R.string.confirm_label);

        FeedRobot.awaitCondition(() -> preferences(feed).getFilter().excludeOnly()
                && !preferences(feed).getFilter().getExcludeFilter().isEmpty());
        FeedFilter filter = preferences(feed).getFilter();
        assertEquals(Arrays.asList("Trailer"), filter.getExcludeFilter());
        assertFalse(filter.hasIncludeFilter());
        assertEquals(-1, filter.getMinimalDurationFilter());
    }

    @Test
    public void disablingAutoDownloadHidesTheEpisodeFilter() throws Exception {
        Feed feed = subscribeToFeed(EPISODE_A);
        FeedRobot.openFeedSettings();
        FeedRobot.clickSetting(R.string.auto_download_label);
        FeedRobot.chooseOption(R.string.enabled);
        FeedRobot.awaitCondition(() -> preferences(feed).isAutoDownload(false));
        assertTrue(isSettingListed(R.string.episode_filters_label));

        FeedRobot.clickSetting(R.string.auto_download_label);
        FeedRobot.chooseOption(R.string.disabled);

        FeedRobot.awaitCondition(() -> preferences(feed).getAutoDownload()
                == FeedPreferences.AutoDownloadSetting.DISABLED);
        assertFalse(isSettingListed(R.string.episode_filters_label));
    }

    private void addFilterTerm(String term) {
        onView(allOf(instanceOf(EditText.class), isDescendantOfA(withId(R.id.termsTextInput))))
                .perform(replaceText(term));
        onView(allOf(withId(R.id.text_input_end_icon), isDescendantOfA(withId(R.id.termsTextInput))))
                .perform(click());
    }
}
