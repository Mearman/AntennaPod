package de.danoeh.antennapod.net.download.service.feed;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.net.download.service.R;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class NewEpisodesNotificationTest {
    private static final long FEED_ID = 7;

    private final Context context = RuntimeEnvironment.getApplication();
    private final PodDBAdapter adapter = Mockito.mock(PodDBAdapter.class);
    private final NotificationManager notifications =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    private MockedStatic<PodDBAdapter> database;

    @Before
    public void setUp() {
        database = Mockito.mockStatic(PodDBAdapter.class);
        database.when(PodDBAdapter::getInstance).thenReturn(adapter);
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    @After
    public void tearDown() {
        database.close();
    }

    private static Feed feed(boolean keepUpdated, boolean showNotification) {
        Feed feed = new Feed("http://example.com/feed.xml", null, "Podcast");
        feed.setId(FEED_ID);
        FeedPreferences preferences = new FeedPreferences(FEED_ID, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, null, null);
        preferences.setKeepUpdated(keepUpdated);
        preferences.setShowEpisodeNotification(showNotification);
        feed.setPreferences(preferences);
        return feed;
    }

    private void countersBefore(Map<Long, Integer> counters) {
        Mockito.when(adapter.getFeedCounters(FeedCounter.SHOW_NEW)).thenReturn(counters);
    }

    private void countersAfter(int count) {
        Mockito.when(adapter.getFeedCounters(FeedCounter.SHOW_NEW, FEED_ID))
                .thenReturn(Collections.singletonMap(FEED_ID, count));
    }

    private List<Notification> postedNotifications() {
        return shadowOf(notifications).getAllNotifications();
    }

    @Test
    public void countersAreReadWithinAnOpenDatabaseConnection() {
        countersBefore(Collections.emptyMap());

        new NewEpisodesNotification().loadCountersBeforeRefresh();

        InOrder order = Mockito.inOrder(adapter);
        order.verify(adapter).open();
        order.verify(adapter).getFeedCounters(FeedCounter.SHOW_NEW);
        order.verify(adapter).close();
    }

    @Test
    public void newEpisodesPostAFeedNotificationAndAGroupSummary() {
        countersBefore(Collections.singletonMap(FEED_ID, 1));
        countersAfter(3);
        NewEpisodesNotification newEpisodes = new NewEpisodesNotification();
        newEpisodes.loadCountersBeforeRefresh();

        newEpisodes.showIfNeeded(context, feed(true, true));

        List<Notification> posted = postedNotifications();
        assertEquals(2, posted.size());
        String expectedText = context.getResources().getQuantityString(
                R.plurals.new_episode_notification_message, 3, 3, "Podcast");
        boolean feedNotificationPosted = false;
        boolean summaryPosted = false;
        for (Notification notification : posted) {
            if (expectedText.equals(notification.extras.getString(Notification.EXTRA_TEXT))) {
                feedNotificationPosted = true;
            }
            if (context.getString(R.string.new_episode_notification_group_text)
                    .equals(notification.extras.getString(Notification.EXTRA_TITLE))) {
                summaryPosted = true;
            }
        }
        assertTrue(feedNotificationPosted);
        assertTrue(summaryPosted);
    }

    @Test
    public void feedWithNoPreviousCounterCountsFromZero() {
        countersBefore(Collections.emptyMap());
        countersAfter(1);
        NewEpisodesNotification newEpisodes = new NewEpisodesNotification();
        newEpisodes.loadCountersBeforeRefresh();

        newEpisodes.showIfNeeded(context, feed(true, true));

        String expectedText = context.getResources().getQuantityString(
                R.plurals.new_episode_notification_message, 1, 1, "Podcast");
        boolean feedNotificationPosted = false;
        for (Notification notification : postedNotifications()) {
            if (expectedText.equals(notification.extras.getString(Notification.EXTRA_TEXT))) {
                feedNotificationPosted = true;
            }
        }
        assertTrue(feedNotificationPosted);
    }

    @Test
    public void noNotificationWhenNoNewEpisodesArrived() {
        countersBefore(Collections.singletonMap(FEED_ID, 2));
        countersAfter(2);
        NewEpisodesNotification newEpisodes = new NewEpisodesNotification();
        newEpisodes.loadCountersBeforeRefresh();

        newEpisodes.showIfNeeded(context, feed(true, true));

        assertTrue(postedNotifications().isEmpty());
    }

    @Test
    public void noNotificationWhenEpisodesWereRemoved() {
        countersBefore(Collections.singletonMap(FEED_ID, 5));
        countersAfter(2);
        NewEpisodesNotification newEpisodes = new NewEpisodesNotification();
        newEpisodes.loadCountersBeforeRefresh();

        newEpisodes.showIfNeeded(context, feed(true, true));

        assertTrue(postedNotifications().isEmpty());
    }

    @Test
    public void noNotificationWhenTheFeedDisabledEpisodeNotifications() {
        countersBefore(Collections.emptyMap());
        countersAfter(4);
        NewEpisodesNotification newEpisodes = new NewEpisodesNotification();
        newEpisodes.loadCountersBeforeRefresh();

        newEpisodes.showIfNeeded(context, feed(true, false));

        assertTrue(postedNotifications().isEmpty());
    }

    @Test
    public void noNotificationWhenTheFeedIsNotKeptUpdated() {
        countersBefore(Collections.emptyMap());
        countersAfter(4);
        NewEpisodesNotification newEpisodes = new NewEpisodesNotification();
        newEpisodes.loadCountersBeforeRefresh();

        newEpisodes.showIfNeeded(context, feed(false, true));

        assertTrue(postedNotifications().isEmpty());
    }

    @Test
    public void noNotificationWithoutNotificationPermission() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        countersBefore(Collections.emptyMap());
        countersAfter(4);
        NewEpisodesNotification newEpisodes = new NewEpisodesNotification();
        newEpisodes.loadCountersBeforeRefresh();

        newEpisodes.showIfNeeded(context, feed(true, true));

        assertTrue(postedNotifications().isEmpty());
    }
}
