package de.danoeh.antennapod.net.download.service.feed;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.download.service.R;
import de.danoeh.antennapod.net.download.service.WorkManagerMocks;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedUpdateManagerImplTest {
    private static final String MANUAL_WORK_NAME = "feedUpdateManual";
    private static final String PERIODIC_WORK_NAME = "de.danoeh.antennapod.core.service.FeedUpdateWorker";
    private static long nextFeedId = 1000;

    private final Context context = RuntimeEnvironment.getApplication();
    private final FeedUpdateManagerImpl manager = new FeedUpdateManagerImpl();
    private final List<MessageEvent> messages = new ArrayList<>();
    private WorkManagerMocks workManager;
    private MockedStatic<UserPreferences> preferences;
    private MockedStatic<NetworkUtils> network;

    public static class Subscriber {
        private final List<MessageEvent> messages;

        Subscriber(List<MessageEvent> messages) {
            this.messages = messages;
        }

        @Subscribe
        public void onMessage(MessageEvent event) {
            messages.add(event);
        }
    }

    private final Subscriber subscriber = new Subscriber(messages);

    @Before
    public void setUp() {
        workManager = new WorkManagerMocks();
        preferences = Mockito.mockStatic(UserPreferences.class);
        network = Mockito.mockStatic(NetworkUtils.class);
        EventBus.getDefault().register(subscriber);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(subscriber);
        EventBus.getDefault().removeAllStickyEvents();
        network.close();
        preferences.close();
        workManager.close();
    }

    private static Feed uniqueFeed(String downloadUrl) {
        Feed feed = new Feed(downloadUrl, null, "Feed");
        feed.setId(nextFeedId++);
        return feed;
    }

    private static Feed remoteFeed() {
        return uniqueFeed("http://example.com/feed.xml");
    }

    private static Feed localFeed() {
        return uniqueFeed(Feed.PREFIX_LOCAL_FOLDER + "content://tree/folder");
    }

    private OneTimeWorkRequest manualRequest() {
        return workManager.enqueuedUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.REPLACE);
    }

    private static NetworkType networkOf(Constraints constraints) {
        return constraints.getRequiredNetworkType();
    }

    @Test
    public void periodicUpdateIsCancelledWhenAutoUpdateIsDisabled() {
        preferences.when(UserPreferences::isAutoUpdateDisabled).thenReturn(true);

        manager.restartUpdateAlarm(context, true);

        Mockito.verify(workManager.manager()).cancelUniqueWork(PERIODIC_WORK_NAME);
    }

    @Test
    public void periodicUpdateRunsHourlyOnUnmeteredNetworkByDefault() {
        manager.restartUpdateAlarm(context, false);

        PeriodicWorkRequest request = workManager.enqueuedUniquePeriodicWork(PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP);
        assertEquals(TimeUnit.HOURS.toMillis(1), request.getWorkSpec().intervalDuration);
        assertEquals(NetworkType.UNMETERED, networkOf(request.getWorkSpec().constraints));
    }

    @Test
    public void periodicUpdateUsesAnyNetworkWhenMobileRefreshIsAllowed() {
        preferences.when(UserPreferences::isAllowMobileFeedRefresh).thenReturn(true);

        manager.restartUpdateAlarm(context, false);

        PeriodicWorkRequest request = workManager.enqueuedUniquePeriodicWork(PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP);
        assertEquals(NetworkType.CONNECTED, networkOf(request.getWorkSpec().constraints));
    }

    @Test
    public void replacingTheAlarmCancelsAndReenqueuesThePeriodicWork() {
        manager.restartUpdateAlarm(context, true);

        assertNotNull(workManager.enqueuedUniquePeriodicWork(PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE));
    }

    @Test
    public void runOnceRefreshesAllFeedsManuallyEvenOnMobile() {
        manager.runOnce(context);

        OneTimeWorkRequest request = manualRequest();
        assertTrue(request.getTags().contains(FeedUpdateManagerImpl.WORK_TAG_FEED_UPDATE));
        assertTrue(request.getWorkSpec().input.getBoolean(FeedUpdateManagerImpl.EXTRA_MANUAL, false));
        assertTrue(request.getWorkSpec().input.getBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, false));
        assertEquals(-1, request.getWorkSpec().input.getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
        assertEquals(NetworkType.CONNECTED, networkOf(request.getWorkSpec().constraints));
        assertTrue(request.getWorkSpec().expedited);
    }

    @Test
    public void runOnceForRemoteFeedCarriesFeedIdAndRequiresNetwork() {
        Feed feed = remoteFeed();

        manager.runOnce(context, feed);

        OneTimeWorkRequest request = manualRequest();
        assertEquals(feed.getId(), request.getWorkSpec().input.getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
        assertFalse(request.getWorkSpec().input.getBoolean(FeedUpdateManagerImpl.EXTRA_NEXT_PAGE, true));
        assertEquals(NetworkType.CONNECTED, networkOf(request.getWorkSpec().constraints));
    }

    @Test
    public void runOnceForLocalFeedNeedsNoNetwork() {
        Feed feed = localFeed();

        manager.runOnce(context, feed);

        assertEquals(NetworkType.NOT_REQUIRED, networkOf(manualRequest().getWorkSpec().constraints));
    }

    @Test
    public void runOnceCanRequestTheNextPageOfAFeed() {
        manager.runOnce(context, remoteFeed(), true);

        assertTrue(manualRequest().getWorkSpec().input.getBoolean(FeedUpdateManagerImpl.EXTRA_NEXT_PAGE, false));
    }

    @Test
    public void askingAgainWithinCooldownOnlyShowsAMessage() {
        Feed feed = remoteFeed();
        manager.runOnce(context, feed);
        Mockito.clearInvocations(workManager.manager());

        manager.runOnceOrAsk(context, feed);

        assertEquals(1, messages.size());
        assertEquals(context.getString(R.string.please_wait_before_refreshing), messages.get(0).message);
        assertFalse(EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent.class).isFeedUpdateRunning);
        Mockito.verifyNoInteractions(workManager.manager());
    }

    @Test
    public void cooldownDoesNotApplyToADifferentFeed() {
        manager.runOnce(context, remoteFeed());
        Mockito.clearInvocations(workManager.manager());
        Feed other = localFeed();

        manager.runOnceOrAsk(context, other);

        assertTrue(messages.isEmpty());
        assertEquals(other.getId(),
                manualRequest().getWorkSpec().input.getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
    }

    @Test
    public void localFeedIsRefreshedWithoutCheckingTheNetwork() {
        manager.runOnceOrAsk(context, localFeed());

        assertTrue(messages.isEmpty());
        assertNotNull(manualRequest());
    }

    @Test
    public void missingConnectionShowsAMessageInsteadOfRefreshing() {
        network.when(NetworkUtils::networkAvailable).thenReturn(false);

        manager.runOnceOrAsk(context, remoteFeed());

        assertEquals(1, messages.size());
        assertEquals(context.getString(R.string.download_error_no_connection), messages.get(0).message);
        assertFalse(EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent.class).isFeedUpdateRunning);
        Mockito.verifyNoInteractions(workManager.manager());
    }

    @Test
    public void allowedConnectionRefreshesImmediately() {
        Feed feed = remoteFeed();
        network.when(NetworkUtils::networkAvailable).thenReturn(true);
        network.when(NetworkUtils::isFeedRefreshAllowed).thenReturn(true);

        manager.runOnceOrAsk(context, feed);

        assertTrue(messages.isEmpty());
        assertEquals(feed.getId(), manualRequest().getWorkSpec().input.getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
    }

    private AlertDialog showMobileRefreshDialog() {
        network.when(NetworkUtils::networkAvailable).thenReturn(true);
        network.when(NetworkUtils::isFeedRefreshAllowed).thenReturn(false);
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        manager.runOnceOrAsk(activity, remoteFeed());
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    @Test
    public void restrictedConnectionAsksBeforeRefreshingWithoutEnqueueingWork() {
        AlertDialog dialog = showMobileRefreshDialog();

        assertTrue(dialog.isShowing());
        Mockito.verifyNoInteractions(workManager.manager());
    }

    @Test
    public void confirmingOnceRefreshesWithoutChangingThePreference() {
        AlertDialog dialog = showMobileRefreshDialog();

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();

        assertNotNull(manualRequest());
        preferences.verify(() -> UserPreferences.setAllowMobileFeedRefresh(true), Mockito.never());
    }

    @Test
    public void confirmingAlwaysStoresThePreferenceAndRefreshes() {
        AlertDialog dialog = showMobileRefreshDialog();

        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
        ShadowLooper.idleMainLooper();

        preferences.verify(() -> UserPreferences.setAllowMobileFeedRefresh(true));
        assertNotNull(manualRequest());
    }

    @Test
    public void decliningStopsTheRunningIndicatorWithoutRefreshing() {
        AlertDialog dialog = showMobileRefreshDialog();

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();

        assertFalse(EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent.class).isFeedUpdateRunning);
        Mockito.verifyNoInteractions(workManager.manager());
    }

    @Test
    public void cancellingTheDialogStopsTheRunningIndicator() {
        AlertDialog dialog = showMobileRefreshDialog();

        dialog.cancel();
        ShadowLooper.idleMainLooper();

        assertFalse(EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent.class).isFeedUpdateRunning);
    }
}
