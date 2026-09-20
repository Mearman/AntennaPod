package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class ShakeListenerTest {
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private SleepTimer sleepTimer;

    @Before
    public void setUp() {
        sensorManager = mock(SensorManager.class);
        accelerometer = mock(Sensor.class);
        sleepTimer = mock(SleepTimer.class);
        when(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)).thenReturn(accelerometer);
        when(sensorManager.registerListener(any(), any(Sensor.class), anyInt())).thenReturn(true);
    }

    @Test
    public void theListenerSubscribesToTheAccelerometerAsSoonAsItIsCreated() {
        ShakeListener listener = new ShakeListener(contextWithSensors(sensorManager), sleepTimer);

        verify(sensorManager).registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Test
    public void aDeviceWithoutSensorsCannotDriveTheShakeToReset() {
        Context context = contextWithSensors(null);

        assertThrows(UnsupportedOperationException.class, () -> new ShakeListener(context, sleepTimer));
    }

    @Test
    public void anAccelerometerThatRefusesToRegisterIsReleasedAgain() {
        when(sensorManager.registerListener(any(), any(Sensor.class), anyInt())).thenReturn(false);
        Context context = contextWithSensors(sensorManager);

        assertThrows(UnsupportedOperationException.class, () -> new ShakeListener(context, sleepTimer));
        verify(sensorManager).unregisterListener(any(ShakeListener.class));
    }

    @Test
    public void pausingUnsubscribesFromTheAccelerometer() {
        ShakeListener listener = new ShakeListener(contextWithSensors(sensorManager), sleepTimer);

        listener.pause();

        verify(sensorManager).unregisterListener(listener);
    }

    @Test
    public void pausingTwiceOnlyUnsubscribesOnce() {
        ShakeListener listener = new ShakeListener(contextWithSensors(sensorManager), sleepTimer);

        listener.pause();
        listener.pause();

        verify(sensorManager).unregisterListener(listener);
    }

    @Test
    public void aShakeStrongerThanTheThresholdRestartsTheSleepTimer() {
        ShakeListener listener = new ShakeListener(contextWithSensors(sensorManager), sleepTimer);

        listener.onSensorChanged(sensorEvent(3 * SensorManager.GRAVITY_EARTH, 0, 0));

        verify(sleepTimer).reset();
    }

    @Test
    public void thePullOfGravityAloneDoesNotRestartTheSleepTimer() {
        ShakeListener listener = new ShakeListener(contextWithSensors(sensorManager), sleepTimer);

        listener.onSensorChanged(sensorEvent(0, 0, SensorManager.GRAVITY_EARTH));

        verify(sleepTimer, never()).reset();
    }

    @Test
    public void theShakeThresholdIsMeasuredAcrossAllThreeAxesTogether() {
        ShakeListener listener = new ShakeListener(contextWithSensors(sensorManager), sleepTimer);
        float perAxis = 1.5f * SensorManager.GRAVITY_EARTH;

        listener.onSensorChanged(sensorEvent(perAxis, 0, 0));
        verify(sleepTimer, never()).reset();

        listener.onSensorChanged(sensorEvent(perAxis, perAxis, perAxis));
        verify(sleepTimer).reset();
    }

    @Test
    public void aChangeInSensorAccuracyDoesNotTouchTheSleepTimer() {
        ShakeListener listener = new ShakeListener(contextWithSensors(sensorManager), sleepTimer);

        listener.onAccuracyChanged(accelerometer, SensorManager.SENSOR_STATUS_ACCURACY_LOW);

        verify(sleepTimer, never()).reset();
    }

    private static Context contextWithSensors(SensorManager sensorManager) {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override
            public Object getSystemService(String name) {
                if (Context.SENSOR_SERVICE.equals(name)) {
                    return sensorManager;
                }
                return super.getSystemService(name);
            }
        };
    }

    private static SensorEvent sensorEvent(float x, float y, float z) {
        SensorEvent event = ReflectionHelpers.callConstructor(SensorEvent.class,
                ReflectionHelpers.ClassParameter.from(int.class, 3));
        event.values[0] = x;
        event.values[1] = y;
        event.values[2] = z;
        return event;
    }
}
