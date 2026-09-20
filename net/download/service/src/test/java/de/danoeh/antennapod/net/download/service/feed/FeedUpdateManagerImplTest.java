package de.danoeh.antennapod.net.download.service.feed;

import android.content.Context;
import android.net.ConnectivityManager;
import android.view.ContextThemeWrapper;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.download.service.DownloadIntegrationTestBase;
import de.danoeh.antennapod.net.download.service.R;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Category(IntegrationTest.class)
public class FeedUpdateManagerImplTest extends DownloadIntegrationTestBase {
    private static final String WORK_ID_PERIODIC = "de.danoeh.antennapod.core.service.FeedUpdateWorker";
    private static final String WORK_ID_MANUAL = "feedUpdateManual";
    private static final long FEED_ID = 1_000;

    private final List<MessageEvent> messages = new ArrayList<>();
    private FeedUpdateManagerImpl manager;

    @Subscribe
    public void onMessage(MessageEvent event) {
        messages.add(event);
    }

    @Before
    public void createManager() {
        manager = new FeedUpdateManagerImpl();
        manager.runOnce(context);
        Mockito.clearInvocations(workManager);
        EventBus.getDefault().register(this);
    }

    @After
    public void cleanUpEvents() {
        EventBus.getDefault().unregister(this);
        EventBus.getDefault().removeAllStickyEvents();
    }

    private static Feed feedWithId(long id) {
        Feed feed = new Feed("https://example.com/feed.xml", null, "Feed");
        feed.setId(id);
        return feed;
    }

    private OneTimeWorkRequest captureManualRequest() {
        ArgumentCaptor<OneTimeWorkRequest> captor = ArgumentCaptor.forClass(OneTimeWorkRequest.class);
        verify(workManager).enqueueUniqueWork(eq(WORK_ID_MANUAL), eq(ExistingWorkPolicy.REPLACE), captor.capture());
        return captor.getValue();
    }

    @Test
    public void periodicUpdateIsScheduledHourlyWithUnmeteredConstraintByDefault() {
        manager.restartUpdateAlarm(context, false);

        ArgumentCaptor<PeriodicWorkRequest> captor = ArgumentCaptor.forClass(PeriodicWorkRequest.class);
        verify(workManager).enqueueUniquePeriodicWork(eq(WORK_ID_PERIODIC), eq(ExistingPeriodicWorkPolicy.KEEP),
                captor.capture());
        assertEquals(FeedUpdateWorker.class.getName(), captor.getValue().getWorkSpec().workerClassName);
        assertEquals(TimeUnit.HOURS.toMillis(1), captor.getValue().getWorkSpec().intervalDuration);
        assertEquals(NetworkType.UNMETERED, captor.getValue().getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test
    public void periodicUpdateReplacesExistingScheduleWhenRequested() {
        manager.restartUpdateAlarm(context, true);

        verify(workManager).enqueueUniquePeriodicWork(eq(WORK_ID_PERIODIC),
                eq(ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE), any(PeriodicWorkRequest.class));
    }

    @Test
    public void periodicUpdateAcceptsMobileNetworkWhenMobileRefreshIsAllowed() {
        UserPreferences.setAllowMobileFeedRefresh(true);

        manager.restartUpdateAlarm(context, false);

        ArgumentCaptor<PeriodicWorkRequest> captor = ArgumentCaptor.forClass(PeriodicWorkRequest.class);
        verify(workManager).enqueueUniquePeriodicWork(eq(WORK_ID_PERIODIC), any(ExistingPeriodicWorkPolicy.class),
                captor.capture());
        assertEquals(NetworkType.CONNECTED, captor.getValue().getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test
    public void periodicUpdateIsCancelledWhenAutomaticUpdatesAreDisabled() {
        UserPreferences.setUpdateInterval(0);

        manager.restartUpdateAlarm(context, false);

        verify(workManager).cancelUniqueWork(WORK_ID_PERIODIC);
        verify(workManager, never()).enqueueUniquePeriodicWork(any(String.class),
                any(ExistingPeriodicWorkPolicy.class), any(PeriodicWorkRequest.class));
    }

    @Test
    public void runOnceForAllFeedsEnqueuesManualRequestWithoutFeedId() {
        manager.runOnce(context);

        OneTimeWorkRequest request = captureManualRequest();
        assertEquals(FeedUpdateWorker.class.getName(), request.getWorkSpec().workerClassName);
        assertTrue(request.getWorkSpec().input.getBoolean(FeedUpdateManagerImpl.EXTRA_MANUAL, false));
        assertTrue(request.getWorkSpec().input.getBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, false));
        assertEquals(-1, request.getWorkSpec().input.getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
        assertTrue(request.getTags().contains(FeedUpdateManagerImpl.WORK_TAG_FEED_UPDATE));
        assertEquals(NetworkType.CONNECTED, request.getWorkSpec().constraints.getRequiredNetworkType());
        assertTrue(request.getWorkSpec().expedited);
    }

    @Test
    public void runOnceForRemoteFeedCarriesFeedIdAndNextPageFlag() {
        Feed feed = feedWithId(FEED_ID);

        manager.runOnce(context, feed, true);

        OneTimeWorkRequest request = captureManualRequest();
        assertEquals(feed.getId(), request.getWorkSpec().input.getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
        assertTrue(request.getWorkSpec().input.getBoolean(FeedUpdateManagerImpl.EXTRA_NEXT_PAGE, false));
        assertEquals(NetworkType.CONNECTED, request.getWorkSpec().constraints.getRequiredNetworkType());
    }

    @Test
    public void runOnceForLocalFeedNeedsNoNetwork() {
        Feed feed = new Feed(Feed.PREFIX_LOCAL_FOLDER + "folder", null, "Local");
        feed.setId(FEED_ID);

        manager.runOnce(context, feed);

        OneTimeWorkRequest request = captureManualRequest();
        assertEquals(NetworkType.NOT_REQUIRED, request.getWorkSpec().constraints.getRequiredNetworkType());
        assertFalse(request.getWorkSpec().input.getBoolean(FeedUpdateManagerImpl.EXTRA_NEXT_PAGE, false));
        assertEquals(feed.getId(), request.getWorkSpec().input.getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
    }

    @Test
    public void runOnceOrAskStartsRefreshWhenNetworkIsUnrestricted() {
        setNetwork(ConnectivityManager.TYPE_WIFI);
        Feed feed = feedWithId(FEED_ID);

        manager.runOnceOrAsk(context, feed);

        assertEquals(feed.getId(), captureManualRequest().getWorkSpec().input
                .getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void runOnceOrAskReportsMissingConnection() {
        setNoNetwork();

        manager.runOnceOrAsk(context, feedWithId(FEED_ID));

        assertEquals(1, messages.size());
        assertEquals(context.getString(R.string.download_error_no_connection), messages.get(0).message);
        FeedUpdateRunningEvent sticky = EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent.class);
        assertNotNull(sticky);
        assertFalse(sticky.isFeedUpdateRunning);
        verify(workManager, never()).enqueueUniqueWork(any(String.class), any(ExistingWorkPolicy.class),
                any(OneTimeWorkRequest.class));
    }

    @Test
    public void runOnceOrAskRefreshesLocalFeedWithoutNetwork() {
        setNoNetwork();
        Feed feed = new Feed(Feed.PREFIX_LOCAL_FOLDER + "folder", null, "Local");
        feed.setId(FEED_ID);

        manager.runOnceOrAsk(context, feed);

        assertEquals(feed.getId(), captureManualRequest().getWorkSpec().input
                .getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void runOnceOrAskRejectsSecondRequestForSameFeedWithinCooldown() {
        setNetwork(ConnectivityManager.TYPE_WIFI);
        Feed feed = feedWithId(FEED_ID);

        manager.runOnceOrAsk(context, feed);
        manager.runOnceOrAsk(context, feed);

        assertEquals(1, messages.size());
        assertEquals(context.getString(R.string.please_wait_before_refreshing), messages.get(0).message);
        verify(workManager, times(1))
                .enqueueUniqueWork(eq(WORK_ID_MANUAL), eq(ExistingWorkPolicy.REPLACE), any(OneTimeWorkRequest.class));
    }

    @Test
    public void runOnceOrAskAllowsDifferentFeedDuringCooldown() {
        setNetwork(ConnectivityManager.TYPE_WIFI);

        manager.runOnceOrAsk(context, feedWithId(FEED_ID));
        manager.runOnceOrAsk(context, feedWithId(FEED_ID + 1));

        assertTrue(messages.isEmpty());
        verify(workManager, times(2))
                .enqueueUniqueWork(eq(WORK_ID_MANUAL), eq(ExistingWorkPolicy.REPLACE), any(OneTimeWorkRequest.class));
    }

    private Context themedContext() {
        return new ContextThemeWrapper(context, R.style.Theme_Material3_DayNight);
    }

    private AlertDialog showMobileRefreshDialog(Feed feed) {
        setNetwork(ConnectivityManager.TYPE_MOBILE);

        manager.runOnceOrAsk(themedContext(), feed);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    @Test
    public void runOnceOrAskOnMobileNetworkAsksBeforeRefreshing() {
        AlertDialog dialog = showMobileRefreshDialog(feedWithId(FEED_ID));

        TextView title = dialog.findViewById(R.id.alertTitle);
        TextView message = dialog.findViewById(android.R.id.message);
        assertEquals(context.getString(R.string.feed_refresh_title), title.getText().toString());
        assertEquals(context.getString(R.string.confirm_mobile_feed_refresh_dialog_message),
                message.getText().toString());
        verify(workManager, never()).enqueueUniqueWork(any(String.class), any(ExistingWorkPolicy.class),
                any(OneTimeWorkRequest.class));
    }

    @Test
    public void confirmingMobileRefreshOnceStartsRefreshWithoutChangingPreference() {
        Feed feed = feedWithId(FEED_ID);
        AlertDialog dialog = showMobileRefreshDialog(feed);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();

        assertEquals(feed.getId(), captureManualRequest().getWorkSpec().input
                .getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
        assertFalse(UserPreferences.isAllowMobileFeedRefresh());
    }

    @Test
    public void confirmingMobileRefreshAlwaysStoresPreferenceAndStartsRefresh() {
        Feed feed = feedWithId(FEED_ID);
        AlertDialog dialog = showMobileRefreshDialog(feed);

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        ShadowLooper.idleMainLooper();

        assertTrue(UserPreferences.isAllowMobileFeedRefresh());
        assertEquals(feed.getId(), captureManualRequest().getWorkSpec().input
                .getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
    }

    @Test
    public void decliningMobileRefreshReportsRefreshNotRunning() {
        AlertDialog dialog = showMobileRefreshDialog(feedWithId(FEED_ID));

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();

        FeedUpdateRunningEvent sticky = EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent.class);
        assertNotNull(sticky);
        assertFalse(sticky.isFeedUpdateRunning);
        verify(workManager, never()).enqueueUniqueWork(any(String.class), any(ExistingWorkPolicy.class),
                any(OneTimeWorkRequest.class));
    }

    @Test
    public void dismissingMobileRefreshDialogReportsRefreshNotRunning() {
        AlertDialog dialog = showMobileRefreshDialog(feedWithId(FEED_ID));

        dialog.cancel();
        ShadowLooper.idleMainLooper();

        FeedUpdateRunningEvent sticky = EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent.class);
        assertNotNull(sticky);
        assertFalse(sticky.isFeedUpdateRunning);
    }

    @Test
    public void mobileRefreshIsAllowedWithoutAskingOnceThePreferenceIsSet() {
        UserPreferences.setAllowMobileFeedRefresh(true);
        setNetwork(ConnectivityManager.TYPE_MOBILE);
        Feed feed = feedWithId(FEED_ID);

        manager.runOnceOrAsk(themedContext(), feed);

        assertEquals(feed.getId(), captureManualRequest().getWorkSpec().input
                .getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1));
    }
}
