package de.danoeh.antennapod.model.feed;

import de.danoeh.antennapod.model.playback.MediaType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedMediaPlaybackStateTest {
    private FeedItem item;
    private FeedMedia media;

    @Before
    public void setUp() {
        item = new FeedItem(1, "Title", "guid", "link", null, FeedItem.UNPLAYED, FeedMother.anyFeed());
        media = new FeedMedia(item, "http://example.com/episode", 1000, "audio/mpeg");
        item.setMedia(media);
    }

    @Test
    public void setPosition_positivePositionMarksNewItemAsUnplayed() {
        item.setNew();
        media.setPosition(500);
        assertEquals(500, media.getPosition());
        assertFalse(item.isNew());
        assertFalse(item.isPlayed());
    }

    @Test
    public void setPosition_zeroPositionKeepsNewItemNew() {
        item.setNew();
        media.setPosition(0);
        assertTrue(item.isNew());
    }

    @Test
    public void setPosition_positivePositionKeepsPlayedItemPlayed() {
        item.setPlayed(true);
        media.setPosition(500);
        assertTrue(item.isPlayed());
    }

    @Test
    public void isInProgress_requiresPositivePosition() {
        assertFalse(media.isInProgress());
        media.setPosition(1);
        assertTrue(media.isInProgress());
    }

    @Test
    public void onPlaybackStart_recordsStartPositionAndPlayedDuration() {
        assertEquals(-1, media.getStartPosition());
        media.setPosition(12000);
        media.setPlayedDuration(3000);
        media.onPlaybackStart();
        assertEquals(12000, media.getStartPosition());
        assertEquals(3000, media.getPlayedDurationWhenStarted());

        media.setPlayedDuration(8000);
        assertEquals(3000, media.getPlayedDurationWhenStarted());
        assertEquals(8000, media.getPlayedDuration());
    }

    @Test
    public void onPlaybackStart_negativePositionStartsAtZero() {
        media.setPosition(-5);
        media.onPlaybackStart();
        assertEquals(0, media.getStartPosition());
    }

    @Test
    public void checkedOnSizeButUnknown_isRememberedAndClearedByRealSize() {
        assertFalse(media.checkedOnSizeButUnknown());
        media.setCheckedOnSizeButUnknown();
        assertTrue(media.checkedOnSizeButUnknown());
        assertTrue(media.getSize() <= 0);
        media.setSize(500);
        assertFalse(media.checkedOnSizeButUnknown());
        assertEquals(500, media.getSize());
    }

    @Test
    public void lastPlayedTimeStatistics_isStored() {
        media.setLastPlayedTimeStatistics(4242);
        assertEquals(4242, media.getLastPlayedTimeStatistics());
    }

    @Test
    public void getMediaType_followsMimeType() {
        assertEquals(MediaType.AUDIO, media.getMediaType());
        FeedMedia video = new FeedMedia(item, "http://example.com/video", 0, "video/mp4");
        assertEquals(MediaType.VIDEO, video.getMediaType());
        FeedMedia unknown = new FeedMedia(item, "http://example.com/other", 0, null);
        assertEquals(MediaType.UNKNOWN, unknown.getMediaType());
    }

    @Test
    public void playableType_identifiesFeedMedia() {
        assertEquals(FeedMedia.PLAYABLE_TYPE_FEEDMEDIA, media.getPlayableType());
    }
}
