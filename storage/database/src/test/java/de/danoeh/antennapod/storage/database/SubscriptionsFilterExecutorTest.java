package de.danoeh.antennapod.storage.database;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SubscriptionsFilter;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SubscriptionsFilterExecutorTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getContext();
        UserPreferences.init(context);
        setGlobalAutoDownload(false);
    }

    @Test
    public void emptyFilterKeepsEverySubscribedFeedInOrder() {
        Feed first = feed(1, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        Feed second = feed(2, FeedPreferences.AutoDownloadSetting.ENABLED, false, true, Feed.STATE_SUBSCRIBED);

        List<Feed> result = filter(List.of(first, second), new SubscriptionsFilter(""));

        assertEquals(List.of(first, second), result);
    }

    @Test
    public void nonSubscribedFeedsAreHiddenUnlessExplicitlyShown() {
        Feed subscribed = feed(1, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        Feed notSubscribed = feed(2, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false,
                Feed.STATE_NOT_SUBSCRIBED);
        Feed archived = feed(3, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_ARCHIVED);
        List<Feed> feeds = List.of(subscribed, notSubscribed, archived);

        assertEquals(List.of(subscribed, archived), filter(feeds, new SubscriptionsFilter("")));
        assertEquals(feeds, filter(feeds, new SubscriptionsFilter(SubscriptionsFilter.SHOW_NON_SUBSCRIBED_FEEDS)));
    }

    @Test
    public void autoDownloadEnabledFilterUsesGlobalSettingForFeedsFollowingIt() {
        Feed explicit = feed(1, FeedPreferences.AutoDownloadSetting.ENABLED, true, false, Feed.STATE_SUBSCRIBED);
        Feed disabled = feed(2, FeedPreferences.AutoDownloadSetting.DISABLED, true, false, Feed.STATE_SUBSCRIBED);
        Feed followsGlobal = feed(3, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        SubscriptionsFilter filter = new SubscriptionsFilter(SubscriptionsFilter.ENABLED_AUTO_DOWNLOAD);

        assertEquals(List.of(explicit), filter(List.of(explicit, disabled, followsGlobal), filter));

        setGlobalAutoDownload(true);
        assertEquals(List.of(explicit, followsGlobal), filter(List.of(explicit, disabled, followsGlobal), filter));
    }

    @Test
    public void autoDownloadDisabledFilterUsesGlobalSettingForFeedsFollowingIt() {
        Feed explicit = feed(1, FeedPreferences.AutoDownloadSetting.ENABLED, true, false, Feed.STATE_SUBSCRIBED);
        Feed disabled = feed(2, FeedPreferences.AutoDownloadSetting.DISABLED, true, false, Feed.STATE_SUBSCRIBED);
        Feed followsGlobal = feed(3, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        SubscriptionsFilter filter = new SubscriptionsFilter(SubscriptionsFilter.DISABLED_AUTO_DOWNLOAD);

        assertEquals(List.of(disabled, followsGlobal), filter(List.of(explicit, disabled, followsGlobal), filter));

        setGlobalAutoDownload(true);
        assertEquals(List.of(disabled), filter(List.of(explicit, disabled, followsGlobal), filter));
    }

    @Test
    public void keepUpdatedFiltersSeparateEnabledFromDisabledFeeds() {
        Feed updated = feed(1, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        Feed paused = feed(2, FeedPreferences.AutoDownloadSetting.GLOBAL, false, false, Feed.STATE_SUBSCRIBED);
        List<Feed> feeds = List.of(updated, paused);

        assertEquals(List.of(updated), filter(feeds, new SubscriptionsFilter(SubscriptionsFilter.ENABLED_UPDATES)));
        assertEquals(List.of(paused), filter(feeds, new SubscriptionsFilter(SubscriptionsFilter.DISABLED_UPDATES)));
    }

    @Test
    public void episodeNotificationFiltersSeparateEnabledFromDisabledFeeds() {
        Feed notifying = feed(1, FeedPreferences.AutoDownloadSetting.GLOBAL, true, true, Feed.STATE_SUBSCRIBED);
        Feed silent = feed(2, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        List<Feed> feeds = List.of(notifying, silent);

        assertEquals(List.of(notifying),
                filter(feeds, new SubscriptionsFilter(SubscriptionsFilter.EPISODE_NOTIFICATION_ENABLED)));
        assertEquals(List.of(silent),
                filter(feeds, new SubscriptionsFilter(SubscriptionsFilter.EPISODE_NOTIFICATION_DISABLED)));
    }

    @Test
    public void counterFilterKeepsOnlyFeedsWithPositiveCounter() {
        Feed positive = feed(1, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        Feed zero = feed(2, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        Feed negative = feed(3, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        Feed missing = feed(4, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        Feed alsoPositive = feed(5, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        Map<Long, Integer> counters = new HashMap<>();
        counters.put(1L, 3);
        counters.put(2L, 0);
        counters.put(3L, -1);
        counters.put(5L, 1);

        List<Feed> result = SubscriptionsFilterExecutor.filter(
                List.of(positive, zero, negative, missing, alsoPositive), counters,
                new SubscriptionsFilter(SubscriptionsFilter.COUNTER_GREATER_ZERO));

        assertEquals(List.of(positive, alsoPositive), result);
    }

    @Test
    public void counterIsIgnoredWithoutCounterFilter() {
        Feed feed = feed(1, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        List<Feed> result = SubscriptionsFilterExecutor.filter(List.of(feed), Collections.emptyMap(),
                new SubscriptionsFilter(""));
        assertEquals(List.of(feed), result);
    }

    @Test
    public void allCriteriaMustMatchTogether() {
        Feed matching = feed(1, FeedPreferences.AutoDownloadSetting.ENABLED, true, true, Feed.STATE_SUBSCRIBED);
        Feed wrongUpdates = feed(2, FeedPreferences.AutoDownloadSetting.ENABLED, false, true, Feed.STATE_SUBSCRIBED);
        Feed wrongNotification = feed(3, FeedPreferences.AutoDownloadSetting.ENABLED, true, false,
                Feed.STATE_SUBSCRIBED);
        Feed wrongAutoDownload = feed(4, FeedPreferences.AutoDownloadSetting.DISABLED, true, true,
                Feed.STATE_SUBSCRIBED);
        SubscriptionsFilter filter = new SubscriptionsFilter(SubscriptionsFilter.ENABLED_AUTO_DOWNLOAD + ","
                + SubscriptionsFilter.ENABLED_UPDATES + "," + SubscriptionsFilter.EPISODE_NOTIFICATION_ENABLED);

        List<Feed> result = filter(List.of(matching, wrongUpdates, wrongNotification, wrongAutoDownload), filter);

        assertEquals(List.of(matching), result);
    }

    @Test
    public void filteringDoesNotModifyTheGivenList() {
        Feed subscribed = feed(1, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false, Feed.STATE_SUBSCRIBED);
        Feed notSubscribed = feed(2, FeedPreferences.AutoDownloadSetting.GLOBAL, true, false,
                Feed.STATE_NOT_SUBSCRIBED);
        List<Feed> input = new ArrayList<>(List.of(subscribed, notSubscribed));

        filter(input, new SubscriptionsFilter(""));

        assertEquals(2, input.size());
        assertTrue(input.contains(notSubscribed));
    }

    private List<Feed> filter(List<Feed> feeds, SubscriptionsFilter filter) {
        return SubscriptionsFilterExecutor.filter(feeds, Collections.emptyMap(), filter);
    }

    private void setGlobalAutoDownload(boolean enabled) {
        context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(UserPreferences.PREF_AUTODL_GLOBAL, enabled)
                .commit();
    }

    private Feed feed(long id, FeedPreferences.AutoDownloadSetting autoDownload, boolean keepUpdated,
                      boolean episodeNotification, int state) {
        Feed feed = new Feed(id, null, "Feed " + id, null, null, null, null, null, null, null, null, null, null,
                "url" + id, 0, false, null, null, null, false, state);
        FeedPreferences preferences = new FeedPreferences(id, autoDownload,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, null, null);
        preferences.setKeepUpdated(keepUpdated);
        preferences.setShowEpisodeNotification(episodeNotification);
        feed.setPreferences(preferences);
        return feed;
    }
}
