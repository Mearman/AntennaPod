package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class UsageStatisticsTest {
    private static final int LOGS_UNTIL_SIGNIFICANT_BIAS = 10;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        UsageStatistics.init(context);
    }

    private void logRepeatedly(UsageStatistics.StatsAction action, int times) {
        for (int i = 0; i < times; i++) {
            UsageStatistics.logAction(action);
        }
    }

    @Test
    public void noBiasWithoutAnyLoggedActions() {
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD));
    }

    @Test
    public void singleActionDoesNotCreateSignificantBias() {
        UsageStatistics.logAction(UsageStatistics.ACTION_STREAM);

        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
    }

    @Test
    public void repeatedStreamingCreatesBiasToStreamingOnly() {
        logRepeatedly(UsageStatistics.ACTION_STREAM, LOGS_UNTIL_SIGNIFICANT_BIAS);

        assertTrue(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD));
    }

    @Test
    public void repeatedDownloadingCreatesBiasToDownloadingOnly() {
        logRepeatedly(UsageStatistics.ACTION_DOWNLOAD, LOGS_UNTIL_SIGNIFICANT_BIAS);

        assertTrue(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD));
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
    }

    @Test
    public void recentOppositeActionsShiftTheBiasBack() {
        logRepeatedly(UsageStatistics.ACTION_STREAM, LOGS_UNTIL_SIGNIFICANT_BIAS);
        logRepeatedly(UsageStatistics.ACTION_DOWNLOAD, 2 * LOGS_UNTIL_SIGNIFICANT_BIAS);

        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
        assertTrue(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD));
    }

    @Test
    public void doNotAskAgainSuppressesBiasForBothActionsOfTheSameType() {
        logRepeatedly(UsageStatistics.ACTION_STREAM, LOGS_UNTIL_SIGNIFICANT_BIAS);

        UsageStatistics.doNotAskAgain(UsageStatistics.ACTION_STREAM);

        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD));
    }

    @Test
    public void actionsOfDifferentTypesAreTrackedIndependently() {
        UsageStatistics.StatsAction other = new UsageStatistics.StatsAction("otherType", 0);
        logRepeatedly(other, LOGS_UNTIL_SIGNIFICANT_BIAS);

        assertTrue(UsageStatistics.hasSignificantBiasTo(other));
        assertFalse(UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM));

        UsageStatistics.doNotAskAgain(other);
        assertFalse(UsageStatistics.hasSignificantBiasTo(other));
    }
}
