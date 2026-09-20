package de.danoeh.antennapod.model.download;

import android.os.Bundle;
import android.os.Parcel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DownloadRequestTest {
    private Bundle arguments;
    private DownloadRequest request;

    @Before
    public void setUp() {
        arguments = new Bundle();
        arguments.putInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 2);
        request = new DownloadRequest("/storage/episode.mp3", "http://example.com/episode.mp3", "Episode", 15, 2,
                "etag", "user", "secret", true, arguments, true);
    }

    private static DownloadRequest requestWith(String destination, String source, String username, String password) {
        return new DownloadRequest(destination, source, "Episode", 15, 2, "etag", username, password, true,
                new Bundle(), true);
    }

    @Test
    public void constructor_keepsValues() {
        assertEquals("/storage/episode.mp3", request.getDestination());
        assertEquals("http://example.com/episode.mp3", request.getSource());
        assertEquals("Episode", request.getTitle());
        assertEquals(15, request.getFeedfileId());
        assertEquals(2, request.getFeedfileType());
        assertEquals("etag", request.getLastModified());
        assertEquals("user", request.getUsername());
        assertEquals("secret", request.getPassword());
        assertSame(arguments, request.getArguments());
        assertEquals(0, request.getProgressPercent());
        assertEquals(0, request.getSoFar());
        assertEquals(0, request.getSize());
    }

    @Test
    public void shortConstructor_hasNoLastModified() {
        DownloadRequest shortRequest = new DownloadRequest("/dest", "http://example.com/src", "Title", 1, 0,
                "user", "secret", new Bundle(), false);
        assertNull(shortRequest.getLastModified());
        assertEquals("user", shortRequest.getUsername());
        assertEquals("secret", shortRequest.getPassword());
    }

    @Test
    public void setters_updateProgressAndCredentials() {
        request.setProgressPercent(50);
        request.setSoFar(512);
        request.setSize(1024);
        request.setUsername("changed");
        request.setPassword("changed-secret");
        assertEquals(50, request.getProgressPercent());
        assertEquals(512, request.getSoFar());
        assertEquals(1024, request.getSize());
        assertEquals("changed", request.getUsername());
        assertEquals("changed-secret", request.getPassword());
    }

    @Test
    public void setLastModified_returnsSameRequestForChaining() {
        assertSame(request, request.setLastModified("new-etag"));
        assertEquals("new-etag", request.getLastModified());
        request.setLastModified(null);
        assertNull(request.getLastModified());
    }

    @Test
    public void equals_sameValuesAreEqualWithSameHashCode() {
        DownloadRequest copy = new DownloadRequest("/storage/episode.mp3", "http://example.com/episode.mp3",
                "Episode", 15, 2, "etag", "user", "secret", true, arguments, true);
        assertEquals(request, request);
        assertEquals(request, copy);
        assertEquals(request.hashCode(), copy.hashCode());
    }

    @Test
    public void equals_differsWhenAnyComparedFieldDiffers() {
        assertNotEquals(request, requestWith("/other", "http://example.com/episode.mp3", "user", "secret"));
        assertNotEquals(request, requestWith("/storage/episode.mp3", "http://example.com/other.mp3", "user",
                "secret"));
        assertNotEquals(request, requestWith("/storage/episode.mp3", "http://example.com/episode.mp3", "user", null));
        assertNotEquals(request, requestWith("/storage/episode.mp3", "http://example.com/episode.mp3", null,
                "secret"));
        assertNotEquals(request, null);
        assertNotEquals(request, "request");
    }

    @Test
    public void equals_progressAndStatusDifferencesMatter() {
        DownloadRequest first = requestWith("/dest", "http://example.com/src", null, null);
        DownloadRequest second = requestWith("/dest", "http://example.com/src", null, null);
        assertEquals(first, second);

        second.setProgressPercent(10);
        assertNotEquals(first, second);
        second.setProgressPercent(0);
        second.setSoFar(1);
        assertNotEquals(first, second);
        second.setSoFar(0);
        second.setSize(1);
        assertNotEquals(first, second);
        second.setSize(0);
        second.setStatusMsg(3);
        assertNotEquals(first, second);
        second.setStatusMsg(0);
        assertEquals(first, second);
    }

    @Test
    public void equals_lastModifiedAndFlagsMatter() {
        DownloadRequest withEtag = new DownloadRequest("/dest", "http://example.com/src", "Episode", 15, 2, "etag",
                null, null, false, new Bundle(), false);
        DownloadRequest withoutEtag = new DownloadRequest("/dest", "http://example.com/src", "Episode", 15, 2, null,
                null, null, false, new Bundle(), false);
        DownloadRequest enqueued = new DownloadRequest("/dest", "http://example.com/src", "Episode", 15, 2, null,
                null, null, true, new Bundle(), false);
        DownloadRequest userInitiated = new DownloadRequest("/dest", "http://example.com/src", "Episode", 15, 2, null,
                null, null, false, new Bundle(), true);
        assertNotEquals(withEtag, withoutEtag);
        assertNotEquals(withoutEtag, enqueued);
        assertNotEquals(withoutEtag, userInitiated);
        assertNotEquals(withoutEtag, new DownloadRequest("/dest", "http://example.com/src", "Other", 15, 2, null,
                null, null, false, new Bundle(), false));
        assertNotEquals(withoutEtag, new DownloadRequest("/dest", "http://example.com/src", "Episode", 16, 2, null,
                null, null, false, new Bundle(), false));
        assertNotEquals(withoutEtag, new DownloadRequest("/dest", "http://example.com/src", "Episode", 15, 3, null,
                null, null, false, new Bundle(), false));
    }

    @Test
    public void hashCode_reflectsProgress() {
        DownloadRequest first = requestWith("/dest", "http://example.com/src", null, null);
        DownloadRequest second = requestWith("/dest", "http://example.com/src", null, null);
        second.setProgressPercent(10);
        assertNotEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void describeContents_isZero() {
        assertEquals(0, request.describeContents());
    }

    @Test
    public void newArray_createsArrayOfRequestedSize() {
        assertEquals(2, DownloadRequest.CREATOR.newArray(2).length);
    }

    @Test
    public void parcelRoundTrip_preservesRequest() {
        request.setLastModified("etag");
        Parcel parcel = Parcel.obtain();
        try {
            request.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            DownloadRequest restored = DownloadRequest.CREATOR.createFromParcel(parcel);

            assertEquals("/storage/episode.mp3", restored.getDestination());
            assertEquals("http://example.com/episode.mp3", restored.getSource());
            assertEquals("Episode", restored.getTitle());
            assertEquals(15, restored.getFeedfileId());
            assertEquals(2, restored.getFeedfileType());
            assertEquals("etag", restored.getLastModified());
            assertEquals("user", restored.getUsername());
            assertEquals("secret", restored.getPassword());
            assertEquals(2, restored.getArguments().getInt(DownloadRequest.REQUEST_ARG_PAGE_NR));
            assertEquals(request, restored);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void parcelRoundTrip_missingCredentialsRestoreAsNull() {
        DownloadRequest withoutCredentials = requestWith("/dest", "http://example.com/src", null, null);
        Parcel parcel = Parcel.obtain();
        try {
            withoutCredentials.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            DownloadRequest restored = DownloadRequest.CREATOR.createFromParcel(parcel);

            assertNull(restored.getUsername());
            assertNull(restored.getPassword());
            assertEquals("etag", restored.getLastModified());
            assertFalse(restored.getArguments().containsKey(DownloadRequest.REQUEST_ARG_PAGE_NR));
            assertTrue(withoutCredentials.equals(restored));
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void equals_nullAndNonNullTitleOrUsernameAreDifferent() {
        DownloadRequest untitled = new DownloadRequest("/dest", "http://example.com/src", null, 15, 2, "etag", null,
                null, true, arguments, true);
        DownloadRequest untitledCopy = new DownloadRequest("/dest", "http://example.com/src", null, 15, 2, "etag",
                null, null, true, arguments, true);
        DownloadRequest titled = new DownloadRequest("/dest", "http://example.com/src", "Episode", 15, 2, "etag",
                null, null, true, arguments, true);
        DownloadRequest otherUser = new DownloadRequest("/dest", "http://example.com/src", null, 15, 2, "etag",
                "other", null, true, arguments, true);
        DownloadRequest user = new DownloadRequest("/dest", "http://example.com/src", null, 15, 2, "etag", "user",
                null, true, arguments, true);

        assertEquals(untitled, untitledCopy);
        assertEquals(untitled.hashCode(), untitledCopy.hashCode());
        assertNotEquals(untitled, titled);
        assertNotEquals(titled, untitled);
        assertNotEquals(untitled, user);
        assertNotEquals(user, untitled);
        assertNotEquals(user, otherUser);
        assertNotEquals(untitled.hashCode(), titled.hashCode());
        assertNotEquals(untitled.hashCode(), user.hashCode());
    }
}
