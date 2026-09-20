package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;

public abstract class StoredPreferencesTestBase {
    protected Context context;
    protected SharedPreferences stored;

    @Before
    public void initialisePreferences() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        SleepTimerPreferences.init(context);
        SynchronizationCredentials.init(context);
        SynchronizationSettings.init(context);
        UsageStatistics.init(context);
        stored = PreferenceManager.getDefaultSharedPreferences(context);
    }
}
