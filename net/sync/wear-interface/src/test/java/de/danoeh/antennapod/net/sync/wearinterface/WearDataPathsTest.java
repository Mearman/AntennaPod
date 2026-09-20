package de.danoeh.antennapod.net.sync.wearinterface;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WearDataPathsTest {
    @Test
    public void playPathAppendsItemIdToPlayPrefix() {
        assertEquals(WearDataPaths.PLAY_PREFIX + 42, WearDataPaths.playPath(42));
        assertEquals("/play/42", WearDataPaths.playPath(42));
    }

    @Test
    public void openOnPhonePathAppendsItemIdToItsPrefix() {
        assertEquals("/open_on_phone/7", WearDataPaths.openOnPhonePath(7));
    }

    @Test
    public void feedEpisodesPathAppendsFeedIdToItsPrefix() {
        assertEquals("/feed_episodes/13", WearDataPaths.feedEpisodesPath(13));
    }

    @Test
    public void dynamicPathsAreRecognisedByTheirPrefixOnly() {
        assertTrue(WearDataPaths.playPath(1).startsWith(WearDataPaths.PLAY_PREFIX));
        assertTrue(WearDataPaths.openOnPhonePath(1).startsWith(WearDataPaths.OPEN_ON_PHONE_PREFIX));
        assertTrue(WearDataPaths.feedEpisodesPath(1).startsWith(WearDataPaths.FEED_EPISODES_PREFIX));
    }

    @Test
    public void differentItemsGetDifferentPaths() {
        assertNotEquals(WearDataPaths.playPath(1), WearDataPaths.playPath(2));
    }
}
