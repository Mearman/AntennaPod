package de.test.antennapod.storage.database;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.TestListenableWorkerBuilder;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.databasemaintenanceservice.DatabaseMaintenanceWorker;
import de.test.antennapod.EspressoTestUtils;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * Runs the periodic database maintenance that removes outdated download log entries.
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseMaintenanceTest {
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);
    private static final String WORK_TAG = DatabaseMaintenanceWorker.class.getName();

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EspressoTestUtils.clearDatabase();
    }

    @After
    public void tearDown() throws Exception {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG);
        WorkManager.getInstance(context).pruneWork().getResult().get();
        EspressoTestUtils.clearDatabase();
    }

    private void log(String title, long completionMillis) throws Exception {
        DBWriter.addDownloadStatus(new DownloadResult(0, title, 1, Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, new Date(completionMillis), null)).get();
    }

    @Test
    public void maintenanceRemovesDownloadLogEntriesOlderThanAWeek() throws Exception {
        long now = System.currentTimeMillis();
        log("Old", now - 8 * DAY_MILLIS);
        log("Older", now - 30 * DAY_MILLIS);
        log("Recent", now - 2 * DAY_MILLIS);
        log("Fresh", now);

        ListenableWorker.Result result = TestListenableWorkerBuilder.from(context, DatabaseMaintenanceWorker.class)
                .build().startWork().get(30, TimeUnit.SECONDS);

        assertEquals(ListenableWorker.Result.success(), result);
        assertEquals(2, DBReader.getDownloadLog().size());
        assertEquals("Fresh", DBReader.getDownloadLog().get(0).getTitle());
        assertEquals("Recent", DBReader.getDownloadLog().get(1).getTitle());
    }

    @Test
    public void maintenanceLeavesAnEmptyLogAlone() throws Exception {
        ListenableWorker.Result result = TestListenableWorkerBuilder.from(context, DatabaseMaintenanceWorker.class)
                .build().startWork().get(30, TimeUnit.SECONDS);

        assertEquals(ListenableWorker.Result.success(), result);
        assertEquals(0, DBReader.getDownloadLog().size());
    }

    @Test
    public void maintenanceIsScheduledOnlyOnce() throws Exception {
        DatabaseMaintenanceWorker.enqueueIfNeeded(context);
        DatabaseMaintenanceWorker.enqueueIfNeeded(context);

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> !scheduled().isEmpty());
        assertEquals(1, scheduled().size());
    }

    private List<WorkInfo> scheduled() throws Exception {
        return WorkManager.getInstance(context).getWorkInfosByTag(WORK_TAG).get();
    }
}
