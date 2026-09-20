package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.ui.widget.WidgetUpdater;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class PlaybackServiceTaskManagerTest {
    private Context context;
    private PlaybackServiceTaskManager.PSTMCallback callback;
    private PlaybackServiceTaskManager taskManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        callback = mock(PlaybackServiceTaskManager.PSTMCallback.class);
        when(callback.requestWidgetState()).thenReturn(mock(WidgetUpdater.WidgetState.class));
        taskManager = new PlaybackServiceTaskManager(context, callback);
    }

    @After
    public void tearDown() {
        taskManager.shutdown();
    }

    @Test
    public void noBackgroundTaskRunsBeforeOneIsStarted() {
        assertFalse(taskManager.isPositionSaverActive());
        assertFalse(taskManager.isWidgetUpdaterActive());
    }

    @Test
    public void thePositionSaverCanBeStartedAndCancelledAgain() {
        taskManager.startPositionSaver();
        assertTrue(taskManager.isPositionSaverActive());

        taskManager.cancelPositionSaver();
        assertFalse(taskManager.isPositionSaverActive());
    }

    @Test
    public void cancellingAPositionSaverThatNeverRanIsHarmless() {
        taskManager.cancelPositionSaver();

        assertFalse(taskManager.isPositionSaverActive());
    }

    @Test
    public void startingThePositionSaverTwiceKeepsTheFirstScheduleRunning() {
        taskManager.startPositionSaver();
        taskManager.startPositionSaver();
        assertTrue(taskManager.isPositionSaverActive());

        taskManager.cancelPositionSaver();
        assertFalse(taskManager.isPositionSaverActive());
    }

    @Test
    public void theWidgetUpdaterCanBeStartedAndCancelledAgain() {
        taskManager.startWidgetUpdater();
        assertTrue(taskManager.isWidgetUpdaterActive());

        taskManager.cancelWidgetUpdater();
        assertFalse(taskManager.isWidgetUpdaterActive());
    }

    @Test
    public void cancellingAllTasksStopsBothSchedules() {
        taskManager.startPositionSaver();
        taskManager.startWidgetUpdater();

        taskManager.cancelAllTasks();

        assertFalse(taskManager.isPositionSaverActive());
        assertFalse(taskManager.isWidgetUpdaterActive());
    }

    @Test
    public void aWidgetUpdateAsksTheServiceForTheStateToDisplay() {
        taskManager.requestWidgetUpdate();

        verify(callback).requestWidgetState();
    }

    @Test
    public void noScheduleCanBeStartedAfterTheManagerWasShutDown() {
        taskManager.shutdown();

        taskManager.startWidgetUpdater();

        assertFalse(taskManager.isWidgetUpdaterActive());
    }

    @Test
    public void mediaThatAlreadyCarriesItsChaptersIsNotLoadedAgain() {
        Playable media = mock(Playable.class);
        when(media.getChapters()).thenReturn(Collections.emptyList());

        taskManager.startChapterLoader(media);

        verify(callback, never()).onChapterLoaded(media);
    }

    @Test
    public void everyWidgetUpdateRequestReachesTheServiceAnew() {
        taskManager.requestWidgetUpdate();
        taskManager.requestWidgetUpdate();

        verify(callback, times(2)).requestWidgetState();
    }
}
