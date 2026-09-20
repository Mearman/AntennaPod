package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import de.danoeh.antennapod.model.playback.TimerValue;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowSensorManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class ShakeListenerTest {
    private Context context;
    private ShadowSensorManager sensorManager;
    private RecordingSleepTimer sleepTimer;
    private ShakeListener listener;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        sensorManager = shadowOf((SensorManager) context.getSystemService(Context.SENSOR_SERVICE));
        sleepTimer = new RecordingSleepTimer();
        listener = new ShakeListener(context, sleepTimer);
    }

    @After
    public void tearDown() {
        listener.pause();
        PlaybackTestDatabase.tearDown();
    }

    private void deliver(float x, float y, float z) {
        SensorEvent event = ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_ACCELEROMETER);
        event.values[0] = x;
        event.values[1] = y;
        event.values[2] = z;
        sensorManager.sendSensorEventToListeners(event);
    }

    @Test
    public void aShakeListenerRegistersItselfWithTheAccelerometerWhileItIsRunning() {
        assertTrue(sensorManager.hasListener(listener));

        listener.pause();

        assertFalse(sensorManager.hasListener(listener));
    }

    @Test
    public void pausingTwiceIsHarmless() {
        listener.pause();
        listener.pause();

        assertFalse(sensorManager.hasListener(listener));
    }

    @Test
    public void aDeviceLyingStillDoesNotResetTheSleepTimer() {
        deliver(0, SensorManager.GRAVITY_EARTH, 0);

        assertEquals(0, sleepTimer.resets);
    }

    @Test
    public void aSharpShakeResetsTheSleepTimer() {
        deliver(3 * SensorManager.GRAVITY_EARTH, 0, 0);

        assertEquals(1, sleepTimer.resets);
    }

    @Test
    public void aShakeThatIsBarelyBelowTheThresholdIsIgnored() {
        deliver(2.2f * SensorManager.GRAVITY_EARTH, 0, 0);

        assertEquals(0, sleepTimer.resets);
    }

    @Test
    public void aChangeInSensorAccuracyDoesNotTouchTheSleepTimer() {
        listener.onAccuracyChanged(null, SensorManager.SENSOR_STATUS_ACCURACY_LOW);

        assertEquals(0, sleepTimer.resets);
    }

    private static class RecordingSleepTimer implements SleepTimer {
        private int resets;

        @Override
        public TimerValue getTimeLeft() {
            return new TimerValue(0, 0);
        }

        @Override
        public void start(long initialWaitingTime) {
        }

        @Override
        public void stop() {
        }

        @Override
        public void updateRemainingTime(long waitingTimeOrEpisodes) {
        }

        @Override
        public void reset() {
            resets++;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public boolean isEndingThisEpisode(long episodeRemainingMillis) {
            return false;
        }

        @Override
        public boolean shouldContinueToNextEpisode() {
            return true;
        }

        @Override
        public void episodeFinishedPlayback() {
        }
    }
}
