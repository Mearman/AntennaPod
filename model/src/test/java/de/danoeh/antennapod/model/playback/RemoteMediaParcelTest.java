package de.danoeh.antennapod.model.playback;

import android.os.Parcel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class RemoteMediaParcelTest {
    private Parcel parcel;

    @Before
    public void setUp() {
        parcel = Parcel.obtain();
    }

    @After
    public void tearDown() {
        parcel.recycle();
    }

    @Test
    public void roundTrip_preservesAllValuesAndPlaybackState() {
        RemoteMedia media = new RemoteMedia("http://example.com/episode.mp3", "guid", "http://example.com/feed.xml",
                "Feed title", "Episode title", "http://example.com/episode", "Author", "http://example.com/image.png",
                "http://example.com", "audio/mpeg", new Date(5000), "Shownotes");
        media.setDuration(60000);
        media.setPosition(1200);
        media.setLastPlayedTimeStatistics(77);

        media.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RemoteMedia restored = RemoteMedia.CREATOR.createFromParcel(parcel);

        assertEquals(media, restored);
        assertEquals("Feed title", restored.getFeedTitle());
        assertEquals("Episode title", restored.getEpisodeTitle());
        assertEquals("http://example.com/episode", restored.getEpisodeLink());
        assertEquals("Author", restored.getFeedAuthor());
        assertEquals("http://example.com/image.png", restored.getImageUrl());
        assertEquals("http://example.com", restored.getFeedLink());
        assertEquals("audio/mpeg", restored.getMimeType());
        assertEquals(5000, restored.getPubDate().getTime());
        assertEquals("Shownotes", restored.getNotes());
        assertEquals(60000, restored.getDuration());
        assertEquals(1200, restored.getPosition());
        assertEquals(77, restored.getLastPlayedTimeStatistics());
    }

    @Test
    public void roundTrip_missingPubDateRestoresAsEpoch() {
        RemoteMedia media = new RemoteMedia("http://example.com/episode.mp3", "guid", "http://example.com/feed.xml",
                null, null, null, null, null, null, null, null, null);

        media.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RemoteMedia restored = RemoteMedia.CREATOR.createFromParcel(parcel);

        assertEquals(0, restored.getPubDate().getTime());
    }

    @Test
    public void newArray_createsArrayOfRequestedSize() {
        assertEquals(3, RemoteMedia.CREATOR.newArray(3).length);
    }

    @Test
    public void describeContents_isZero() {
        RemoteMedia media = new RemoteMedia("u", "g", "f", null, null, null, null, null, null, null, null, null);
        assertEquals(0, media.describeContents());
    }
}
