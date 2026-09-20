package de.danoeh.antennapod.parser.feed.element.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import de.danoeh.antennapod.parser.feed.util.MimeTypeUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MimeTypeUtilsTest {

    @Test
    public void declaredMediaTypeWinsOverFileExtension() {
        assertEquals("audio/ogg", MimeTypeUtils.getMimeType("audio/ogg", "http://example.com/file.mp3"));
    }

    @Test
    public void octetStreamIsReplacedByTypeFromExtension() {
        assertEquals("audio/mpeg",
                MimeTypeUtils.getMimeType(MimeTypeUtils.OCTET_STREAM, "http://example.com/file.mp3"));
    }

    @Test
    public void octetStreamIsKeptWhenExtensionIsUnknown() {
        assertEquals(MimeTypeUtils.OCTET_STREAM,
                MimeTypeUtils.getMimeType(MimeTypeUtils.OCTET_STREAM, "http://example.com/file"));
    }

    @Test
    public void missingTypeIsDerivedFromExtension() {
        assertEquals("video/mp4", MimeTypeUtils.getMimeType(null, "http://example.com/file.mp4"));
    }

    @Test
    public void nonMediaTypeIsKeptWhenExtensionIsNotMedia() {
        assertEquals("text/plain", MimeTypeUtils.getMimeType("text/plain", "http://example.com/file.txt"));
    }

    @Test
    public void mediaExtensionOverridesNonMediaType() {
        assertEquals("audio/mpeg", MimeTypeUtils.getMimeType("text/plain", "http://example.com/file.mp3"));
    }

    @Test
    public void noTypeAndNoFilenameGivesNull() {
        assertNull(MimeTypeUtils.getMimeType(null, null));
    }

    @Test
    public void unknownExtensionAndNoTypeGivesNull() {
        assertNull(MimeTypeUtils.getMimeType(null, "http://example.com/file.zzzz"));
    }

    @Test
    public void audioAndVideoTypesAreMediaFiles() {
        assertTrue(MimeTypeUtils.isMediaFile("audio/mpeg"));
        assertTrue(MimeTypeUtils.isMediaFile("video/mp4"));
    }

    @Test
    public void oggApplicationAndOctetStreamAreMediaFiles() {
        assertTrue(MimeTypeUtils.isMediaFile("application/ogg"));
        assertTrue(MimeTypeUtils.isMediaFile("application/octet-stream"));
    }

    @Test
    public void otherTypesAreNotMediaFiles() {
        assertFalse(MimeTypeUtils.isMediaFile("image/png"));
        assertFalse(MimeTypeUtils.isMediaFile("text/html"));
        assertFalse(MimeTypeUtils.isMediaFile(null));
    }

    @Test
    public void onlyImageTypesAreImageFiles() {
        assertTrue(MimeTypeUtils.isImageFile("image/jpeg"));
        assertFalse(MimeTypeUtils.isImageFile("audio/mpeg"));
        assertFalse(MimeTypeUtils.isImageFile(null));
    }
}
