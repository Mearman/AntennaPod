package de.danoeh.antennapod.storage.importexport;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AutomaticDatabaseExportWorkerTest {
    private static final String WORK_ID = "de.danoeh.antennapod.AutomaticDbExport";
    private static final String BACKUP_NAME_PATTERN = "AntennaPodBackup-\\d{4}-\\d{2}-\\d{2}\\.db";
    private static final int BACKUPS_TO_KEEP = 5;

    public static class MessageCollector {
        private final List<MessageEvent> events = new ArrayList<>();

        @Subscribe
        public void onMessage(MessageEvent event) {
            events.add(event);
        }
    }

    private Context context;
    private WorkManager workManager;
    private MockedStatic<WorkManager> workManagerStatic;
    private MockedStatic<DocumentFile> documentFileStatic;
    private MockedStatic<DatabaseExporter> databaseExporter;
    private AutomaticDatabaseExportWorker worker;
    private DocumentFile folder;
    private DocumentFile exportFile;
    private Uri exportUri;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        UserPreferences.init(context);
        workManager = mock(WorkManager.class);
        workManagerStatic = mockStatic(WorkManager.class);
        workManagerStatic.when(() -> WorkManager.getInstance(any(Context.class))).thenReturn(workManager);
        documentFileStatic = mockStatic(DocumentFile.class);
        databaseExporter = mockStatic(DatabaseExporter.class);
        worker = new AutomaticDatabaseExportWorker(context, mock(WorkerParameters.class));

        exportUri = Uri.parse("content://backup/export.db");
        folder = mock(DocumentFile.class);
        when(folder.exists()).thenReturn(true);
        when(folder.canWrite()).thenReturn(true);
        exportFile = mock(DocumentFile.class);
        when(exportFile.canWrite()).thenReturn(true);
        when(exportFile.getUri()).thenReturn(exportUri);
        when(folder.createFile(eq("application/x-sqlite3"), matches(BACKUP_NAME_PATTERN))).thenReturn(exportFile);
        documentFileStatic.when(() -> DocumentFile.fromTreeUri(any(Context.class), any(Uri.class)))
                .thenReturn(folder);
    }

    @After
    public void tearDown() {
        workManagerStatic.close();
        documentFileStatic.close();
        databaseExporter.close();
    }

    private DocumentFile createFile(String name, long lastModified, boolean deletable) {
        DocumentFile file = mock(DocumentFile.class);
        when(file.getName()).thenReturn(name);
        when(file.lastModified()).thenReturn(lastModified);
        when(file.delete()).thenReturn(deletable);
        return file;
    }

    private void setFolderContent(DocumentFile... files) {
        when(folder.listFiles()).thenReturn(files);
    }

    private void configureExportFolder() {
        UserPreferences.setAutomaticExportFolder("content://tree/backups");
    }

    @Test
    public void disablingAutomaticExportCancelsScheduledWork() {
        UserPreferences.setAutomaticExportFolder(null);

        AutomaticDatabaseExportWorker.enqueueIfNeeded(context, false);

        verify(workManager).cancelUniqueWork(WORK_ID);
        verify(workManager, never()).enqueueUniquePeriodicWork(any(), any(), any());
    }

    @Test
    public void enablingAutomaticExportSchedulesThreeDayPeriodicWorkKeepingExistingSchedule() {
        configureExportFolder();

        AutomaticDatabaseExportWorker.enqueueIfNeeded(context, false);

        ArgumentCaptor<PeriodicWorkRequest> request = ArgumentCaptor.forClass(PeriodicWorkRequest.class);
        verify(workManager).enqueueUniquePeriodicWork(eq(WORK_ID), eq(ExistingPeriodicWorkPolicy.KEEP),
                request.capture());
        assertEquals(AutomaticDatabaseExportWorker.class.getName(), request.getValue().getWorkSpec().workerClassName);
        assertEquals(TimeUnit.DAYS.toMillis(3), request.getValue().getWorkSpec().intervalDuration);
        verify(workManager, never()).cancelUniqueWork(any());
    }

    @Test
    public void replacingScheduleUsesReplacePolicy() {
        configureExportFolder();

        AutomaticDatabaseExportWorker.enqueueIfNeeded(context, true);

        verify(workManager).enqueueUniquePeriodicWork(eq(WORK_ID), eq(ExistingPeriodicWorkPolicy.REPLACE),
                any(PeriodicWorkRequest.class));
    }

    @Test
    public void workSucceedsWithoutExportingWhenNoFolderIsConfigured() {
        UserPreferences.setAutomaticExportFolder(null);

        assertEquals(ListenableWorker.Result.success(), worker.doWork());

        databaseExporter.verifyNoInteractions();
        documentFileStatic.verifyNoInteractions();
    }

    @Test
    public void exportCreatesBackupFileNamedAfterTheCurrentDateAndSucceeds() {
        configureExportFolder();
        setFolderContent();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String dateBefore = dateFormat.format(new Date());

        assertEquals(ListenableWorker.Result.success(), worker.doWork());

        String dateAfter = dateFormat.format(new Date());
        ArgumentCaptor<String> createdName = ArgumentCaptor.forClass(String.class);
        verify(folder).createFile(eq("application/x-sqlite3"), createdName.capture());
        assertTrue(createdName.getValue().equals("AntennaPodBackup-" + dateBefore + ".db")
                || createdName.getValue().equals("AntennaPodBackup-" + dateAfter + ".db"));
        databaseExporter.verify(() -> DatabaseExporter.exportToDocument(exportUri, worker.getApplicationContext()));
    }

    @Test
    public void onlyFiveNewestBackupsAreKeptAndOlderOnesAreDeleted() {
        configureExportFolder();
        List<DocumentFile> backups = new ArrayList<>();
        for (int day = 1; day <= 8; day++) {
            backups.add(createFile("AntennaPodBackup-2026-01-0" + day + ".db", day, true));
        }
        setFolderContent(backups.toArray(new DocumentFile[0]));

        assertEquals(ListenableWorker.Result.success(), worker.doWork());

        for (int day = 1; day <= 3; day++) {
            verify(backups.get(day - 1)).delete();
        }
        for (int day = 4; day <= 8; day++) {
            verify(backups.get(day - 1), never()).delete();
        }
    }

    @Test
    public void backupsAreNotDeletedWhileWithinRetentionLimit() {
        configureExportFolder();
        List<DocumentFile> backups = new ArrayList<>();
        for (int day = 1; day <= BACKUPS_TO_KEEP; day++) {
            backups.add(createFile("AntennaPodBackup-2026-01-0" + day + ".db", day, true));
        }
        setFolderContent(backups.toArray(new DocumentFile[0]));

        assertEquals(ListenableWorker.Result.success(), worker.doWork());

        for (DocumentFile backup : backups) {
            verify(backup, never()).delete();
        }
    }

    @Test
    public void filesNotMatchingBackupNamePatternAreNeverDeleted() {
        configureExportFolder();
        DocumentFile unrelated = createFile("holiday-photos.zip", 0, true);
        DocumentFile renamedBackup = createFile("AntennaPodBackup-2026-01-01.db.bak", 0, true);
        List<DocumentFile> backups = new ArrayList<>();
        for (int day = 1; day <= BACKUPS_TO_KEEP; day++) {
            backups.add(createFile("AntennaPodBackup-2026-02-0" + day + ".db", 100 + day, true));
        }
        List<DocumentFile> content = new ArrayList<>(backups);
        content.add(unrelated);
        content.add(renamedBackup);
        setFolderContent(content.toArray(new DocumentFile[0]));

        assertEquals(ListenableWorker.Result.success(), worker.doWork());

        verify(unrelated, never()).delete();
        verify(renamedBackup, never()).delete();
    }

    @Test
    public void failedDeletionFailsWorkButStillTriesToDeleteAllOldBackups() {
        configureExportFolder();
        DocumentFile undeletable = createFile("AntennaPodBackup-2026-01-01.db", 1, false);
        DocumentFile deletable = createFile("AntennaPodBackup-2026-01-02.db", 2, true);
        List<DocumentFile> content = new ArrayList<>();
        content.add(undeletable);
        content.add(deletable);
        for (int day = 3; day <= 2 + BACKUPS_TO_KEEP; day++) {
            content.add(createFile("AntennaPodBackup-2026-01-0" + day + ".db", day, true));
        }
        setFolderContent(content.toArray(new DocumentFile[0]));

        assertEquals(ListenableWorker.Result.failure(), worker.doWork());

        verify(undeletable).delete();
        verify(deletable).delete();
    }

    @Test
    public void failureIsReportedThroughMessageEventWhenAnEventSubscriberExists() {
        configureExportFolder();
        documentFileStatic.when(() -> DocumentFile.fromTreeUri(any(Context.class), any(Uri.class))).thenReturn(null);
        MessageCollector collector = new MessageCollector();
        EventBus.getDefault().register(collector);
        try {
            assertEquals(ListenableWorker.Result.failure(), worker.doWork());
        } finally {
            EventBus.getDefault().unregister(collector);
        }

        assertEquals(1, collector.events.size());
        String prefix = context.getString(R.string.automatic_database_export_error);
        assertEquals(prefix + " Unable to open export folder", collector.events.get(0).message);
    }

    @Test
    public void unavailableFolderFailsWorkWithoutExporting() {
        configureExportFolder();
        when(folder.exists()).thenReturn(false);

        assertEquals(ListenableWorker.Result.failure(), worker.doWork());

        databaseExporter.verifyNoInteractions();
    }

    @Test
    public void readOnlyFolderFailsWorkWithoutExporting() {
        configureExportFolder();
        when(folder.canWrite()).thenReturn(false);

        assertEquals(ListenableWorker.Result.failure(), worker.doWork());

        databaseExporter.verifyNoInteractions();
    }

    @Test
    public void exportFileThatCannotBeCreatedFailsWork() {
        configureExportFolder();
        when(folder.createFile(any(), any())).thenReturn(null);

        assertEquals(ListenableWorker.Result.failure(), worker.doWork());

        databaseExporter.verifyNoInteractions();
    }

    @Test
    public void exportFileThatIsNotWritableFailsWork() {
        configureExportFolder();
        when(exportFile.canWrite()).thenReturn(false);

        assertEquals(ListenableWorker.Result.failure(), worker.doWork());

        databaseExporter.verifyNoInteractions();
    }

    @Test
    public void databaseExportErrorFailsWorkAndSkipsCleanupOfOldBackups() {
        configureExportFolder();
        databaseExporter.when(() -> DatabaseExporter.exportToDocument(any(Uri.class), any(Context.class)))
                .thenThrow(new IOException("disk full"));

        assertEquals(ListenableWorker.Result.failure(), worker.doWork());

        verify(folder, never()).listFiles();
    }

    @Test
    public void failureWithoutEventSubscriberPostsNotificationWhenPermissionIsGranted() {
        configureExportFolder();
        when(folder.exists()).thenReturn(false);
        shadowOf((Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);

        assertEquals(ListenableWorker.Result.failure(), worker.doWork());

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = shadowOf(notificationManager).getNotification(
                R.id.notification_id_backup_error);
        assertNotNull(notification);
        assertEquals(context.getString(R.string.automatic_database_export_error),
                notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Unable to open export folder", notification.extras.getString(Notification.EXTRA_TEXT));
    }

    @Test
    public void failureWithoutEventSubscriberShowsToastWhenNotificationPermissionIsMissing() {
        configureExportFolder();
        when(folder.exists()).thenReturn(false);
        shadowOf((Application) context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);

        assertEquals(ListenableWorker.Result.failure(), worker.doWork());

        String toastText = ShadowToast.getTextOfLatestToast();
        assertTrue(toastText.endsWith("Unable to open export folder"));
    }
}
