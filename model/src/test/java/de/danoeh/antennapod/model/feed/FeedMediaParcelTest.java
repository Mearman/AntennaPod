package de.danoeh.antennapod.model.feed;

import android.os.Parcel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class FeedMediaParcelTest {
    private Parcel parcel;

    @Before
    public void setUp() {
        parcel = Parcel.obtain();
    }

    @After
    public void tearDown() {
        parcel.recycle();
    }

    private FeedMedia roundTrip(FeedMedia media) {
        media.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return FeedMedia.CREATOR.createFromParcel(parcel);
    }

    @Test
    public void roundTrip_preservesPersistedFieldsAndItemId() {
        FeedItem item = new FeedItem();
        item.setId(21);
        FeedMedia media = new FeedMedia(7, item, 60000, 15000, 4321, "audio/ogg", "/local/file.ogg",
                "http://example.com/file.ogg", 5000, new Date(99000), 20000, 77000);

        FeedMedia restored = roundTrip(media);

        assertEquals(7, restored.getId());
        assertEquals(21, restored.getItemId());
        assertNull(restored.getItem());
        assertEquals(60000, restored.getDuration());
        assertEquals(15000, restored.getPosition());
        assertEquals(4321, restored.getSize());
        assertEquals("audio/ogg", restored.getMimeType());
        assertEquals("/local/file.ogg", restored.getLocalFileUrl());
        assertEquals("http://example.com/file.ogg", restored.getDownloadUrl());
        assertEquals(5000, restored.getDownloadDate());
        assertEquals(99000, restored.getLastPlayedTimeHistory().getTime());
        assertEquals(20000, restored.getPlayedDuration());
        assertEquals(77000, restored.getLastPlayedTimeStatistics());
    }

    @Test
    public void roundTrip_mediaWithoutItemOrHistoryRestoresZeroValues() {
        FeedMedia restored = roundTrip(new FeedMedia(3, null, 0, 0, 0, null, null, null, 0, null, 0, 0));
        assertEquals(0, restored.getItemId());
        assertEquals(0, restored.getLastPlayedTimeHistory().getTime());
        assertNull(restored.getMimeType());
        assertNull(restored.getLocalFileUrl());
    }

    @Test
    public void newArray_createsArrayOfRequestedSize() {
        assertEquals(4, FeedMedia.CREATOR.newArray(4).length);
    }

    @Test
    public void describeContents_isZero() {
        assertEquals(0, FeedMediaMother.anyFeedMedia().describeContents());
    }
}
