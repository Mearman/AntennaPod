package de.danoeh.antennapod.storage.importexport;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.TestWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class AutomaticDatabaseExportPipelineTest extends ImportExportPipelineTestBase {
    private static final String WORK_ID = "de.danoeh.antennapod.AutomaticDbExport";
    private static final String UNREACHABLE_FOLDER = "content://de.danoeh.antennapod.test.missing/tree/backups";

    public static class MessageCollector {
        private final List<MessageEvent> events = new ArrayList<>();

        @Subscribe
        public void onMessage(MessageEvent event) {
            events.add(event);
        }
    }

    private WorkManager workManager;

    @Before
    public void setUpWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context);
        workManager = WorkManager.getInstance(context);
        UserPreferences.setAutomaticExportFolder(null);
    }

    private List<WorkInfo> scheduledWork() throws Exception {
        return workManager.getWorkInfosForUniqueWork(WORK_ID).get();
    }

    private ListenableWorker.Result runWorker() {
        AutomaticDatabaseExportWorker worker = TestWorkerBuilder
                .from(context, AutomaticDatabaseExportWorker.class, Executors.newSingleThreadExecutor()).build();
        return worker.doWork();
    }

    @Test
    public void exportIsScheduledOnlyWhileABackupFolderIsConfigured() throws Exception {
        UserPreferences.setAutomaticExportFolder(UNREACHABLE_FOLDER);
        AutomaticDatabaseExportWorker.enqueueIfNeeded(context, false);

        List<WorkInfo> scheduled = scheduledWork();
        assertEquals(1, scheduled.size());
        assertEquals(WorkInfo.State.ENQUEUED, scheduled.get(0).getState());

        UserPreferences.setAutomaticExportFolder(null);
        AutomaticDatabaseExportWorker.enqueueIfNeeded(context, false);

        assertEquals(WorkInfo.State.CANCELLED, scheduledWork().get(0).getState());
    }

    @Test
    public void enqueuingAgainKeepsTheScheduledWorkUnlessReplacementIsRequested() throws Exception {
        UserPreferences.setAutomaticExportFolder(UNREACHABLE_FOLDER);
        AutomaticDatabaseExportWorker.enqueueIfNeeded(context, false);
        UUID original = scheduledWork().get(0).getId();

        AutomaticDatabaseExportWorker.enqueueIfNeeded(context, false);
        assertEquals(original, scheduledWork().get(0).getId());

        AutomaticDatabaseExportWorker.enqueueIfNeeded(context, true);
        assertNotEquals(original, scheduledWork().get(0).getId());
    }

    @Test
    public void workerDoesNothingWithoutABackupFolder() {
        assertEquals(ListenableWorker.Result.success(), runWorker());
    }

    @Test
    public void unreachableBackupFolderFailsTheWorkAndShowsAMessageWhenTheAppIsOpen() {
        UserPreferences.setAutomaticExportFolder(UNREACHABLE_FOLDER);
        MessageCollector collector = new MessageCollector();
        EventBus.getDefault().register(collector);
        try {
            assertEquals(ListenableWorker.Result.failure(), runWorker());
        } finally {
            EventBus.getDefault().unregister(collector);
        }

        assertEquals(1, collector.events.size());
        assertTrue(collector.events.get(0).message
                .startsWith(context.getString(R.string.automatic_database_export_error)));
        assertTrue(collector.events.get(0).message.endsWith("Unable to open export folder"));
    }

    @Test
    public void unreachableBackupFolderPostsANotificationWhenNobodyIsListeningAndTheUserAllowedThem() {
        shadowOf((Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        UserPreferences.setAutomaticExportFolder(UNREACHABLE_FOLDER);

        assertEquals(ListenableWorker.Result.failure(), runWorker());

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = shadowOf(manager).getNotification(R.id.notification_id_backup_error);
        assertEquals(context.getString(R.string.automatic_database_export_error),
                notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Unable to open export folder", notification.extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void unreachableBackupFolderShowsAToastWhenNotificationsAreNotAllowed() {
        UserPreferences.setAutomaticExportFolder(UNREACHABLE_FOLDER);

        assertEquals(ListenableWorker.Result.failure(), runWorker());

        assertTrue(ShadowToast.getTextOfLatestToast()
                .startsWith(context.getString(R.string.automatic_database_export_error)));
    }
}
