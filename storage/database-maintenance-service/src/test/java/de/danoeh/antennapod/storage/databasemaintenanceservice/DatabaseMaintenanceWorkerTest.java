package de.danoeh.antennapod.storage.databasemaintenanceservice;

import android.app.Notification;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DatabaseMaintenanceWorkerTest {
    private static final String WORK_NAME = "DatabaseMaintenanceWorker";
    private Context context;

    private static class SucceedingWorker extends Worker {
        SucceedingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            return Result.success();
        }
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration.Builder()
                .setWorkerFactory(new WorkerFactory() {
                    @Override
                    public ListenableWorker createWorker(@NonNull Context appContext,
                                                         @NonNull String workerClassName,
                                                         @NonNull WorkerParameters workerParameters) {
                        if (DatabaseMaintenanceWorker.class.getName().equals(workerClassName)) {
                            return new SucceedingWorker(appContext, workerParameters);
                        }
                        return null;
                    }
                })
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration);
    }

    private List<WorkInfo> scheduledWork() throws Exception {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME).get();
    }

    @Test
    public void enqueueSchedulesOnePeriodicWorkEveryThreeDays() throws Exception {
        DatabaseMaintenanceWorker.enqueueIfNeeded(context);

        List<WorkInfo> work = scheduledWork();
        assertEquals(1, work.size());
        assertEquals(WorkInfo.State.ENQUEUED, work.get(0).getState());
        assertEquals(TimeUnit.DAYS.toMillis(3), work.get(0).getPeriodicityInfo().getRepeatIntervalMillis());
    }

    @Test
    public void enqueuingAgainKeepsTheExistingWork() throws Exception {
        DatabaseMaintenanceWorker.enqueueIfNeeded(context);
        WorkInfo first = scheduledWork().get(0);

        DatabaseMaintenanceWorker.enqueueIfNeeded(context);

        List<WorkInfo> work = scheduledWork();
        assertEquals(1, work.size());
        assertEquals(first.getId(), work.get(0).getId());
    }

    @Test
    public void foregroundInfoShowsOngoingRefreshNotification() throws Exception {
        DatabaseMaintenanceWorker worker = TestWorkerBuilder.from(context, DatabaseMaintenanceWorker.class,
                Executors.newSingleThreadExecutor()).build();

        ForegroundInfo info = worker.getForegroundInfoAsync().get();

        assertEquals(R.id.notification_db_maintenance, info.getNotificationId());
        Notification notification = info.getNotification();
        assertEquals(NotificationUtils.CHANNEL_ID_REFRESHING, notification.getChannelId());
        assertTrue((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertNotEquals(0, notification.icon);
        assertEquals(context.getString(R.string.download_notification_title_feeds),
                notification.extras.getString(Notification.EXTRA_TITLE));
    }
}
