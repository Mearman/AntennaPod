package de.danoeh.antennapod.storage.preferences;

import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class UsageStatisticsStoredValuesTest extends StoredPreferencesTestBase {

    private int executions(UsageStatistics.StatsAction action) {
        return context.getSharedPreferences("UsageStatistics", 0).getInt(action.type + action.value, 0);
    }

    @Test
    public void noBiasIsSignificantBeforeAnythingWasLogged() {
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD));
    }

    @Test
    public void loggedActionsAreCountedPerActionValue() {
        UsageStatistics.logAction(UsageStatistics.ACTION_STREAM);
        UsageStatistics.logAction(UsageStatistics.ACTION_STREAM);
        UsageStatistics.logAction(UsageStatistics.ACTION_DOWNLOAD);

        assertEquals(2, executions(UsageStatistics.ACTION_STREAM));
        assertEquals(1, executions(UsageStatistics.ACTION_DOWNLOAD));
    }

    @Test
    public void repeatedStreamingBuildsASignificantBiasTowardsStreaming() {
        for (int i = 0; i < 15; i++) {
            UsageStatistics.logAction(UsageStatistics.ACTION_STREAM);
        }

        assertTrue(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD));
    }

    @Test
    public void repeatedDownloadingBuildsASignificantBiasTowardsDownloading() {
        for (int i = 0; i < 15; i++) {
            UsageStatistics.logAction(UsageStatistics.ACTION_DOWNLOAD);
        }

        assertTrue(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD));
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
    }

    @Test
    public void mixedUsageShowsNoSignificantBias() {
        for (int i = 0; i < 6; i++) {
            UsageStatistics.logAction(UsageStatistics.ACTION_STREAM);
            UsageStatistics.logAction(UsageStatistics.ACTION_DOWNLOAD);
        }

        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD));
    }

    @Test
    public void dismissingTheSuggestionHidesTheBiasForBothActions() {
        for (int i = 0; i < 15; i++) {
            UsageStatistics.logAction(UsageStatistics.ACTION_STREAM);
        }

        UsageStatistics.doNotAskAgain(UsageStatistics.ACTION_STREAM);

        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
    }
}
