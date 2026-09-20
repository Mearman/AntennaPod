package de.danoeh.antennapod.net.sync.serviceinterface;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SynchronizationProviderTest {
    @Test
    public void everyProviderCanBeResolvedFromItsIdentifier() {
        for (SynchronizationProvider provider : SynchronizationProvider.values()) {
            assertEquals(provider, SynchronizationProvider.fromIdentifier(provider.getIdentifier()));
        }
    }

    @Test
    public void unknownIdentifierResolvesToNoProvider() {
        assertNull(SynchronizationProvider.fromIdentifier("SOMETHING_ELSE"));
    }

    @Test
    public void identifierLookupIsCaseSensitive() {
        assertNull(SynchronizationProvider.fromIdentifier("gpodder_net"));
    }

    @Test
    public void nullIdentifierResolvesToNoProvider() {
        assertNull(SynchronizationProvider.fromIdentifier(null));
    }
}
