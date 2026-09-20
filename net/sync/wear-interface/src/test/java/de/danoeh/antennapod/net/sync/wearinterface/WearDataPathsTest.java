package de.danoeh.antennapod.net.sync.wearinterface;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WearDataPathsTest {
    @Test
    public void playPathAppendsItemIdToPlayPrefix() {
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
}
