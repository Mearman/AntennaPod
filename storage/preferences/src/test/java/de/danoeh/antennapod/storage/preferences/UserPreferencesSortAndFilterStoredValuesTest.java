package de.danoeh.antennapod.storage.preferences;

import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.feed.SubscriptionsFilter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class UserPreferencesSortAndFilterStoredValuesTest extends StoredPreferencesTestBase {

    @Test
    public void queueKeepSortedIsOffAndSortsByNewestFirstUntilConfigured() {
        assertFalse(UserPreferences.isQueueKeepSorted());
        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getQueueKeepSortedOrder());
    }

    @Test
    public void queueKeepSortedOrderIsStoredIndependentlyOfTheKeepSortedFlag() {
        UserPreferences.setQueueKeepSortedOrder(SortOrder.EPISODE_TITLE_Z_A);

        assertEquals("EPISODE_TITLE_Z_A", stored.getString(UserPreferences.PREF_QUEUE_KEEP_SORTED_ORDER, null));
        assertEquals(SortOrder.EPISODE_TITLE_Z_A, UserPreferences.getQueueKeepSortedOrder());
        assertFalse(UserPreferences.isQueueKeepSorted());

        UserPreferences.setQueueKeepSorted(true);
        assertTrue(UserPreferences.isQueueKeepSorted());
        assertTrue(stored.getBoolean(UserPreferences.PREF_QUEUE_KEEP_SORTED, false));
    }

    @Test
    public void settingANullQueueSortOrderKeepsTheExistingOne() {
        UserPreferences.setQueueKeepSortedOrder(SortOrder.DURATION_LONG_SHORT);

        UserPreferences.setQueueKeepSortedOrder(null);

        assertEquals(SortOrder.DURATION_LONG_SHORT, UserPreferences.getQueueKeepSortedOrder());
    }

    @Test
    public void unknownQueueSortOrderNamesFallBackToNewestFirst() {
        stored.edit().putString(UserPreferences.PREF_QUEUE_KEEP_SORTED_ORDER, "no-such-order").commit();

        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getQueueKeepSortedOrder());
    }

    @Test
    public void newEpisodesActionDefaultsToTheInboxAndFollowsTheStoredCode() {
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX, UserPreferences.getNewEpisodesAction());

        stored.edit().putString(UserPreferences.PREF_NEW_EPISODES_ACTION,
                "" + FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE.code).commit();
        assertEquals(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE, UserPreferences.getNewEpisodesAction());

        stored.edit().putString(UserPreferences.PREF_NEW_EPISODES_ACTION,
                "" + FeedPreferences.NewEpisodesAction.NOTHING.code).commit();
        assertEquals(FeedPreferences.NewEpisodesAction.NOTHING, UserPreferences.getNewEpisodesAction());
    }

    @Test
    public void listSortOrdersAreStoredAsCodesAndDefaultToNewestFirst() {
        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getDownloadsSortedOrder());
        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getInboxSortedOrder());
        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getPrefGlobalSortedOrder());
        assertEquals(SortOrder.DATE_NEW_OLD, UserPreferences.getAllEpisodesSortOrder());

        UserPreferences.setDownloadsSortedOrder(SortOrder.SIZE_LARGE_SMALL);
        UserPreferences.setInboxSortedOrder(SortOrder.DATE_OLD_NEW);
        UserPreferences.setPrefGlobalSortedOrder(SortOrder.EPISODE_TITLE_A_Z);
        UserPreferences.setAllEpisodesSortOrder(SortOrder.FEED_TITLE_Z_A);

        assertEquals("" + SortOrder.SIZE_LARGE_SMALL.code, stored.getString("prefDownloadSortedOrder", null));
        assertEquals("" + SortOrder.DATE_OLD_NEW.code, stored.getString("prefInboxSortedOrder", null));
        assertEquals("" + SortOrder.EPISODE_TITLE_A_Z.code,
                stored.getString(UserPreferences.PREF_GLOBAL_DEFAULT_SORTED_ORDER, null));
        assertEquals("" + SortOrder.FEED_TITLE_Z_A.code,
                stored.getString(UserPreferences.PREF_SORT_ALL_EPISODES, null));
        assertEquals(SortOrder.SIZE_LARGE_SMALL, UserPreferences.getDownloadsSortedOrder());
        assertEquals(SortOrder.DATE_OLD_NEW, UserPreferences.getInboxSortedOrder());
        assertEquals(SortOrder.EPISODE_TITLE_A_Z, UserPreferences.getPrefGlobalSortedOrder());
        assertEquals(SortOrder.FEED_TITLE_Z_A, UserPreferences.getAllEpisodesSortOrder());
    }

    @Test
    public void subscriptionsFilterRoundTripsThroughItsSerialisedForm() {
        assertFalse(UserPreferences.getSubscriptionsFilter().isEnabled());

        UserPreferences.setSubscriptionsFilter(new SubscriptionsFilter(new String[] {
                SubscriptionsFilter.COUNTER_GREATER_ZERO, SubscriptionsFilter.DISABLED_AUTO_DOWNLOAD}));

        assertEquals("counter_greater_zero,disabled_auto_download",
                stored.getString(UserPreferences.PREF_FILTER_FEED, null));
        SubscriptionsFilter filter = UserPreferences.getSubscriptionsFilter();
        assertTrue(filter.isEnabled());
        assertTrue(filter.showIfCounterGreaterZero);
        assertTrue(filter.showAutoDownloadDisabled);
        assertFalse(filter.showAutoDownloadEnabled);
        assertArrayEquals(new String[] {SubscriptionsFilter.COUNTER_GREATER_ZERO,
                SubscriptionsFilter.DISABLED_AUTO_DOWNLOAD}, filter.getValues());
    }

    @Test
    public void subscriptionTitleAndAllEpisodesFilterRoundTrip() {
        assertFalse(UserPreferences.shouldShowSubscriptionTitle());
        assertEquals("", UserPreferences.getPrefFilterAllEpisodes());

        UserPreferences.setShouldShowSubscriptionTitle(true);
        UserPreferences.setPrefFilterAllEpisodes("unplayed,downloaded");

        assertTrue(stored.getBoolean(UserPreferences.PREF_SUBSCRIPTION_TITLE, false));
        assertTrue(UserPreferences.shouldShowSubscriptionTitle());
        assertEquals("unplayed,downloaded", stored.getString(UserPreferences.PREF_FILTER_ALL_EPISODES, null));
        assertEquals("unplayed,downloaded", UserPreferences.getPrefFilterAllEpisodes());
    }
}
