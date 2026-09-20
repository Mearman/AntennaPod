package de.danoeh.antennapod.net.sync.serviceinterface;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubscriptionChangesTest {
    @Test
    public void exposesAddedRemovedAndTimestamp() {
        SubscriptionChanges changes = new SubscriptionChanges(
                Collections.singletonList("added"), Collections.singletonList("removed"), 42);

        assertEquals(Collections.singletonList("added"), changes.getAdded());
        assertEquals(Collections.singletonList("removed"), changes.getRemoved());
        assertEquals(42, changes.getTimestamp());
    }

    @Test
    public void descriptionListsBothDirectionsAndTimestamp() {
        SubscriptionChanges changes = new SubscriptionChanges(
                Collections.singletonList("http://a.example/feed"),
                Collections.singletonList("http://b.example/feed"), 1234);

        String description = changes.toString();

        assertTrue(description.contains("http://a.example/feed"));
        assertTrue(description.contains("http://b.example/feed"));
        assertTrue(description.contains("1234"));
    }
}
