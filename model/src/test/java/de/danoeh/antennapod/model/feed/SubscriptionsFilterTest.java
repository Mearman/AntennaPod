package de.danoeh.antennapod.model.feed;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SubscriptionsFilterTest {

    @Test
    public void emptyFilter_isNotEnabledAndHidesNonSubscribedFeeds() {
        SubscriptionsFilter filter = new SubscriptionsFilter(new String[0]);
        assertFalse(filter.isEnabled());
        assertTrue(filter.hideNonSubscribedFeeds);
        assertFalse(filter.showIfCounterGreaterZero);
        assertFalse(filter.showAutoDownloadEnabled);
        assertFalse(filter.showAutoDownloadDisabled);
        assertFalse(filter.showUpdatedEnabled);
        assertFalse(filter.showUpdatedDisabled);
        assertFalse(filter.showEpisodeNotificationEnabled);
        assertFalse(filter.showEpisodeNotificationDisabled);
    }

    @Test
    public void emptyString_isNotEnabled() {
        assertFalse(new SubscriptionsFilter("").isEnabled());
    }

    @Test
    public void stringConstructor_splitsCommaSeparatedProperties() {
        SubscriptionsFilter filter = new SubscriptionsFilter(
                SubscriptionsFilter.COUNTER_GREATER_ZERO + "," + SubscriptionsFilter.ENABLED_AUTO_DOWNLOAD);
        assertTrue(filter.isEnabled());
        assertTrue(filter.showIfCounterGreaterZero);
        assertTrue(filter.showAutoDownloadEnabled);
        assertFalse(filter.showAutoDownloadDisabled);
    }

    @Test
    public void eachProperty_setsOnlyItsOwnFlag() {
        assertTrue(new SubscriptionsFilter(new String[] {SubscriptionsFilter.DISABLED_AUTO_DOWNLOAD})
                .showAutoDownloadDisabled);
        assertTrue(new SubscriptionsFilter(new String[] {SubscriptionsFilter.ENABLED_UPDATES}).showUpdatedEnabled);
        assertTrue(new SubscriptionsFilter(new String[] {SubscriptionsFilter.DISABLED_UPDATES}).showUpdatedDisabled);
        assertTrue(new SubscriptionsFilter(new String[] {SubscriptionsFilter.EPISODE_NOTIFICATION_ENABLED})
                .showEpisodeNotificationEnabled);
        assertTrue(new SubscriptionsFilter(new String[] {SubscriptionsFilter.EPISODE_NOTIFICATION_DISABLED})
                .showEpisodeNotificationDisabled);
        SubscriptionsFilter updatesOnly = new SubscriptionsFilter(new String[] {SubscriptionsFilter.ENABLED_UPDATES});
        assertFalse(updatesOnly.showUpdatedDisabled);
        assertFalse(updatesOnly.showEpisodeNotificationEnabled);
    }

    @Test
    public void showNonSubscribedProperty_disablesHidingOfNonSubscribedFeeds() {
        SubscriptionsFilter filter = new SubscriptionsFilter(
                new String[] {SubscriptionsFilter.SHOW_NON_SUBSCRIBED_FEEDS});
        assertFalse(filter.hideNonSubscribedFeeds);
    }

    @Test
    public void serialize_joinsPropertiesWithComma() {
        String[] properties = {SubscriptionsFilter.ENABLED_UPDATES, SubscriptionsFilter.COUNTER_GREATER_ZERO};
        SubscriptionsFilter filter = new SubscriptionsFilter(properties);
        assertEquals("enabled_updates,counter_greater_zero", filter.serialize());
    }

    @Test
    public void serialize_roundTripsThroughStringConstructor() {
        SubscriptionsFilter original = new SubscriptionsFilter(
                new String[] {SubscriptionsFilter.DISABLED_UPDATES, SubscriptionsFilter.SHOW_NON_SUBSCRIBED_FEEDS});
        SubscriptionsFilter restored = new SubscriptionsFilter(original.serialize());
        assertArrayEquals(original.getValues(), restored.getValues());
        assertTrue(restored.showUpdatedDisabled);
        assertFalse(restored.hideNonSubscribedFeeds);
    }

    @Test
    public void getValues_returnsDefensiveCopy() {
        SubscriptionsFilter filter = new SubscriptionsFilter(new String[] {SubscriptionsFilter.ENABLED_UPDATES});
        String[] values = filter.getValues();
        values[0] = "changed";
        assertNotSame(values, filter.getValues());
        assertArrayEquals(new String[] {SubscriptionsFilter.ENABLED_UPDATES}, filter.getValues());
    }
}
