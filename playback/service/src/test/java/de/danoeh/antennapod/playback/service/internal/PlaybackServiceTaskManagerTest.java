package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.base.PlayerStatus;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import de.danoeh.antennapod.ui.widget.WidgetUpdater;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceTaskManagerTest {
    private Context context;
    private RecordingCallback callback;
    private PlaybackServiceTaskManager taskManager;
    private FeedMedia media;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        media = PlaybackTestDatabase.storedItems(
                PlaybackTestDatabase.storeFeed(context, "http://example.com/feed", "Main", 1).getId())
                .get(0).getMedia();
        callback = new RecordingCallback(media);
        taskManager = new PlaybackServiceTaskManager(context, callback);
    }

    @After
    public void tearDown() {
        taskManager.shutdown();
        PlaybackTestDatabase.tearDown();
    }

    @Test
    public void noBackgroundTaskIsRunningBeforeAnyIsStarted() {
        assertFalse(taskManager.isPositionSaverActive());
        assertFalse(taskManager.isWidgetUpdaterActive());
    }

    @Test
    public void thePositionSaverRunsUntilItIsCancelled() {
        taskManager.startPositionSaver();
        assertTrue(taskManager.isPositionSaverActive());

        taskManager.cancelPositionSaver();

        assertFalse(taskManager.isPositionSaverActive());
    }

    @Test
    public void startingThePositionSaverTwiceKeepsTheOneAlreadyRunning() {
        taskManager.startPositionSaver();
        taskManager.startPositionSaver();

        taskManager.cancelPositionSaver();

        assertFalse(taskManager.isPositionSaverActive());
    }

    @Test
    public void cancellingAPositionSaverThatIsNotRunningIsHarmless() {
        taskManager.cancelPositionSaver();

        assertFalse(taskManager.isPositionSaverActive());
    }

    @Test
    public void theWidgetUpdaterRunsUntilItIsCancelled() {
        taskManager.startWidgetUpdater();
        assertTrue(taskManager.isWidgetUpdaterActive());

        taskManager.cancelWidgetUpdater();

        assertFalse(taskManager.isWidgetUpdaterActive());
    }

    @Test
    public void aWidgetUpdateAsksTheServiceForTheStateToDisplay() {
        taskManager.requestWidgetUpdate();

        assertEquals(1, callback.widgetStateRequests);
    }

    @Test
    public void cancellingAllTasksLeavesNothingRunning() {
        taskManager.startPositionSaver();
        taskManager.startWidgetUpdater();

        taskManager.cancelAllTasks();

        assertFalse(taskManager.isPositionSaverActive());
        assertFalse(taskManager.isWidgetUpdaterActive());
    }

    @Test
    public void aShutDownTaskManagerRefusesToStartTheWidgetUpdaterAgain() {
        taskManager.shutdown();

        taskManager.startWidgetUpdater();

        assertFalse(taskManager.isWidgetUpdaterActive());
    }

    @Test
    public void aShutDownTaskManagerStillAnswersAWidgetUpdateRequestWithoutScheduling() {
        taskManager.shutdown();

        taskManager.requestWidgetUpdate();

        assertEquals(1, callback.widgetStateRequests);
    }

    @Test
    public void loadingChaptersForAnEpisodeThatAlreadyHasThemDoesNothing() {
        media.setChapters(new ArrayList<>());

        taskManager.startChapterLoader(media);

        assertTrue(callback.chapterLoads.isEmpty());
    }

    private static class RecordingCallback implements PlaybackServiceTaskManager.PSTMCallback {
        private final Playable playable;
        private final List<Playable> chapterLoads = new ArrayList<>();
        private int positionSaverTicks;
        private int widgetStateRequests;

        RecordingCallback(Playable playable) {
            this.playable = playable;
        }

        @Override
        public void positionSaverTick() {
            positionSaverTicks++;
        }

        @Override
        public WidgetUpdater.WidgetState requestWidgetState() {
            widgetStateRequests++;
            return new WidgetUpdater.WidgetState(playable,
                    PlayerStatus.PLAYING, 0, 1000, 1.0f);
        }

        @Override
        public void onChapterLoaded(Playable media) {
            chapterLoads.add(media);
        }
    }
}
