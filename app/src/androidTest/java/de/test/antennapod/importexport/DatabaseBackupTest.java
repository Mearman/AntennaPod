package de.test.antennapod.importexport;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.importexport.AutomaticDatabaseExportWorker;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.ui.UITestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasType;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickPreference;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class DatabaseBackupTest {
    private static final String AUTOMATIC_BACKUP_WORK = "de.danoeh.antennapod.AutomaticDbExport";

    private UITestUtils uiTestUtils;

    @Rule
    public IntentsTestRule<PreferenceActivity> activityTestRule =
            new IntentsTestRule<>(PreferenceActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearDatabase();
        EspressoTestUtils.clearPreferences();
        uiTestUtils = new UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext());
        uiTestUtils.setup();
        activityTestRule.launchActivity(new Intent());
    }

    @After
    public void tearDown() throws Exception {
        activityTestRule.finishActivity();
        UserPreferences.setAutomaticExportFolder(null);
        uiTestUtils.tearDown();
        PodDBAdapter.tearDownTests();
    }

    private File exportFile(String name) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null);
        File file = new File(dir, name);
        if (file.exists()) {
            assertTrue(file.delete());
        }
        return file;
    }

    private void addFeedsToDatabase() throws IOException {
        uiTestUtils.addHostedFeedData();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(uiTestUtils.hostedFeeds.toArray(new Feed[0]));
        adapter.close();
    }

    private void stubCreateDocument(File target) {
        Intent data = new Intent();
        data.setData(Uri.fromFile(target));
        intending(allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT),
                hasType("application/x-sqlite3")))
                .respondWith(new Instrumentation.ActivityResult(Activity.RESULT_OK, data));
    }

    private void stubOpenDocument(File source) {
        Intent data = new Intent();
        data.setData(Uri.fromFile(source));
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
                .respondWith(new Instrumentation.ActivityResult(Activity.RESULT_OK, data));
    }

    private void openImportExportScreen() {
        clickPreference(R.string.import_export_pref);
    }

    private void exportDatabase(File target) {
        stubCreateDocument(target);
        openImportExportScreen();
        clickPreference(R.string.database_export_label);
        waitForViewGlobally(withText(R.string.export_success_title), 20000);
    }

    private int queryFeedCount(File databaseFile) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(databaseFile.getAbsolutePath(),
                null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM Feeds", null);
        assertTrue(cursor.moveToFirst());
        int count = cursor.getInt(0);
        cursor.close();
        db.close();
        return count;
    }

    @Test
    public void testDatabaseExportWritesValidDatabase() throws Exception {
        addFeedsToDatabase();
        File target = exportFile("AntennaPodBackup-test.db");

        exportDatabase(target);

        FileInputStream in = new FileInputStream(target);
        byte[] magic = new byte[16];
        assertEquals(16, in.read(magic));
        in.close();
        assertEquals("SQLite format 3",
                new String(magic, 0, 15, StandardCharsets.US_ASCII));
        assertEquals(uiTestUtils.hostedFeeds.size(), queryFeedCount(target));
    }

    @Test
    public void testDatabaseImportRestoresExportedDatabase() throws Exception {
        addFeedsToDatabase();
        File backup = exportFile("AntennaPodBackup-restore.db");
        exportDatabase(backup);
        pressBack();

        EspressoTestUtils.clearDatabase();
        stubOpenDocument(backup);
        openImportExportScreen();
        clickPreference(R.string.database_import_label);
        onView(withText(R.string.confirm_label)).perform(click());

        waitForViewGlobally(withText(R.string.successful_import_label), 20000);
        File imported = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getDatabasePath(PodDBAdapter.DATABASE_NAME);
        assertEquals(uiTestUtils.hostedFeeds.size(), queryFeedCount(imported));
    }

    @Test
    public void testDatabaseImportRejectsDatabaseFromNewerAppVersion() throws Exception {
        addFeedsToDatabase();
        File backup = exportFile("AntennaPodBackup-future.db");
        exportDatabase(backup);
        SQLiteDatabase db = SQLiteDatabase.openDatabase(backup.getAbsolutePath(),
                null, SQLiteDatabase.OPEN_READWRITE);
        db.setVersion(PodDBAdapter.VERSION + 1);
        db.close();
        pressBack();

        stubOpenDocument(backup);
        openImportExportScreen();
        clickPreference(R.string.database_import_label);
        onView(withText(R.string.confirm_label)).perform(click());

        waitForViewGlobally(withText(R.string.import_error_label), 20000);
        String tooNew = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getString(R.string.import_no_downgrade);
        waitForViewGlobally(withText(tooNew), 5000);
    }

    @Test
    public void testDatabaseExportRejectsDatabaseFromOlderAppVersion() throws Exception {
        addFeedsToDatabase();
        File backup = exportFile("AntennaPodBackup-old.db");
        exportDatabase(backup);
        SQLiteDatabase db = SQLiteDatabase.openDatabase(backup.getAbsolutePath(),
                null, SQLiteDatabase.OPEN_READWRITE);
        db.setVersion(PodDBAdapter.VERSION - 1);
        db.close();

        File currentDatabase = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getDatabasePath(PodDBAdapter.DATABASE_NAME);
        FileUtils.copyFile(backup, currentDatabase);
        File target = exportFile("AntennaPodBackup-fromold.db");
        stubCreateDocument(target);
        clickPreference(R.string.database_export_label);
        waitForViewGlobally(withText(R.string.export_error_label), 20000);
    }

    @Test
    public void testDatabaseImportShowsErrorForCorruptBackup() throws Exception {
        File corrupt = exportFile("AntennaPodBackup-corrupt.db");
        FileOutputStream out = new FileOutputStream(corrupt);
        out.write("this is definitely not a sqlite database".getBytes());
        out.close();
        stubOpenDocument(corrupt);
        openImportExportScreen();
        clickPreference(R.string.database_import_label);
        onView(withText(R.string.confirm_label)).perform(click());
        waitForViewGlobally(withText(R.string.import_error_label), 20000);
    }

    @Test
    public void testAutomaticBackupSwitchStaysOffWhenPickerCancelled() {
        Intent cancelled = new Intent();
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT_TREE))
                .respondWith(new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, cancelled));

        openImportExportScreen();
        clickPreference(R.string.automatic_database_export_label);
        assertNull(UserPreferences.getAutomaticExportFolder());
    }

    @Test
    public void testTurningAutomaticBackupOffClearsFolderAndCancelsWork() throws Exception {
        UserPreferences.setAutomaticExportFolder("content://mock/tree");
        AutomaticDatabaseExportWorker.enqueueIfNeeded(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), true);
        WorkManager workManager = WorkManager.getInstance(
                InstrumentationRegistry.getInstrumentation().getTargetContext());

        openImportExportScreen();
        clickPreference(R.string.automatic_database_export_label);

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> UserPreferences.getAutomaticExportFolder() == null);
        List<WorkInfo> infos = workManager.getWorkInfosForUniqueWork(AUTOMATIC_BACKUP_WORK).get();
        assertTrue(!infos.isEmpty());
        for (WorkInfo info : infos) {
            assertTrue(info.getState() == WorkInfo.State.CANCELLED || info.getState().isFinished());
        }
    }

    @Test
    public void testAutomaticBackupWorkerSucceedsWithoutBackupFolder() throws Exception {
        WorkManager workManager = WorkManager.getInstance(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        workManager.enqueueUniqueWork("AutomaticBackupOnce",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.from(AutomaticDatabaseExportWorker.class));
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            List<WorkInfo> infos = workManager.getWorkInfosForUniqueWork("AutomaticBackupOnce").get();
            return !infos.isEmpty() && infos.get(0).getState() == WorkInfo.State.SUCCEEDED;
        });
    }
}
